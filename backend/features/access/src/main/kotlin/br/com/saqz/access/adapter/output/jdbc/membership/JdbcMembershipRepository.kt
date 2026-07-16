package br.com.saqz.access.adapter.output.jdbc.membership

import br.com.saqz.access.application.membership.AccessMembership
import br.com.saqz.access.application.membership.ChangeMemberRoleCommand
import br.com.saqz.access.application.membership.MembershipRepository
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcMembershipRepository(
    dataSource: DataSource,
) : MembershipRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(groupId: UUID): List<AccessMembership> = jdbc.sql(
        """
        SELECT user_id, display_name, role
        FROM (
            SELECT users.id AS user_id, users.display_name, 'OWNER' AS role, 0 AS role_order
            FROM access_groups groups
            JOIN access_users users ON users.id = groups.owner_user_id
            WHERE groups.id = :groupId
            UNION ALL
            SELECT users.id AS user_id, users.display_name, memberships.role,
                CASE memberships.role WHEN 'ADMIN' THEN 1 ELSE 2 END AS role_order
            FROM group_memberships memberships
            JOIN access_users users ON users.id = memberships.user_id
            WHERE memberships.group_id = :groupId
        ) access_memberships
        ORDER BY role_order, display_name, user_id
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .query(::mapMembership)
        .list()

    override fun find(groupId: UUID, userId: UUID): AccessMembership? = jdbc.sql(
        """
        SELECT user_id, display_name, role
        FROM (
            SELECT users.id AS user_id, users.display_name, 'OWNER' AS role
            FROM access_groups groups
            JOIN access_users users ON users.id = groups.owner_user_id
            WHERE groups.id = :groupId AND users.id = :userId
            UNION ALL
            SELECT users.id AS user_id, users.display_name, memberships.role
            FROM group_memberships memberships
            JOIN access_users users ON users.id = memberships.user_id
            WHERE memberships.group_id = :groupId AND memberships.user_id = :userId
        ) target_membership
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query(::mapMembership)
        .optional()
        .orElse(null)

    override fun change(command: ChangeMemberRoleCommand): AccessMembership = jdbc.sql(
        """
        WITH changed AS (
            INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at)
            SELECT :groupId, :userId, :role, now(), now()
            FROM access_groups groups
            WHERE groups.id = :groupId AND groups.owner_user_id <> :userId
            ON CONFLICT (group_id, user_id) DO UPDATE SET
                role = EXCLUDED.role,
                updated_at = now()
            RETURNING user_id, role
        )
        SELECT users.id AS user_id, users.display_name, changed.role
        FROM changed
        JOIN access_users users ON users.id = changed.user_id
        """.trimIndent(),
    )
        .param("groupId", command.groupId)
        .param("userId", command.userId)
        .param("role", command.role.name)
        .query(::mapMembership)
        .single()

    private fun mapMembership(result: java.sql.ResultSet, rowNumber: Int) = AccessMembership(
        userId = result.getObject("user_id", UUID::class.java),
        displayName = AccessName.from(result.getString("display_name")),
        role = GroupRole.valueOf(result.getString("role")),
    )
}
