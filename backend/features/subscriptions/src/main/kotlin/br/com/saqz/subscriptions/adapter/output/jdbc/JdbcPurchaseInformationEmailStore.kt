package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.PurchaseInformationReservation
import br.com.saqz.subscriptions.application.PurchaseInformationReservationPort
import br.com.saqz.subscriptions.application.PurchaseInformationReservationResult
import br.com.saqz.subscriptions.application.SendPurchaseInformation
import br.com.saqz.subscriptions.application.SubscriptionEmailLookup
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * Durable reservation and authoritative recipient lookup for purchase-information e-mail.
 *
 * This adapter intentionally reads the shared [access_users] table through SQL instead of
 * depending on the access Gradle module. Reservation decisions and history writes are kept
 * in short READ COMMITTED transactions; SMTP is always called by the application outside
 * those transactions.
 */
class JdbcPurchaseInformationEmailStore(
    dataSource: DataSource,
    private val staleReservationAfter: Duration = DEFAULT_STALE_RESERVATION_AFTER,
) : SubscriptionEmailLookup, PurchaseInformationReservationPort {
    init {
        require(!staleReservationAfter.isNegative && !staleReservationAfter.isZero) {
            "staleReservationAfter must be positive"
        }
    }

    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource)).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    override fun findEmail(ownerUserId: UUID): String? = jdbc.sql(
        """
        SELECT email
        FROM access_users
        WHERE id = :ownerUserId
          AND deleted_at IS NULL
        """.trimIndent(),
    )
        .param("ownerUserId", ownerUserId)
        .query(String::class.java)
        .optional()
        .orElse(null)

    override fun reserve(
        ownerUserId: UUID,
        now: Instant,
    ): PurchaseInformationReservationResult = transaction.execute {
        jdbc.sql(
            """
            INSERT INTO subscription_purchase_information_emails (
                user_id, reservation_id, reservation_created_at, created_at, updated_at
            ) VALUES (:ownerUserId, NULL, NULL, :now, :now)
            ON CONFLICT (user_id) DO NOTHING
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .param("now", timestamp(now))
            .update()

        val current = jdbc.sql(
            """
            SELECT reservation_id, reservation_created_at
            FROM subscription_purchase_information_emails
            WHERE user_id = :ownerUserId
            FOR UPDATE
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .query { rs, _ ->
                ReservationRow(
                    reservationId = rs.getObject("reservation_id", UUID::class.java),
                    createdAt = rs.getTimestamp("reservation_created_at")?.toInstant(),
                )
            }
            .single()

        val hourFloor = now.minus(SUCCESS_WINDOW)
        val dedupeFloor = now.minus(DEDUPE_WINDOW)
        val staleFloor = now.minus(staleReservationAfter)

        // History only needs to cover the active rolling window. Pruning while holding
        // the user's reservation row lock cannot race a completion for this user.
        jdbc.sql(
            """
            DELETE FROM subscription_purchase_information_email_successes
            WHERE user_id = :ownerUserId
              AND succeeded_at <= :hourFloor
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .param("hourFloor", timestamp(hourFloor))
            .update()

        val history = jdbc.sql(
            """
            SELECT count(*)::int AS successful_count,
                   min(succeeded_at) AS earliest_success_at,
                   max(succeeded_at) AS latest_success_at
            FROM subscription_purchase_information_email_successes
            WHERE user_id = :ownerUserId
              AND succeeded_at > :hourFloor
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .param("hourFloor", timestamp(hourFloor))
            .query { rs, _ ->
                SuccessHistory(
                    count = rs.getInt("successful_count"),
                    earliest = rs.getTimestamp("earliest_success_at")?.toInstant(),
                    latest = rs.getTimestamp("latest_success_at")?.toInstant(),
                )
            }
            .single()

        history.latest?.takeIf { it > dedupeFloor }?.let {
            return@execute PurchaseInformationReservationResult.AlreadySent
        }

        current.createdAt?.takeIf { it > staleFloor }?.let {
            return@execute PurchaseInformationReservationResult.InProgress(
                retryAfterSeconds = retryAfterSeconds(it.plus(staleReservationAfter), now),
            )
        }

        if (history.count >= MAX_SUCCESSFUL_SENDS) {
            val earliest = checkNotNull(history.earliest)
            return@execute PurchaseInformationReservationResult.RateLimited(
                retryAfterSeconds = retryAfterSeconds(earliest.plus(SUCCESS_WINDOW), now),
            )
        }

        val reservationId = UUID.randomUUID()
        jdbc.sql(
            """
            UPDATE subscription_purchase_information_emails
            SET reservation_id = :reservationId,
                reservation_created_at = :now,
                updated_at = :now
            WHERE user_id = :ownerUserId
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .param("reservationId", reservationId)
            .param("now", timestamp(now))
            .update()

        PurchaseInformationReservationResult.Reserved(
            PurchaseInformationReservation(ownerUserId, reservationId.toString()),
        )
    }

    override fun complete(
        reservation: PurchaseInformationReservation,
        completedAt: Instant,
    ): Boolean {
        val reservationId = reservationId(reservation) ?: return false
        return transaction.execute {
            val updated = jdbc.sql(
                """
                UPDATE subscription_purchase_information_emails
                SET reservation_id = NULL,
                    reservation_created_at = NULL,
                    updated_at = :completedAt
                WHERE user_id = :ownerUserId
                  AND reservation_id = :reservationId
                """.trimIndent(),
            )
                .param("ownerUserId", reservation.ownerUserId)
                .param("reservationId", reservationId)
                .param("completedAt", timestamp(completedAt))
                .update()
            if (updated != 1) return@execute false

            jdbc.sql(
                """
                INSERT INTO subscription_purchase_information_email_successes (user_id, succeeded_at)
                VALUES (:ownerUserId, :completedAt)
                """.trimIndent(),
            )
                .param("ownerUserId", reservation.ownerUserId)
                .param("completedAt", timestamp(completedAt))
                .update()
            true
        }
    }

    override fun release(reservation: PurchaseInformationReservation): Boolean {
        val reservationId = reservationId(reservation) ?: return false
        return transaction.execute {
            jdbc.sql(
                """
                UPDATE subscription_purchase_information_emails
                SET reservation_id = NULL,
                    reservation_created_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = :ownerUserId
                  AND reservation_id = :reservationId
                """.trimIndent(),
            )
                .param("ownerUserId", reservation.ownerUserId)
                .param("reservationId", reservationId)
                .update() == 1
        }
    }

    private fun reservationId(reservation: PurchaseInformationReservation): UUID? =
        runCatching { UUID.fromString(reservation.token) }.getOrNull()

    private fun timestamp(instant: Instant): Timestamp = Timestamp.from(instant)

    private fun retryAfterSeconds(until: Instant, now: Instant): Int {
        val remaining = Duration.between(now, until)
        check(!remaining.isNegative && !remaining.isZero) {
            "retry deadline must be in the future"
        }
        val seconds = remaining.seconds + if (remaining.nano == 0) 0 else 1
        return seconds.coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private data class ReservationRow(
        val reservationId: UUID?,
        val createdAt: Instant?,
    )

    private data class SuccessHistory(
        val count: Int,
        val earliest: Instant?,
        val latest: Instant?,
    )

    companion object {
        val DEFAULT_STALE_RESERVATION_AFTER: Duration = Duration.ofMinutes(1)

        // A política é do caso de uso — o KDoc da porta manda aplicar estes valores aqui.
        // Copiá-los deixaria dois donos, e só a cópia deste adapter valeria em produção.
        val DEDUPE_WINDOW: Duration = SendPurchaseInformation.DEDUPE_WINDOW
        val SUCCESS_WINDOW: Duration = SendPurchaseInformation.RATE_LIMIT_WINDOW
        const val MAX_SUCCESSFUL_SENDS = SendPurchaseInformation.MAX_SUCCESSFUL_SENDS
    }
}
