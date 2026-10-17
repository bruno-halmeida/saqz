package br.com.saqz.groups.adapter.output.jdbc.group.delete

import br.com.saqz.groups.application.communication.GROUP_DELETED_BODY
import br.com.saqz.groups.application.delete.DeleteGroupResult
import br.com.saqz.groups.application.delete.GroupDeletionRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupDeletionRepository(
    dataSource: DataSource,
) : GroupDeletionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun softDelete(actorUserId: UUID, groupId: UUID): DeleteGroupResult {
        val changed = jdbc.sql(
            """
            UPDATE access_groups
            SET deleted_at = now(), updated_at = now()
            WHERE id = :groupId
              AND owner_user_id = :actorUserId
              AND deleted_at IS NULL
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("actorUserId", actorUserId)
            .update()
        if (changed == 1) {
            noticeMembers(actorUserId, groupId)
            return DeleteGroupResult.Success
        }

        // Deliberately reads deleted rows to distinguish already-deleted/missing (404) from non-owner (403).
        val group = jdbc.sql(
            "SELECT owner_user_id, deleted_at FROM access_groups WHERE id = :groupId",
        )
            .param("groupId", groupId)
            .query { result, _ ->
                result.getObject("owner_user_id", UUID::class.java) to result.getTimestamp("deleted_at")
            }
            .optional()
            .orElse(null)

        return when {
            group == null || group.second != null -> DeleteGroupResult.GroupNotFound
            group.first != actorUserId -> DeleteGroupResult.AccessForbidden
            else -> DeleteGroupResult.GroupNotFound
        }
    }

    /**
     * Aviso de grupo excluído (SAI-008), na mesma transação da exclusão. Canal AUTO_CONFIRMATION, que os apps das
     * lojas já conhecem e cujo push leva o corpo; sem jogo, então o toque no push abre a central, não um grupo que
     * não existe mais. O `request_id` (md5 do grupo) faz o UNIQUE de `group_messages` e o UNIQUE de
     * `group_notifications` derrubarem qualquer repetição, e a V131 só deixa a entrega atravessar o filtro de grupo
     * excluído para esse id. O autor é o dono, com nome neutro: o aviso não guarda dado pessoal dele.
     */
    private fun noticeMembers(ownerUserId: UUID, groupId: UUID) {
        jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body)
            VALUES (:id, :group, :owner, 'Saqz', CAST('AUTO_CONFIRMATION' AS group_message_channel),
                    md5('group-deleted:' || CAST(:group AS text))::uuid, :body)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).param("id", UUID.randomUUID()).param("group", groupId).param("owner", ownerUserId).param("body", GROUP_DELETED_BODY).update()
        jdbc.sql(
            """
            INSERT INTO group_notifications (recipient_id, message_id)
            SELECT gm.user_id, m.id
            FROM group_memberships gm
            JOIN group_messages m ON m.group_id = gm.group_id AND m.channel = 'AUTO_CONFIRMATION'
                AND m.request_id = md5('group-deleted:' || CAST(:group AS text))::uuid
            WHERE gm.group_id = :group AND gm.user_id <> :owner
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).param("group", groupId).param("owner", ownerUserId).update()
        jdbc.sql(
            """
            UPDATE group_messages SET recipient_count = (
                SELECT count(*) FROM group_notifications WHERE message_id = group_messages.id AND in_app)
            WHERE group_id = :group AND channel = 'AUTO_CONFIRMATION'
              AND request_id = md5('group-deleted:' || CAST(:group AS text))::uuid
            """.trimIndent(),
        ).param("group", groupId).update()
    }
}
