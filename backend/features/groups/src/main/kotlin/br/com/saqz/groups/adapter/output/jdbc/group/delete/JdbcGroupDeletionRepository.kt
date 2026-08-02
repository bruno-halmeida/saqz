package br.com.saqz.groups.adapter.output.jdbc.group.delete

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
        if (changed == 1) return DeleteGroupResult.Success

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
}
