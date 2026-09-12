package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.application.session.AppOnboardingCode
import br.com.saqz.access.application.session.AppOnboardingDigest
import br.com.saqz.access.application.session.AppOnboardingIssuedCode
import br.com.saqz.access.application.session.AppOnboardingOwner
import br.com.saqz.access.application.session.AppOnboardingSecrets
import br.com.saqz.access.application.session.AppOnboardingTokenStore
import br.com.saqz.access.application.session.IssueAppOnboardingLink
import br.com.saqz.access.application.session.SecureAppOnboardingSecrets
import br.com.saqz.access.domain.AccessName
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcAppOnboardingTokenStore(
    dataSource: DataSource,
    private val secrets: AppOnboardingSecrets = SecureAppOnboardingSecrets(),
) : AppOnboardingTokenStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource)).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    override fun issue(subject: String, now: Instant): AppOnboardingIssuedCode? = transaction.execute {
        val owner = jdbc.sql(
            """
            SELECT id
            FROM access_users
            WHERE firebase_subject = :subject
              AND deleted_at IS NULL
              AND suspended_at IS NULL
            FOR UPDATE
            """.trimIndent(),
        )
            .param("subject", subject)
            .query(UUID::class.java)
            .optional()
            .orElse(null) ?: return@execute null

        jdbc.sql(
            """
            UPDATE app_onboarding_login_tokens
            SET consumed_at = :now
            WHERE owner_user_id = :ownerUserId
              AND consumed_at IS NULL
            """.trimIndent(),
        )
            .param("ownerUserId", owner)
            .param("now", timestamp(now))
            .update()

        val secret = secrets.next()
        val expiresAt = now.plus(IssueAppOnboardingLink.TOKEN_TTL)
        jdbc.sql(
            """
            INSERT INTO app_onboarding_login_tokens (
                id, owner_user_id, token_digest, purpose, created_at, expires_at
            ) VALUES (
                :id, :ownerUserId, :tokenDigest, 'app-onboarding', :createdAt, :expiresAt
            )
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID())
            .param("ownerUserId", owner)
            .param("tokenDigest", secret.digest.toByteArray())
            .param("createdAt", timestamp(now))
            .param("expiresAt", timestamp(expiresAt))
            .update()
        AppOnboardingIssuedCode(owner, secret.code, expiresAt)
    }

    override fun consumeOpen(code: AppOnboardingCode, now: Instant): AppOnboardingOwner? {
        val digest = AppOnboardingDigest.sha256(code)
        return jdbc.sql(
            """
            UPDATE app_onboarding_login_tokens tokens
            SET consumed_at = :now
            FROM access_users users
            WHERE tokens.token_digest = :tokenDigest
              AND tokens.owner_user_id = users.id
              AND tokens.purpose = 'app-onboarding'
              AND tokens.consumed_at IS NULL
              AND tokens.expires_at > :now
              AND users.deleted_at IS NULL
              AND users.suspended_at IS NULL
            RETURNING users.id, users.firebase_subject, users.display_name, users.onboarding_completed_at
            """.trimIndent(),
        )
            .param("tokenDigest", digest.toByteArray())
            .param("now", timestamp(now))
            .query { result, _ ->
                AppOnboardingOwner(
                    ownerUserId = result.getObject("id", UUID::class.java),
                    firebaseSubject = result.getString("firebase_subject"),
                    displayName = AccessName.from(result.getString("display_name")),
                    onboardingCompleted = result.getTimestamp("onboarding_completed_at") != null,
                )
            }
            .optional()
            .orElse(null)
    }

    override fun onboardingCompleted(ownerUserId: UUID): Boolean = jdbc.sql(
        "SELECT onboarding_completed_at IS NOT NULL FROM access_users WHERE id = :ownerUserId AND deleted_at IS NULL",
    )
        .param("ownerUserId", ownerUserId)
        .query(Boolean::class.java)
        .optional()
        .orElse(false)

    override fun completeOnboarding(ownerUserId: UUID): Boolean = jdbc.sql(
        """
        UPDATE access_users
        SET onboarding_completed_at = COALESCE(onboarding_completed_at, now()), updated_at = now()
        WHERE id = :ownerUserId AND deleted_at IS NULL AND suspended_at IS NULL
        """.trimIndent(),
    )
        .param("ownerUserId", ownerUserId)
        .update() == 1

    private fun timestamp(instant: Instant): Timestamp = Timestamp.from(instant)
}
