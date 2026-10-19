package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.OverdueInvoice
import br.com.saqz.subscriptions.application.PastDueProvider
import br.com.saqz.subscriptions.application.PastDueSubscription
import br.com.saqz.subscriptions.application.SubscriptionPastDueRepository
import br.com.saqz.subscriptions.domain.Subscription
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

/**
 * Atrasos de pagamento que ainda dão acesso, nas três origens. O dono que tem outro plano em dia (outra origem ativa e sem atraso)
 * não é avisado. Não depende do `SubscriptionEventStore`, que só existe com o token do webhook do Asaas: o payload é lido direto.
 */
class JdbcSubscriptionPastDueRepository(
    dataSource: DataSource,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : SubscriptionPastDueRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun pastDue(now: Instant): List<PastDueSubscription> =
        asaas(now) + appStore(now) + googlePlay(now)

    private fun asaas(now: Instant): List<PastDueSubscription> = jdbc.sql(
        """
        SELECT s.owner_user_id, s.past_due_since, s.asaas_subscription_id FROM subscriptions s
        WHERE s.status = 'PAST_DUE' AND s.first_confirmed_at IS NOT NULL AND s.canceled_at IS NULL
          AND s.past_due_since IS NOT NULL AND s.past_due_since > :floor
          AND $NO_HEALTHY_STORE
        ORDER BY s.past_due_since, s.owner_user_id
        """.trimIndent(),
    ).param("floor", Timestamp.from(now.minusSeconds(FLOOR_SECONDS))).query { rs, _ ->
        val since = rs.getTimestamp("past_due_since").toInstant()
        val dueDay = Subscription.pastDueDay(since)
        PastDueSubscription(
            rs.getObject("owner_user_id", UUID::class.java), PastDueProvider.ASAAS, since,
            dueDay, dueDay.plusDays(Subscription.PAST_DUE_GRACE_DAYS), rs.getString("asaas_subscription_id"),
        )
    }.list()

    /** Vencida, a Apple ainda tentando cobrar e o acesso mantido pela carência dela: o último dia é o fim dessa carência. */
    private fun appStore(now: Instant): List<PastDueSubscription> = jdbc.sql(
        """
        SELECT a.owner_user_id, a.expires_at, a.grace_period_expires_at FROM app_store_subscriptions a
        WHERE a.revoked_at IS NULL AND a.in_billing_retry AND a.auto_renew IS DISTINCT FROM false
          AND a.expires_at <= :now AND a.grace_period_expires_at > :now
          AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.owner_user_id = a.owner_user_id AND ($ASAAS_HEALTHY))
          AND NOT EXISTS (SELECT 1 FROM app_store_subscriptions o WHERE o.owner_user_id = a.owner_user_id
                          AND o.original_transaction_id <> a.original_transaction_id AND o.revoked_at IS NULL AND o.expires_at > :now)
          AND NOT EXISTS (SELECT 1 FROM google_play_subscriptions g WHERE g.owner_user_id = a.owner_user_id AND ($GOOGLE_HEALTHY))
        ORDER BY a.expires_at, a.owner_user_id
        """.trimIndent(),
    ).param("now", Timestamp.from(now)).query { rs, _ ->
        val since = rs.getTimestamp("expires_at").toInstant()
        val graceEnds = rs.getTimestamp("grace_period_expires_at").toInstant()
        PastDueSubscription(
            rs.getObject("owner_user_id", UUID::class.java), PastDueProvider.APP_STORE, since,
            Subscription.pastDueDay(since), Subscription.pastDueDay(graceEnds), accessEndsAt = graceEnds,
        )
    }.list()

    /**
     * Carência do Play: o `expires_at` já gravado é o fim do acesso (é o que o `isEntitlingAt` usa), mas o dia em que a cobrança
     * falhou não é guardado. Sem o vencimento, só o último dia é avisado (`dueDay` nulo).
     */
    private fun googlePlay(now: Instant): List<PastDueSubscription> = jdbc.sql(
        """
        SELECT g.owner_user_id, g.expires_at FROM google_play_subscriptions g
        WHERE g.superseded_at IS NULL AND g.state = 'IN_GRACE_PERIOD' AND g.expires_at > :now
          AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.owner_user_id = g.owner_user_id AND ($ASAAS_HEALTHY))
          AND NOT EXISTS (SELECT 1 FROM app_store_subscriptions a WHERE a.owner_user_id = g.owner_user_id
                          AND a.revoked_at IS NULL AND a.expires_at > :now)
          AND NOT EXISTS (SELECT 1 FROM google_play_subscriptions o WHERE o.owner_user_id = g.owner_user_id
                          AND o.purchase_token <> g.purchase_token AND ($GOOGLE_HEALTHY_OTHER))
        ORDER BY g.expires_at, g.owner_user_id
        """.trimIndent(),
    ).param("now", Timestamp.from(now)).query { rs, _ ->
        val expires = rs.getTimestamp("expires_at").toInstant()
        PastDueSubscription(
            rs.getObject("owner_user_id", UUID::class.java), PastDueProvider.GOOGLE_PLAY, expires,
            null, Subscription.pastDueDay(expires), accessEndsAt = expires,
        )
    }.list()

    override fun overdueInvoice(subscription: PastDueSubscription): OverdueInvoice? {
        val subscriptionId = subscription.asaasSubscriptionId ?: return null
        val earliest = subscription.dueDay ?: return null
        val payloads = jdbc.sql(
            """
            SELECT payload FROM subscription_events
            WHERE type = 'PAYMENT_OVERDUE' AND owner_user_id = :owner
            ORDER BY coalesce(processed_at, created_at) DESC LIMIT :limit
            """.trimIndent(),
        ).param("owner", subscription.ownerUserId).param("limit", EVENTS_READ).query(String::class.java).list()
        return payloads.asSequence().filterNotNull().mapNotNull { payment(it, subscriptionId, earliest) }.maxByOrNull { it.first }?.second
    }

    /** O pagamento do payload, se for da mensalidade desta assinatura e não for de um vencimento anterior ao atraso. */
    private fun payment(payload: String, subscriptionId: String, earliest: LocalDate): Pair<LocalDate, OverdueInvoice>? {
        val payment = runCatching { objectMapper.readTree(payload).path("payment") }.getOrNull() ?: return null
        if (payment.path("subscription").asText(null) != subscriptionId) return null
        val due = payment.path("dueDate").asText(null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        if (due.isBefore(earliest)) return null
        val id = payment.path("id").asText(null)?.takeIf { it.isNotBlank() } ?: return null
        val url = payment.path("invoiceUrl").asText(null)?.takeIf { it.isNotBlank() }
            ?: payment.path("bankSlipUrl").asText(null)?.takeIf { it.isNotBlank() }
        return due to OverdueInvoice(id, url)
    }

    override fun claim(subscription: PastDueSubscription, due: List<Int>): Boolean {
        val tightest = due.max()
        var claimedTightest = false
        due.forEach { milestone ->
            val inserted = jdbc.sql(
                """
                INSERT INTO subscription_past_due_notices (owner_user_id, provider, past_due_since, milestone)
                VALUES (:owner, :provider, :since, :milestone) ON CONFLICT DO NOTHING
                """.trimIndent(),
            ).param("owner", subscription.ownerUserId).param("provider", subscription.provider.name)
                .param("since", Timestamp.from(subscription.since)).param("milestone", milestone.toShort()).update()
            if (milestone == tightest && inserted == 1) claimedTightest = true
        }
        return claimedTightest
    }

    override fun release(subscription: PastDueSubscription, milestone: Int) {
        jdbc.sql(
            """
            DELETE FROM subscription_past_due_notices
            WHERE owner_user_id = :owner AND provider = :provider AND past_due_since = :since AND milestone = :milestone
            """.trimIndent(),
        ).param("owner", subscription.ownerUserId).param("provider", subscription.provider.name)
            .param("since", Timestamp.from(subscription.since)).param("milestone", milestone.toShort()).update()
    }

    private companion object {
        /** Folga larga: o corte fino (último dia da carência) é feito em Kotlin, em dias de São Paulo. */
        const val FLOOR_SECONDS = 10L * 24 * 3600
        const val EVENTS_READ = 20

        /** Plano do site em dia (ACTIVE) ou cancelado ainda dentro do período pago. */
        const val ASAAS_HEALTHY =
            "s.status = 'ACTIVE' OR (s.status = 'CANCELED' AND s.first_confirmed_at IS NOT NULL AND s.current_period_end > now())"

        const val GOOGLE_HEALTHY =
            "g.superseded_at IS NULL AND g.state IN ('ACTIVE', 'CANCELED') AND g.expires_at > now()"
        const val GOOGLE_HEALTHY_OTHER =
            "o.superseded_at IS NULL AND o.state IN ('ACTIVE', 'CANCELED') AND o.expires_at > now()"

        /** Dono do atraso do site sem plano de loja em dia (Apple vigente ou Play ativo). */
        const val NO_HEALTHY_STORE =
            """NOT EXISTS (SELECT 1 FROM app_store_subscriptions a WHERE a.owner_user_id = s.owner_user_id
                  AND a.revoked_at IS NULL AND a.expires_at > now())
          AND NOT EXISTS (SELECT 1 FROM google_play_subscriptions g WHERE g.owner_user_id = s.owner_user_id AND (g.superseded_at IS NULL
                  AND g.state IN ('ACTIVE', 'CANCELED') AND g.expires_at > now()))"""
    }
}
