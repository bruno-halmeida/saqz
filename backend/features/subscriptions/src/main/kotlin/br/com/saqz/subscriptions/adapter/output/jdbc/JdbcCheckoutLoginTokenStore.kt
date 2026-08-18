package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.CheckoutLoginCode
import br.com.saqz.subscriptions.application.CheckoutLoginDigest
import br.com.saqz.subscriptions.application.CheckoutLoginSecrets
import br.com.saqz.subscriptions.application.CheckoutLoginTokens
import br.com.saqz.subscriptions.application.OpenCheckoutLogin
import br.com.saqz.subscriptions.application.RedeemCheckoutLogin
import br.com.saqz.subscriptions.application.SecureCheckoutLoginSecrets
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcCheckoutLoginTokenStore(
    dataSource: DataSource,
    private val secrets: CheckoutLoginSecrets = SecureCheckoutLoginSecrets(),
) : CheckoutLoginTokens {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource)).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    override fun issue(ownerUserId: UUID, now: Instant): String = checkNotNull(
        transaction.execute {
            jdbc.sql(
                """
                UPDATE subscription_checkout_login_tokens
                SET consumed_at = :now
                WHERE user_id = :ownerUserId
                  AND consumed_at IS NULL
                """.trimIndent(),
            )
                .param("ownerUserId", ownerUserId)
                .param("now", timestamp(now))
                .update()

            val secret = secrets.next()
            jdbc.sql(
                """
                INSERT INTO subscription_checkout_login_tokens (
                    id, user_id, token_digest, created_at, expires_at
                ) VALUES (
                    :id, :ownerUserId, :tokenDigest, :now, :expiresAt
                )
                """.trimIndent(),
            )
                .param("id", UUID.randomUUID())
                .param("ownerUserId", ownerUserId)
                .param("tokenDigest", secret.digest.toByteArray())
                .param("now", timestamp(now))
                .param("expiresAt", timestamp(now.plus(RedeemCheckoutLogin.TOKEN_TTL)))
                .update()
            secret.code.value
        },
    )

    override fun findOpen(rawToken: String, now: Instant): OpenCheckoutLogin? {
        val digest = CheckoutLoginCode.from(rawToken)?.let(CheckoutLoginDigest::sha256) ?: return null
        return jdbc.sql(
            """
            SELECT id, user_id
            FROM subscription_checkout_login_tokens
            WHERE token_digest = :tokenDigest
              AND consumed_at IS NULL
              AND expires_at > :now
            """.trimIndent(),
        )
            .param("tokenDigest", digest.toByteArray())
            .param("now", timestamp(now))
            .query { result, _ ->
                OpenCheckoutLogin(
                    id = result.getObject("id", UUID::class.java),
                    ownerUserId = result.getObject("user_id", UUID::class.java),
                )
            }
            .optional()
            .orElse(null)
    }

    override fun consume(id: UUID, consumedAt: Instant): Boolean = jdbc.sql(
        """
        UPDATE subscription_checkout_login_tokens
        SET consumed_at = :consumedAt
        WHERE id = :id
          AND consumed_at IS NULL
        """.trimIndent(),
    )
        .param("id", id)
        .param("consumedAt", timestamp(consumedAt))
        .update() == 1

    private fun timestamp(instant: Instant): Timestamp = Timestamp.from(instant)
}
