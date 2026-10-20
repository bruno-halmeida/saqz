package br.com.saqz.groups.adapter.output.jdbc.monthlyinvite

import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteLinkRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcMonthlyInviteLinkRepository(dataSource: DataSource) : MonthlyInviteLinkRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lockAndFind(groupId: UUID): MonthlyInviteLink? {
        val locked = jdbc.sql("SELECT id FROM access_groups WHERE id = :groupId AND deleted_at IS NULL FOR UPDATE")
            .param("groupId", groupId)
            .query(UUID::class.java)
            .optional()
        require(locked.isPresent) { "Grupo excluído ou inexistente" }
        return find(groupId)
    }

    override fun find(groupId: UUID): MonthlyInviteLink? = jdbc.sql(
        "SELECT short_code, expires_at, invalidated_at FROM group_monthly_invite_links WHERE group_id = :groupId",
    )
        .param("groupId", groupId)
        .query { result, _ ->
            MonthlyInviteLink(
                groupId = groupId,
                shortCode = result.getString("short_code"),
                expiresAt = result.getTimestamp("expires_at").toInstant(),
                invalidatedAt = result.getTimestamp("invalidated_at")?.toInstant(),
            )
        }
        .optional()
        .orElse(null)

    override fun replaceCode(groupId: UUID, shortCode: String, expiresAt: Instant, createdByUserId: UUID, now: Instant) {
        jdbc.sql(
            """
            INSERT INTO group_monthly_invite_links (
                group_id, short_code, expires_at, invalidated_at, created_by_user_id, created_at, updated_at
            ) VALUES (:groupId, :shortCode, :expiresAt, NULL, :createdBy, :now, :now)
            ON CONFLICT (group_id) DO UPDATE SET
                short_code = EXCLUDED.short_code,
                expires_at = EXCLUDED.expires_at,
                invalidated_at = NULL,
                created_by_user_id = EXCLUDED.created_by_user_id,
                created_at = EXCLUDED.created_at,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("shortCode", shortCode)
            .param("expiresAt", Timestamp.from(expiresAt))
            .param("createdBy", createdByUserId)
            .param("now", Timestamp.from(now))
            .update()
    }

    override fun renew(groupId: UUID, expiresAt: Instant, now: Instant) {
        jdbc.sql(
            """
            UPDATE group_monthly_invite_links SET expires_at = :expiresAt, updated_at = :now
            WHERE group_id = :groupId AND short_code IS NOT NULL
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("expiresAt", Timestamp.from(expiresAt))
            .param("now", Timestamp.from(now))
            .update()
    }

    override fun invalidate(groupId: UUID, now: Instant) {
        jdbc.sql(
            """
            UPDATE group_monthly_invite_links SET short_code = NULL, invalidated_at = :now, updated_at = :now
            WHERE group_id = :groupId AND short_code IS NOT NULL
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("now", Timestamp.from(now))
            .update()
    }

    override fun isShortCodeTaken(shortCode: String): Boolean = jdbc.sql(
        """
        SELECT EXISTS (SELECT 1 FROM group_monthly_invite_links WHERE short_code = :shortCode)
            OR EXISTS (SELECT 1 FROM group_invites WHERE short_code = :shortCode)
        """.trimIndent(),
    )
        .param("shortCode", shortCode)
        .query(Boolean::class.java)
        .single()
}
