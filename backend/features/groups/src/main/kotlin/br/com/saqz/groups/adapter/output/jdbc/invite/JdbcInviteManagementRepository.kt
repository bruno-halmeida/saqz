package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.application.invite.manage.InviteManagementRepository
import br.com.saqz.groups.application.invite.manage.RotateInviteCommand
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcInviteManagementRepository(dataSource: DataSource) : InviteManagementRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun rotate(command: RotateInviteCommand) {
        lockGroup(command.groupId)
        jdbc.sql(
            """
            INSERT INTO group_invites (
                group_id, token_digest, created_by_user_id, created_at
            ) VALUES (
                :groupId, :tokenDigest, :createdByUserId, now()
            )
            ON CONFLICT (group_id) DO UPDATE SET
                token_digest = EXCLUDED.token_digest,
                created_by_user_id = EXCLUDED.created_by_user_id,
                created_at = EXCLUDED.created_at
            """.trimIndent(),
        )
            .param("groupId", command.groupId)
            .param("tokenDigest", command.digest.toByteArray())
            .param("createdByUserId", command.createdByUserId)
            .update()
    }

    override fun expire(groupId: UUID) {
        lockGroup(groupId)
        jdbc.sql("DELETE FROM group_invites WHERE group_id = :groupId")
            .param("groupId", groupId)
            .update()
    }

    private fun lockGroup(groupId: UUID) {
        val locked = jdbc.sql("SELECT id FROM access_groups WHERE id = :groupId FOR UPDATE")
            .param("groupId", groupId)
            .query(UUID::class.java)
            .optional()
        require(locked.isPresent) { "Group does not exist" }
    }
}
