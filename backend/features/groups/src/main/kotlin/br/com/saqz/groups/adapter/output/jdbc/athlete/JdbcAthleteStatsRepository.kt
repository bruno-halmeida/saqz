package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteStatsAggregate
import br.com.saqz.groups.application.athlete.AthleteStatsRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcAthleteStatsRepository(dataSource: DataSource) : AthleteStatsRepository {
    private val jdbc = JdbcClient.create(dataSource)

    // Every attendance row is a registered game; WAITLISTED rows are excluded from
    // the rate denominator, and DECLINED rows are the absence count.
    override fun find(groupId: UUID, userId: UUID): AthleteStatsAggregate? = jdbc.sql(
        """
        SELECT
            COUNT(attendance.game_id) AS games,
            COUNT(attendance.game_id) FILTER (WHERE attendance.status <> 'WAITLISTED') AS eligible_games,
            COUNT(attendance.game_id) FILTER (WHERE attendance.status = 'DECLINED') AS absences
        FROM group_memberships membership
        LEFT JOIN game_attendance attendance
            ON attendance.group_id = membership.group_id
            AND attendance.member_user_id = membership.user_id
        WHERE membership.group_id = :groupId
          AND membership.user_id = :userId
        GROUP BY membership.group_id, membership.user_id
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query { rs, _ ->
            AthleteStatsAggregate(
                games = rs.getLong("games").toInt(),
                eligibleGames = rs.getLong("eligible_games").toInt(),
                absences = rs.getLong("absences").toInt(),
            )
        }
        .optional()
        .orElse(null)
}
