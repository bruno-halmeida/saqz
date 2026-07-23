package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteMembership
import br.com.saqz.groups.application.athlete.AthleteRepository
import br.com.saqz.groups.application.athlete.UpdateAthleteCommand
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class JdbcAthleteRepository(
    dataSource: DataSource,
) : AthleteRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(groupId: UUID, userId: UUID): AthleteMembership? = jdbc.sql(
        """
        SELECT
            m.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = m.user_id THEN 'OWNER' ELSE m.role END AS role,
            m.position,
            m.membership_type,
            m.active
        FROM group_memberships m
        JOIN access_users u ON u.id = m.user_id
        JOIN access_groups g ON g.id = m.group_id
        WHERE m.group_id = :groupId AND m.user_id = :userId
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query(::mapAthlete)
        .optional()
        .orElse(null)

    override fun updatePosition(groupId: UUID, userId: UUID, position: AthletePosition?): AthleteMembership = jdbc.sql(
        """
        WITH updated AS (
            UPDATE group_memberships
            SET position = :position, updated_at = now()
            WHERE group_id = :groupId AND user_id = :userId
            RETURNING user_id, group_id, role, position, membership_type, active
        )
        SELECT
            updated.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = updated.user_id THEN 'OWNER' ELSE updated.role END AS role,
            updated.position,
            updated.membership_type,
            updated.active
        FROM updated
        JOIN access_users u ON u.id = updated.user_id
        JOIN access_groups g ON g.id = updated.group_id
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .param("position", position?.name, Types.VARCHAR)
        .query(::mapAthlete)
        .single()

    override fun update(command: UpdateAthleteCommand): AthleteMembership = jdbc.sql(
        """
        WITH updated AS (
            UPDATE group_memberships
            SET position = :position, membership_type = :membershipType, active = :active, updated_at = now()
            WHERE group_id = :groupId AND user_id = :userId
            RETURNING user_id, group_id, role, position, membership_type, active
        )
        SELECT
            updated.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = updated.user_id THEN 'OWNER' ELSE updated.role END AS role,
            updated.position,
            updated.membership_type,
            updated.active
        FROM updated
        JOIN access_users u ON u.id = updated.user_id
        JOIN access_groups g ON g.id = updated.group_id
        """.trimIndent(),
    )
        .param("groupId", command.groupId)
        .param("userId", command.userId)
        .param("position", command.position?.name, Types.VARCHAR)
        .param("membershipType", command.membershipType.name)
        .param("active", command.active)
        .query(::mapAthlete)
        .single()

    override fun remove(groupId: UUID, userId: UUID) {
        jdbc.sql("DELETE FROM group_memberships WHERE group_id = :groupId AND user_id = :userId")
            .param("groupId", groupId)
            .param("userId", userId)
            .update()
    }

    private fun mapAthlete(rs: ResultSet, rowNumber: Int) = AthleteMembership(
        userId = rs.getObject("user_id", UUID::class.java),
        displayName = AccessName.from(rs.getString("display_name")),
        role = GroupRole.valueOf(rs.getString("role")),
        position = rs.getString("position")?.let(AthletePosition::valueOf),
        membershipType = AthleteMembershipType.valueOf(rs.getString("membership_type")),
        active = rs.getBoolean("active"),
    )
}
