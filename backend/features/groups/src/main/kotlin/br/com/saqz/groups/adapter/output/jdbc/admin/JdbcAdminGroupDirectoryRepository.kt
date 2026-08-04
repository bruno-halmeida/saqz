package br.com.saqz.groups.adapter.output.jdbc.admin

import br.com.saqz.groups.application.admin.AdminGroupDetail
import br.com.saqz.groups.application.admin.AdminGroupDirectory
import br.com.saqz.groups.application.admin.AdminGroupGame
import br.com.saqz.groups.application.admin.AdminGroupPage
import br.com.saqz.groups.application.admin.AdminGroupSummary
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminGroupDirectoryRepository(
    dataSource: DataSource,
) : AdminGroupDirectory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(query: String?, status: String?, page: Int, size: Int): AdminGroupPage {
        data class Row(val summary: AdminGroupSummary, val total: Long)

        val rows = jdbc.sql(
            """
            SELECT g.id, g.name, g.created_at, g.deleted_at, g.owner_user_id,
                   u.display_name AS owner_name, s.plan AS owner_plan,
                   (SELECT count(*) FROM group_memberships m WHERE m.group_id = g.id) AS members,
                   (SELECT count(*) FROM games x
                     WHERE x.group_id = g.id AND x.status IN ('PUBLISHED', 'COMPLETED')
                       AND x.starts_at < now()) AS games_played,
                   count(*) OVER () AS total
            FROM access_groups g
            JOIN access_users u ON u.id = g.owner_user_id
            LEFT JOIN subscriptions s ON s.owner_user_id = g.owner_user_id AND s.status <> 'CANCELED' AND s.canceled_at IS NULL
            WHERE (:query::text IS NULL
                   OR g.name ILIKE '%' || :query || '%'
                   OR u.display_name ILIKE '%' || :query || '%')
              AND (
                   (:status::text IS NULL AND g.deleted_at IS NULL)
                   OR (:status = 'active' AND g.deleted_at IS NULL)
                   OR (:status = 'deleted' AND g.deleted_at IS NOT NULL)
              )
            ORDER BY g.created_at DESC, g.id
            LIMIT :size OFFSET :offset
            """.trimIndent(),
        )
            .param("query", query?.trim()?.takeIf { it.isNotEmpty() })
            .param("status", status)
            .param("size", size)
            .param("offset", (page - 1).toLong() * size)
            .query { rs, _ ->
                Row(
                    summary = AdminGroupSummary(
                        groupId = rs.getObject("id", UUID::class.java),
                        name = rs.getString("name"),
                        ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
                        ownerName = rs.getString("owner_name"),
                        ownerPlan = rs.getString("owner_plan"),
                        members = rs.getLong("members"),
                        gamesPlayed = rs.getLong("games_played"),
                        deleted = rs.getObject("deleted_at") != null,
                        createdAt = rs.instant("created_at"),
                    ),
                    total = rs.getLong("total"),
                )
            }
            .list()

        val total = rows.firstOrNull()?.total ?: jdbc.sql(
            """
            SELECT count(*)
            FROM access_groups g
            JOIN access_users u ON u.id = g.owner_user_id
            WHERE (:query::text IS NULL
                   OR g.name ILIKE '%' || :query || '%'
                   OR u.display_name ILIKE '%' || :query || '%')
              AND (
                   (:status::text IS NULL AND g.deleted_at IS NULL)
                   OR (:status = 'active' AND g.deleted_at IS NULL)
                   OR (:status = 'deleted' AND g.deleted_at IS NOT NULL)
              )
            """.trimIndent(),
        )
            .param("query", query?.trim()?.takeIf { it.isNotEmpty() })
            .param("status", status)
            .query(Long::class.java)
            .single()

        return AdminGroupPage(rows.map { it.summary }, total, page, size)
    }

    override fun find(groupId: UUID): AdminGroupDetail? {
        val group = jdbc.sql(
            """
            SELECT g.id, g.name, g.time_zone, g.created_at, g.deleted_at, g.owner_user_id,
                   u.display_name AS owner_name, s.plan AS owner_plan,
                   (SELECT count(*) FROM group_memberships m WHERE m.group_id = g.id) AS members,
                   (SELECT count(*) FROM games x
                     WHERE x.group_id = g.id AND x.status IN ('PUBLISHED', 'COMPLETED')
                       AND x.starts_at < now()) AS games_played
            FROM access_groups g
            JOIN access_users u ON u.id = g.owner_user_id
            LEFT JOIN subscriptions s ON s.owner_user_id = g.owner_user_id AND s.status <> 'CANCELED' AND s.canceled_at IS NULL
            WHERE g.id = :id
            """.trimIndent(),
        )
            .param("id", groupId)
            .query { rs, _ ->
                AdminGroupDetail(
                    groupId = rs.getObject("id", UUID::class.java),
                    name = rs.getString("name"),
                    timeZone = rs.getString("time_zone"),
                    ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
                    ownerName = rs.getString("owner_name"),
                    ownerPlan = rs.getString("owner_plan"),
                    members = rs.getLong("members"),
                    gamesPlayed = rs.getLong("games_played"),
                    deletedAt = rs.instantOrNull("deleted_at"),
                    createdAt = rs.instant("created_at"),
                    lastGames = emptyList(),
                )
            }
            .optional()
            .orElse(null) ?: return null

        val games = jdbc.sql(
            """
            SELECT x.id, x.title, x.starts_at, x.status,
                   (SELECT count(*) FROM game_attendance a
                     WHERE a.game_id = x.id AND a.status = 'CONFIRMED') AS confirmed
            FROM games x
            WHERE x.group_id = :id AND x.status <> 'DRAFT' AND x.starts_at < now()
            ORDER BY x.starts_at DESC
            LIMIT :limit
            """.trimIndent(),
        )
            .param("id", groupId)
            .param("limit", LAST_GAMES)
            .query { rs, _ ->
                AdminGroupGame(
                    gameId = rs.getObject("id", UUID::class.java),
                    title = rs.getString("title"),
                    startsAt = rs.instant("starts_at"),
                    status = rs.getString("status"),
                    confirmed = rs.getLong("confirmed"),
                )
            }
            .list()

        return group.copy(lastGames = games)
    }

    private fun ResultSet.instant(column: String): Instant =
        getObject(column, OffsetDateTime::class.java).toInstant()

    private fun ResultSet.instantOrNull(column: String): Instant? =
        getObject(column, OffsetDateTime::class.java)?.toInstant()

    private companion object {
        const val LAST_GAMES = 5
    }
}
