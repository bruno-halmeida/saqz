package br.com.saqz.groups.adapter.output.jdbc.group.read

import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.application.read.GroupSummariesReadRepository
import br.com.saqz.groups.application.read.GroupSummaryReadModel
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.GroupModality
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupSummariesReadRepository(
    dataSource: DataSource,
) : GroupSummariesReadRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun findAllFor(actorUserId: UUID): List<GroupSummaryReadModel> = jdbc.sql(
        """
        SELECT
            groups.id,
            groups.name,
            groups.time_zone,
            groups.city,
            groups.modality,
            CASE
                WHEN groups.modality IS NULL OR groups.composition IS NULL THEN 'INCOMPLETE'
                ELSE groups.profile_status
            END AS profile_status,
            CASE
                WHEN groups.owner_user_id = memberships.user_id THEN 'OWNER'
                ELSE memberships.role::text
            END AS resolved_role,
            (SELECT count(*) FROM group_memberships members WHERE members.group_id = groups.id) AS member_count
        FROM group_memberships memberships
        JOIN access_groups groups ON groups.id = memberships.group_id
        WHERE memberships.user_id = :actorUserId
          AND groups.deleted_at IS NULL
        ORDER BY groups.name, groups.id
        """.trimIndent(),
    )
        .param("actorUserId", actorUserId)
        .query { result, _ ->
            GroupSummaryReadModel(
                id = result.getObject("id", UUID::class.java),
                name = AccessName.from(result.getString("name")),
                timeZone = IanaTimeZone.from(result.getString("time_zone")),
                role = GroupRole.valueOf(result.getString("resolved_role")),
                profileStatus = GroupProfileStatus.valueOf(result.getString("profile_status")),
                modality = result.getString("modality")?.let(GroupModality::valueOf),
                city = result.getString("city"),
                memberCount = result.getInt("member_count"),
            )
        }
        .list()
}
