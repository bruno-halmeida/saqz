package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.application.invite.manage.InviteManagementRepository
import br.com.saqz.groups.application.invite.manage.InviteMetadata
import br.com.saqz.groups.application.invite.manage.RotateInviteCommand
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class JdbcInviteManagementRepository(dataSource: DataSource) : InviteManagementRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun rotate(command: RotateInviteCommand) {
        lockGroup(command.groupId)
        jdbc.sql(
            """
            INSERT INTO group_invites (
                group_id, token_digest, short_code, created_by_user_id, created_at, rotated_at, expires_at, revision
            ) VALUES (
                :groupId, NULL, :shortCode, :createdByUserId, now(), now(), :expiresAt, :revision
            )
            ON CONFLICT (group_id) DO UPDATE SET
                token_digest = NULL,
                short_code = EXCLUDED.short_code,
                created_by_user_id = EXCLUDED.created_by_user_id,
                created_at = EXCLUDED.created_at,
                rotated_at = EXCLUDED.rotated_at,
                expires_at = EXCLUDED.expires_at,
                revision = EXCLUDED.revision
            """.trimIndent(),
        )
            .param("groupId", command.groupId)
            .param("shortCode", command.code.value)
            .param("createdByUserId", command.createdByUserId)
            .param("expiresAt", command.expiresAt?.let(Timestamp::from))
            .param("revision", command.revision)
            .update()
        jdbc.sql("DELETE FROM group_invite_deactivations WHERE group_id = :groupId")
            .param("groupId", command.groupId)
            .update()
    }

    override fun ensureShortCode(groupId: UUID, shortCode: String, createdByUserId: UUID) {
        lockGroup(groupId)
        // O token longo legado (token_digest) fica como está: só o código curto é preenchido.
        jdbc.sql(
            """
            INSERT INTO group_invites (group_id, token_digest, short_code, created_by_user_id, created_at, expires_at)
            VALUES (:groupId, NULL, :shortCode, :createdByUserId, now(), NULL)
            ON CONFLICT (group_id) DO UPDATE SET
                short_code = COALESCE(group_invites.short_code, EXCLUDED.short_code)
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("shortCode", shortCode)
            .param("createdByUserId", createdByUserId)
            .update()
    }

    override fun isShortCodeTaken(shortCode: String): Boolean = jdbc.sql(
        """
        SELECT EXISTS (SELECT 1 FROM group_invites WHERE short_code = :shortCode)
            OR EXISTS (SELECT 1 FROM group_monthly_invite_links WHERE short_code = :shortCode)
        """.trimIndent(),
    )
        .param("shortCode", shortCode)
        .query(Boolean::class.java)
        .single()

    override fun findMetadata(groupId: UUID): InviteMetadata? = jdbc.sql(
        """
        SELECT invites.expires_at, invites.created_at, invites.revision, invites.short_code, users.display_name
        FROM group_invites invites
        JOIN access_users users ON users.id = invites.created_by_user_id
        WHERE invites.group_id = :groupId
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .query { result, _ ->
            InviteMetadata(
                expiresAt = result.getTimestamp("expires_at")?.toInstant(),
                createdAt = result.getTimestamp("created_at").toInstant(),
                createdByName = result.getString("display_name"),
                revision = result.getObject("revision", UUID::class.java),
                shortCode = result.getString("short_code"),
            )
        }
        .optional()
        .orElse(null)

    override fun expire(groupId: UUID) {
        lockGroup(groupId)
        jdbc.sql("DELETE FROM group_invites WHERE group_id = :groupId")
            .param("groupId", groupId)
            .update()
        jdbc.sql(
            """
            INSERT INTO group_invite_deactivations (group_id, deactivated_at) VALUES (:groupId, now())
            ON CONFLICT (group_id) DO UPDATE SET deactivated_at = EXCLUDED.deactivated_at
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .update()
    }

    override fun wasDeactivated(groupId: UUID): Boolean = jdbc.sql(
        "SELECT EXISTS (SELECT 1 FROM group_invite_deactivations WHERE group_id = :groupId)",
    )
        .param("groupId", groupId)
        .query(Boolean::class.java)
        .single()

    private fun lockGroup(groupId: UUID) {
        val locked = jdbc.sql(
            "SELECT id FROM access_groups WHERE id = :groupId AND deleted_at IS NULL FOR UPDATE",
        )
            .param("groupId", groupId)
            .query(UUID::class.java)
            .optional()
        require(locked.isPresent) { "Grupo excluído ou inexistente" }
    }
}
