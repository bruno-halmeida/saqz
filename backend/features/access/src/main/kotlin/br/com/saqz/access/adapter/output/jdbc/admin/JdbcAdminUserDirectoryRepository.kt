package br.com.saqz.access.adapter.output.jdbc.admin

import br.com.saqz.access.application.admin.AdminUserDetail
import br.com.saqz.access.application.admin.AdminUserDirectory
import br.com.saqz.access.application.admin.AdminUserGroup
import br.com.saqz.access.application.admin.AdminUserPage
import br.com.saqz.access.application.admin.AdminUserSubscription
import br.com.saqz.access.application.admin.AdminUserSummary
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminUserDirectoryRepository(
    dataSource: DataSource,
) : AdminUserDirectory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(query: String?, plan: String?, status: String?, page: Int, size: Int): AdminUserPage {
        data class Row(val summary: AdminUserSummary, val total: Long)

        val rows = jdbc.sql(
            """
            SELECT u.id, u.display_name, u.email, u.city, u.created_at, u.updated_at, u.suspended_at,
                   s.plan,
                   (SELECT count(*) FROM group_memberships m WHERE m.user_id = u.id) AS memberships,
                   (SELECT count(*) FROM access_groups g
                     WHERE g.owner_user_id = u.id AND g.deleted_at IS NULL) AS owned_groups,
                   count(*) OVER () AS total
            FROM access_users u
            LEFT JOIN subscriptions s ON s.owner_user_id = u.id AND s.status <> 'CANCELED'
            WHERE u.deleted_at IS NULL
              AND (:query::text IS NULL
                   OR u.display_name ILIKE '%' || :query || '%'
                   OR u.email ILIKE '%' || :query || '%')
              AND (:plan::text IS NULL
                   OR (:plan = 'FREE' AND s.plan IS NULL)
                   OR s.plan = :plan)
              AND (:status::text IS NULL
                   OR (:status = 'suspended' AND u.suspended_at IS NOT NULL)
                   OR (:status = 'active' AND u.suspended_at IS NULL))
            ORDER BY u.created_at DESC, u.id
            LIMIT :size OFFSET :offset
            """.trimIndent(),
        )
            .param("query", query?.trim()?.takeIf { it.isNotEmpty() })
            .param("plan", plan)
            .param("status", status)
            .param("size", size)
            .param("offset", (page - 1) * size)
            .query { rs, _ ->
                Row(
                    summary = AdminUserSummary(
                        userId = rs.getObject("id", UUID::class.java),
                        displayName = rs.getString("display_name"),
                        email = rs.getString("email"),
                        city = rs.getString("city"),
                        plan = rs.getString("plan"),
                        suspended = rs.getObject("suspended_at") != null,
                        memberships = rs.getLong("memberships"),
                        ownedGroups = rs.getLong("owned_groups"),
                        createdAt = rs.instant("created_at"),
                        lastSeenAt = rs.instant("updated_at"),
                    ),
                    total = rs.getLong("total"),
                )
            }
            .list()

        return AdminUserPage(
            items = rows.map { it.summary },
            total = rows.firstOrNull()?.total ?: 0,
            page = page,
            size = size,
        )
    }

    override fun find(userId: UUID): AdminUserDetail? {
        val user = jdbc.sql(
            """
            SELECT u.id, u.display_name, u.email, u.nickname, u.phone, u.city,
                   u.suspended_at, u.created_at, u.updated_at,
                   s.plan, s.cycle, s.status AS subscription_status, s.created_at AS subscribed_at
            FROM access_users u
            LEFT JOIN subscriptions s ON s.owner_user_id = u.id
            WHERE u.id = :id AND u.deleted_at IS NULL
            """.trimIndent(),
        )
            .param("id", userId)
            .query { rs, _ ->
                AdminUserDetail(
                    userId = rs.getObject("id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    nickname = rs.getString("nickname"),
                    phone = rs.getString("phone"),
                    city = rs.getString("city"),
                    suspendedAt = rs.instantOrNull("suspended_at"),
                    createdAt = rs.instant("created_at"),
                    lastSeenAt = rs.instant("updated_at"),
                    groups = emptyList(),
                    subscription = rs.getString("plan")?.let {
                        AdminUserSubscription(
                            plan = it,
                            cycle = rs.getString("cycle"),
                            status = rs.getString("subscription_status"),
                            since = rs.instant("subscribed_at"),
                        )
                    },
                )
            }
            .optional()
            .orElse(null) ?: return null

        val groups = jdbc.sql(
            """
            SELECT g.id, g.name,
                   CASE WHEN g.owner_user_id = :id THEN 'OWNER' ELSE m.role END AS role,
                   (SELECT count(*) FROM group_memberships gm WHERE gm.group_id = g.id) AS members
            FROM group_memberships m
            JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
            WHERE m.user_id = :id
            ORDER BY g.created_at
            """.trimIndent(),
        )
            .param("id", userId)
            .query { rs, _ ->
                AdminUserGroup(
                    groupId = rs.getObject("id", UUID::class.java),
                    name = rs.getString("name"),
                    role = rs.getString("role"),
                    members = rs.getLong("members"),
                )
            }
            .list()

        return user.copy(groups = groups)
    }

    override fun suspend(userId: UUID): Boolean = jdbc.sql(
        "UPDATE access_users SET suspended_at = COALESCE(suspended_at, now()), updated_at = now() " +
            "WHERE id = :id AND deleted_at IS NULL",
    ).param("id", userId).update() > 0

    override fun reactivate(userId: UUID): Boolean = jdbc.sql(
        "UPDATE access_users SET suspended_at = NULL, updated_at = now() " +
            "WHERE id = :id AND deleted_at IS NULL",
    ).param("id", userId).update() > 0

    private fun ResultSet.instant(column: String): Instant =
        getObject(column, OffsetDateTime::class.java).toInstant()

    private fun ResultSet.instantOrNull(column: String): Instant? =
        getObject(column, OffsetDateTime::class.java)?.toInstant()
}
