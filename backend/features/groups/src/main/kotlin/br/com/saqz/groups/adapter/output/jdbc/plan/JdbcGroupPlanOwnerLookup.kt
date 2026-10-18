package br.com.saqz.groups.adapter.output.jdbc.plan

import br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupPlanOwnerLookup(dataSource: DataSource) : GroupPlanOwnerLookup {
    private val jdbc = JdbcClient.create(dataSource)
    override fun ownerOf(groupId: UUID): UUID? = jdbc.sql(
        "SELECT owner_user_id FROM access_groups WHERE id = :group AND deleted_at IS NULL",
    ).param("group", groupId).query(UUID::class.java).optional().orElse(null)

    override fun ownerForMember(groupId: UUID, actorId: UUID): UUID? = jdbc.sql(
        """
        SELECT owner_user_id FROM access_groups g
        WHERE g.id = :group AND g.deleted_at IS NULL
            AND (g.owner_user_id = :actor OR EXISTS (
                SELECT 1 FROM group_memberships m WHERE m.group_id = g.id AND m.user_id = :actor
            ))
        """.trimIndent(),
    ).param("group", groupId).param("actor", actorId).query(UUID::class.java).optional().orElse(null)

    override fun isOrganizer(groupId: UUID, actorId: UUID): Boolean = jdbc.sql(
        """
        SELECT EXISTS (
            SELECT 1 FROM access_groups g
            WHERE g.id = :group AND g.deleted_at IS NULL
                AND (g.owner_user_id = :actor OR EXISTS (
                    SELECT 1 FROM group_memberships m WHERE m.group_id = g.id AND m.user_id = :actor AND m.role = 'ADMIN'
                ))
        )
        """.trimIndent(),
    ).param("group", groupId).param("actor", actorId).query(Boolean::class.java).single()

    override fun zoneOf(groupId: UUID): ZoneId = jdbc.sql(
        "SELECT time_zone FROM access_groups WHERE id = :group",
    ).param("group", groupId).query(String::class.java).optional().map(ZoneId::of).orElse(ZoneId.of("America/Sao_Paulo"))
}
