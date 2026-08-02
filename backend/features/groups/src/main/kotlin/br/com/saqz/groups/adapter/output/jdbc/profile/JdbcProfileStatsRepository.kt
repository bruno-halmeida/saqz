package br.com.saqz.groups.adapter.output.jdbc.profile

import br.com.saqz.groups.application.profile.ProfileStatsAggregate
import br.com.saqz.groups.application.profile.ProfileStatsRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcProfileStatsRepository(dataSource: DataSource) : ProfileStatsRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(userId: UUID, now: Instant): ProfileStatsAggregate = jdbc.sql(QUERY)
        .param("userId", userId)
        .param("now", Timestamp.from(now))
        .query { result, _ ->
            ProfileStatsAggregate(
                games = result.getInt("games"),
                eligibleGames = result.getInt("eligible_games"),
                groups = result.getInt("groups"),
            )
        }
        .single()

    private companion object {
        const val QUERY = """
            SELECT
                COUNT(*) FILTER (
                    WHERE games.id IS NOT NULL AND attendance.status = 'CONFIRMED'
                ) AS games,
                COUNT(*) FILTER (
                    WHERE games.id IS NOT NULL AND attendance.status IS DISTINCT FROM 'WAITLISTED'
                ) AS eligible_games,
                COUNT(DISTINCT memberships.group_id) FILTER (
                    WHERE memberships.active
                ) AS groups
            FROM group_memberships memberships
            LEFT JOIN games
                ON games.group_id = memberships.group_id
                AND games.starts_at < :now
                AND games.starts_at >= memberships.created_at
                AND games.status IN ('PUBLISHED', 'COMPLETED')
            LEFT JOIN game_attendance attendance
                ON attendance.group_id = games.group_id
                AND attendance.game_id = games.id
                AND attendance.member_user_id = memberships.user_id
            WHERE memberships.user_id = :userId
        """
    }
}
