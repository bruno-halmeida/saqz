package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteStatsAggregate
import br.com.saqz.groups.application.athlete.AthleteStatsRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcAthleteStatsRepository(dataSource: DataSource) : AthleteStatsRepository {
    private val jdbc = JdbcClient.create(dataSource)

    // `games` vem da view member_played_games (JOGO-006: encerrado e confirmado); WAITLISTED
    // fica fora do denominador da taxa e DECLINED é a contagem de faltas.
    override fun find(groupId: UUID, userId: UUID): AthleteStatsAggregate? = jdbc.sql(
        """
        SELECT
            (
                SELECT COUNT(*) FROM member_played_games played
                WHERE played.group_id = membership.group_id AND played.user_id = membership.user_id
            ) AS games,
            COUNT(attendance.game_id) FILTER (WHERE attendance.status <> 'WAITLISTED') AS eligible_games,
            COUNT(attendance.game_id) FILTER (WHERE attendance.status = 'DECLINED') AS absences
        FROM group_memberships membership
        LEFT JOIN game_attendance attendance
            ON attendance.group_id = membership.group_id
            AND attendance.member_user_id = membership.user_id
            AND attendance.guest_seq = 0
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
