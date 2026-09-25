package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.application.emailverification.PhoneConfirmationStore
import br.com.saqz.access.application.session.AppOnboardingDigest
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcPhoneConfirmationStore(dataSource: DataSource) : PhoneConfirmationStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun issue(subject: String, digest: AppOnboardingDigest, expiresAt: Instant): String? = jdbc.sql(
        """
        WITH owner AS (
            SELECT id, phone FROM access_users
            WHERE firebase_subject = :subject
              AND deleted_at IS NULL
              AND suspended_at IS NULL
              AND phone IS NOT NULL
              AND phone IS DISTINCT FROM verified_phone
        ), cleared AS (
            DELETE FROM access_phone_confirmations c USING owner WHERE c.user_id = owner.id
        )
        INSERT INTO access_phone_confirmations (token_digest, user_id, phone, expires_at)
        SELECT :digest, id, phone, :expiresAt FROM owner
        RETURNING phone
        """.trimIndent(),
    )
        .param("subject", subject)
        .param("digest", digest.toByteArray())
        .param("expiresAt", Timestamp.from(expiresAt))
        .query(String::class.java)
        .optional()
        .orElse(null)

    override fun confirm(digest: AppOnboardingDigest, now: Instant): Boolean = jdbc.sql(
        """
        WITH used AS (
            DELETE FROM access_phone_confirmations
            WHERE token_digest = :digest AND expires_at > :now
            RETURNING user_id, phone
        )
        UPDATE access_users u
        SET verified_phone = used.phone, updated_at = now()
        FROM used
        WHERE u.id = used.user_id AND u.phone = used.phone AND u.deleted_at IS NULL
        """.trimIndent(),
    )
        .param("digest", digest.toByteArray())
        .param("now", Timestamp.from(now))
        .update() > 0
}
