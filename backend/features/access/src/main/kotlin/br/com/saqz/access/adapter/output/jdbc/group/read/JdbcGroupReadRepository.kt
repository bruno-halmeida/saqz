package br.com.saqz.access.adapter.output.jdbc.group.read

import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.application.group.read.GroupReadSnapshot
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupReadRepository(
    dataSource: DataSource,
) : GroupReadRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(key: GroupReadKey): GroupReadSnapshot? = jdbc.sql(
        """
        SELECT
            groups.id,
            groups.name,
            groups.time_zone,
            groups.version,
            CASE
                WHEN groups.owner_user_id = :actorUserId THEN 'OWNER'
                ELSE memberships.role
            END AS resolved_role
        FROM access_groups groups
        LEFT JOIN group_memberships memberships
            ON memberships.group_id = groups.id
            AND memberships.user_id = :actorUserId
        WHERE groups.id = :groupId
        """.trimIndent(),
    )
        .param("actorUserId", key.actorUserId)
        .param("groupId", key.groupId)
        .query { result, _ ->
            GroupReadSnapshot(
                id = result.getObject("id", UUID::class.java),
                name = AccessName.from(result.getString("name")),
                timeZone = IanaTimeZone.from(result.getString("time_zone")),
                role = result.getString("resolved_role")?.let(GroupRole::valueOf),
                version = result.getLong("version"),
            )
        }
        .optional()
        .orElse(null)
}
