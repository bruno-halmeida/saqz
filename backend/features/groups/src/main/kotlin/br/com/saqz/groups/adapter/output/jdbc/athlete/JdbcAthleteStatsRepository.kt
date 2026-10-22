package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteStatsAggregate
import br.com.saqz.groups.application.athlete.AthleteStatsRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcAthleteStatsRepository(dataSource: DataSource) : AthleteStatsRepository {
    private val jdbc = JdbcClient.create(dataSource)

    // Só jogo encerrado (COMPLETED) entra. `games` vem da view member_played_games (JOGO-006);
    // WAITLISTED fica fora do denominador da taxa e DECLINED ("Não vou") é a contagem de faltas;
    // a falta sem aviso (PRES-016) é contada à parte e não entra na taxa.
    override fun find(groupId: UUID, userId: UUID): AthleteStatsAggregate? = jdbc.sql(
        """
        SELECT
            (
                SELECT COUNT(*) FROM member_played_games played
                WHERE played.group_id = membership.group_id AND played.user_id = membership.user_id
            ) AS games,
            COUNT(attendance.game_id) FILTER (WHERE attendance.status <> 'WAITLISTED') AS eligible_games,
            COUNT(attendance.game_id) FILTER (WHERE attendance.status = 'DECLINED') AS absences,
            COUNT(attendance.game_id) FILTER (
                WHERE attendance.status = 'CONFIRMED' AND attendance.no_show_marked_at IS NOT NULL
            ) AS no_shows
        FROM group_memberships membership
        LEFT JOIN game_attendance attendance
            ON attendance.group_id = membership.group_id
            AND attendance.member_user_id = membership.user_id
            AND attendance.guest_seq = 0
            AND EXISTS (
                SELECT 1 FROM games
                WHERE games.id = attendance.game_id
                  AND games.group_id = attendance.group_id
                  AND games.status = 'COMPLETED'
            )
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
                noShows = rs.getLong("no_shows").toInt(),
            )
        }
        .optional()
        .orElse(null)
}
