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
                group_id, token_digest, created_by_user_id, created_at, expires_at
            ) VALUES (
                :groupId, :tokenDigest, :createdByUserId, now(), :expiresAt
            )
            ON CONFLICT (group_id) DO UPDATE SET
                token_digest = EXCLUDED.token_digest,
                created_by_user_id = EXCLUDED.created_by_user_id,
                created_at = EXCLUDED.created_at,
                expires_at = EXCLUDED.expires_at
            """.trimIndent(),
        )
            .param("groupId", command.groupId)
            .param("tokenDigest", command.digest.toByteArray())
            .param("createdByUserId", command.createdByUserId)
            .param("expiresAt", Timestamp.from(command.expiresAt))
            .update()
    }

    override fun findMetadata(groupId: UUID): InviteMetadata? = jdbc.sql(
        """
        SELECT invites.expires_at, invites.created_at, users.display_name
        FROM group_invites invites
        JOIN access_users users ON users.id = invites.created_by_user_id
        WHERE invites.group_id = :groupId
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .query { result, _ ->
            InviteMetadata(
                expiresAt = result.getTimestamp("expires_at").toInstant(),
                createdAt = result.getTimestamp("created_at").toInstant(),
                createdByName = result.getString("display_name"),
            )
        }
        .optional()
        .orElse(null)

    override fun expire(groupId: UUID) {
        lockGroup(groupId)
        jdbc.sql("DELETE FROM group_invites WHERE group_id = :groupId")
            .param("groupId", groupId)
            .update()
    }

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
