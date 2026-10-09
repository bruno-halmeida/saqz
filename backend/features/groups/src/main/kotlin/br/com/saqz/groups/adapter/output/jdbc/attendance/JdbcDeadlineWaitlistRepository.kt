package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.application.attendance.DeadlineWaitlistCandidate
import br.com.saqz.groups.application.attendance.DeadlineWaitlistRepository
import br.com.saqz.groups.application.communication.ReminderGame
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.sql.DataSource

class JdbcDeadlineWaitlistRepository(dataSource: DataSource) : DeadlineWaitlistRepository {
    private val jdbc = JdbcClient.create(dataSource)

    /**
     * Jogos publicados, ainda por começar, com o prazo vencido, fila e vaga. Em ordem de chegada o estado
     * basta (a promoção esvazia a fila ou as vagas); em escolha manual o aviso é único por jogo.
     */
    override fun expired(now: Instant): List<DeadlineWaitlistCandidate> = jdbc.sql(
        """
        SELECT g.id AS game_id, g.group_id, ag.owner_user_id, g.local_date, g.local_time, g.venue_name, g.capacity,
               count(*) FILTER (WHERE a.status = 'CONFIRMED') AS confirmed,
               count(*) FILTER (WHERE a.status = 'WAITLISTED') AS waitlisted
        FROM games g
        JOIN access_groups ag ON ag.id = g.group_id AND ag.deleted_at IS NULL
        LEFT JOIN game_attendance a ON a.game_id = g.id
        WHERE g.status = 'PUBLISHED'
          AND g.confirmation_deadline <= :now
          AND g.starts_at > :now
          AND (ag.promotion_mode = 'FIFO' OR NOT EXISTS (
              SELECT 1 FROM group_messages m WHERE m.game_id = g.id AND m.channel = 'WAITLIST_DEADLINE'))
        GROUP BY g.id, ag.owner_user_id
        HAVING count(*) FILTER (WHERE a.status = 'WAITLISTED') > 0
           AND count(*) FILTER (WHERE a.status = 'CONFIRMED') < g.capacity
        ORDER BY g.starts_at, g.id
        """.trimIndent(),
    ).param("now", Timestamp.from(now)).query { rs, _ ->
        DeadlineWaitlistCandidate(
            gameId = rs.getObject("game_id", UUID::class.java),
            groupId = rs.getObject("group_id", UUID::class.java),
            ownerId = rs.getObject("owner_user_id", UUID::class.java),
            game = ReminderGame(
                localDate = rs.getObject("local_date", LocalDate::class.java),
                localTime = rs.getObject("local_time", LocalTime::class.java),
                venue = rs.getString("venue_name"),
            ),
            capacity = rs.getInt("capacity"),
            confirmed = rs.getInt("confirmed"),
            waitlisted = rs.getInt("waitlisted"),
        )
    }.list()
}
