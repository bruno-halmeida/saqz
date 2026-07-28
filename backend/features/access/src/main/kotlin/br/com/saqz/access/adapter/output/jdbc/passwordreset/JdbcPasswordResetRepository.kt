package br.com.saqz.access.adapter.output.jdbc.passwordreset

import br.com.saqz.access.application.passwordreset.IpRequestWindow
import br.com.saqz.access.application.passwordreset.PasswordResetRepository
import br.com.saqz.access.application.passwordreset.ReplaceCodeOutcome
import br.com.saqz.access.application.passwordreset.ResetDigest
import br.com.saqz.access.application.passwordreset.StoredResetCode
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcPasswordResetRepository(
    dataSource: DataSource,
) : PasswordResetRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun recordIpRequest(ip: String, now: Instant, windowFloor: Instant): IpRequestWindow = jdbc.sql(
        """
        INSERT INTO password_reset_ip_limits (ip, window_started_at, request_count)
        VALUES (:ip, :now, 1)
        ON CONFLICT (ip) DO UPDATE SET
            window_started_at = CASE
                WHEN password_reset_ip_limits.window_started_at <= :windowFloor THEN :now
                ELSE password_reset_ip_limits.window_started_at
            END,
            request_count = CASE
                WHEN password_reset_ip_limits.window_started_at <= :windowFloor THEN 1
                ELSE password_reset_ip_limits.request_count + 1
            END
        RETURNING window_started_at, request_count
        """.trimIndent(),
    )
        .param("ip", ip)
        .param("now", Timestamp.from(now))
        .param("windowFloor", Timestamp.from(windowFloor))
        .query { result, _ -> IpRequestWindow(result.instant("window_started_at"), result.getInt("request_count")) }
        .single()

    override fun replaceCode(code: StoredResetCode, resendFloor: Instant): ReplaceCodeOutcome {
        val replaced = jdbc.sql(
            """
            INSERT INTO password_reset_codes (
                email, code_digest, attempts, created_at, expires_at, token_digest, token_expires_at
            ) VALUES (
                :email, :codeDigest, :attempts, :createdAt, :expiresAt, NULL, NULL
            )
            ON CONFLICT (email) DO UPDATE SET
                code_digest = EXCLUDED.code_digest,
                attempts = EXCLUDED.attempts,
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
            .param("attempts", code.attempts)
            .param("createdAt", Timestamp.from(code.createdAt))
            .param("expiresAt", Timestamp.from(code.expiresAt))
            .param("resendFloor", Timestamp.from(resendFloor))
            .query { result, _ -> result.instant("created_at") }
            .optional()

        if (replaced.isPresent) return ReplaceCodeOutcome.Replaced

        val previous = findByEmail(code.email) ?: return ReplaceCodeOutcome.Replaced
        return ReplaceCodeOutcome.TooSoon(previous.createdAt)
    }

    override fun findByEmail(email: String): StoredResetCode? = jdbc.sql(
        "SELECT email, code_digest, attempts, created_at, expires_at FROM password_reset_codes WHERE email = :email",
    )
        .param("email", email)
        .query { result, _ ->
            StoredResetCode(
                email = result.getString("email"),
                codeDigest = ResetDigest.from(result.getBytes("code_digest")),
                attempts = result.getInt("attempts"),
                createdAt = result.instant("created_at"),
                expiresAt = result.instant("expires_at"),
            )
        }
        .optional()
        .orElse(null)

    override fun recordAttempt(email: String, attempts: Int) {
        jdbc.sql("UPDATE password_reset_codes SET attempts = :attempts WHERE email = :email")
            .param("attempts", attempts)
            .param("email", email)
            .update()
    }

    override fun issueToken(email: String, tokenDigest: ResetDigest, expiresAt: Instant) {
        jdbc.sql(
            """
            UPDATE password_reset_codes
            SET token_digest = :tokenDigest, token_expires_at = :expiresAt
            WHERE email = :email
            """.trimIndent(),
        )
            .param("tokenDigest", tokenDigest.toByteArray())
            .param("expiresAt", Timestamp.from(expiresAt))
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

    override fun delete(email: String) {
        jdbc.sql("DELETE FROM password_reset_codes WHERE email = :email")
            .param("email", email)
            .update()
    }
}

private fun java.sql.ResultSet.instant(column: String): Instant = getTimestamp(column).toInstant()
