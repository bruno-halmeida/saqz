package br.com.saqz.access.adapter.output.jdbc.passwordreset

import br.com.saqz.access.application.passwordreset.AttemptOutcome
import br.com.saqz.access.application.passwordreset.NewResetCode
import br.com.saqz.access.application.passwordreset.PasswordResetRepository
import br.com.saqz.access.application.passwordreset.RateLimitWindow
import br.com.saqz.access.application.passwordreset.ReplaceCodeOutcome
import br.com.saqz.access.application.passwordreset.ResetDigest
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcPasswordResetRepository(
    dataSource: DataSource,
) : PasswordResetRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun recordRateLimit(bucket: String, now: Instant, windowFloor: Instant): RateLimitWindow = jdbc.sql(
        """
        INSERT INTO password_reset_rate_limits (bucket, window_started_at, request_count)
        VALUES (:bucket, :now, 1)
        ON CONFLICT (bucket) DO UPDATE SET
            window_started_at = CASE
                WHEN password_reset_rate_limits.window_started_at <= :windowFloor THEN :now
                ELSE password_reset_rate_limits.window_started_at
            END,
            request_count = CASE
                WHEN password_reset_rate_limits.window_started_at <= :windowFloor THEN 1
                ELSE password_reset_rate_limits.request_count + 1
            END
        RETURNING window_started_at, request_count
        """.trimIndent(),
    )
        .param("bucket", bucket)
        .param("now", Timestamp.from(now))
        .param("windowFloor", Timestamp.from(windowFloor))
        .query { result, _ -> RateLimitWindow(result.instant("window_started_at"), result.getInt("request_count")) }
        .single()

    override fun replaceCode(code: NewResetCode, resendFloor: Instant): ReplaceCodeOutcome {
        val replaced = jdbc.sql(
            """
            INSERT INTO password_reset_codes (
                email, code_digest, attempts, created_at, expires_at, token_digest, token_expires_at
            ) VALUES (
                :email, :codeDigest, 0, :createdAt, :expiresAt, NULL, NULL
            )
            ON CONFLICT (email) DO UPDATE SET
                code_digest = EXCLUDED.code_digest,
                attempts = 0,
                created_at = EXCLUDED.created_at,
                expires_at = EXCLUDED.expires_at,
                token_digest = NULL,
                token_expires_at = NULL
            WHERE password_reset_codes.created_at <= :resendFloor
            RETURNING created_at
            """.trimIndent(),
        )
            .param("email", code.email)
            .param("codeDigest", code.codeDigest.toByteArray())
            .param("createdAt", Timestamp.from(code.createdAt))
            .param("expiresAt", Timestamp.from(code.expiresAt))
            .param("resendFloor", Timestamp.from(resendFloor))
            .query { result, _ -> result.instant("created_at") }
            .optional()

        if (replaced.isPresent) return ReplaceCodeOutcome.Replaced

        val previousCreatedAt = createdAt(code.email) ?: return ReplaceCodeOutcome.Replaced
        return ReplaceCodeOutcome.TooSoon(previousCreatedAt)
    }

    /**
     * O UPDATE é a decisão: o teto está no próprio `WHERE`, então requisições
     * concorrentes serializam no lock da linha e cada uma recebe o seu próprio
     * `attempts`. O SELECT de fora só classifica o caso em que nada foi incrementado —
     * expirado, já trocado por token, ou teto estourado.
     */
    override fun consumeAttempt(email: String, now: Instant, ceiling: Int): AttemptOutcome? {
        val row = jdbc.sql(
            """
        WITH bumped AS (
            UPDATE password_reset_codes
            SET attempts = attempts + 1
            WHERE email = :email
              AND code_digest IS NOT NULL
              AND expires_at > :now
              AND attempts < :ceiling
            RETURNING code_digest, attempts
        )
        SELECT
            (SELECT attempts FROM bumped) AS bumped_attempts,
            (SELECT code_digest FROM bumped) AS bumped_digest,
            current.code_digest IS NOT NULL AND current.expires_at > :now AS verifiable
        FROM password_reset_codes current
        WHERE current.email = :email
            """.trimIndent(),
        )
            .param("email", email)
            .param("now", Timestamp.from(now))
            .param("ceiling", ceiling)
            .query { result, _ ->
                val attempts = result.getInt("bumped_attempts")
                AttemptRow(
                    bumpedAttempts = if (result.wasNull()) null else attempts,
                    bumpedDigest = result.getBytes("bumped_digest"),
                    verifiable = result.getBoolean("verifiable"),
                )
            }
            .optional()
            .orElse(null)
            ?: return null

        return when {
            row.bumpedAttempts != null && row.bumpedDigest != null ->
                AttemptOutcome.Consumed(ResetDigest.from(row.bumpedDigest), row.bumpedAttempts)
            row.verifiable -> AttemptOutcome.Exhausted
            else -> null
        }
    }

    override fun issueToken(email: String, tokenDigest: ResetDigest, expiresAt: Instant): Boolean = jdbc.sql(
        """
        UPDATE password_reset_codes
        SET token_digest = :tokenDigest, token_expires_at = :expiresAt, code_digest = NULL
        WHERE email = :email AND code_digest IS NOT NULL
        RETURNING email
        """.trimIndent(),
    )
        .param("tokenDigest", tokenDigest.toByteArray())
        .param("expiresAt", Timestamp.from(expiresAt))
        .param("email", email)
        .query { result, _ -> result.getString("email") }
        .optional()
        .isPresent

    override fun retireCode(email: String) {
        jdbc.sql("DELETE FROM password_reset_codes WHERE email = :email AND code_digest IS NOT NULL")
            .param("email", email)
            .update()
    }

    override fun consumeToken(tokenDigest: ResetDigest, now: Instant): String? = jdbc.sql(
        """
        DELETE FROM password_reset_codes
        WHERE token_digest = :tokenDigest AND token_expires_at > :now
        RETURNING email
        """.trimIndent(),
    )
        .param("tokenDigest", tokenDigest.toByteArray())
        .param("now", Timestamp.from(now))
        .query { result, _ -> result.getString("email") }
        .optional()
        .orElse(null)

    private fun createdAt(email: String): Instant? = jdbc.sql(
        "SELECT created_at FROM password_reset_codes WHERE email = :email",
    )
        .param("email", email)
        .query { result, _ -> result.instant("created_at") }
        .optional()
        .orElse(null)
}

private class AttemptRow(
    val bumpedAttempts: Int?,
    val bumpedDigest: ByteArray?,
    val verifiable: Boolean,
)

private fun ResultSet.instant(column: String): Instant = getTimestamp(column).toInstant()
