package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.NoShowTarget
import br.com.saqz.groups.application.game.PastGameCursor
import br.com.saqz.groups.application.game.PastGameSummary
import br.com.saqz.groups.application.game.PastGamesRepository
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.sql.DataSource

class JdbcPastGamesRepository(dataSource: DataSource) : PastGamesRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun role(actorId: UUID, groupId: UUID): GroupRole? = jdbc.sql(ROLE)
        .param("actor", actorId).param("group", groupId)
        .query(String::class.java).optional().map(GroupRole::valueOf).orElse(null)

    override fun page(groupId: UUID, now: Instant, after: PastGameCursor?, limit: Int): List<PastGameSummary> = jdbc.sql(
        """
        SELECT g.id, g.title, g.local_date, g.local_time, g.zone_id, g.starts_at, g.duration_minutes, g.status,
               g.capacity, g.venue_name,
               (SELECT count(*) FROM game_attendance a
                 WHERE a.game_id = g.id AND a.group_id = g.group_id AND a.status = 'CONFIRMED'
                   AND a.no_show_marked_at IS NULL) AS confirmed_count,
               (SELECT count(*) FROM group_charges c
                 WHERE c.game_id = g.id AND c.group_id = g.group_id AND c.status = 'PENDING') AS pending_count,
               (SELECT coalesce(sum(c.amount_cents), 0) FROM group_charges c
                 WHERE c.game_id = g.id AND c.group_id = g.group_id AND c.status = 'PENDING') AS pending_cents
        FROM games g
        WHERE $PAST
          ${if (after == null) "" else "AND (g.starts_at, g.id) < (:afterStartsAt, :afterId)"}
        ORDER BY g.starts_at DESC, g.id DESC
        LIMIT :limit
        """.trimIndent(),
    )
        .param("group", groupId)
        .param("now", Timestamp.from(now))
        .param("limit", limit)
        .let { if (after == null) it else it.param("afterStartsAt", Timestamp.from(after.startsAt)).param("afterId", after.id) }
        .query { rs, _ ->
            PastGameSummary(
                id = rs.getObject("id", UUID::class.java),
                title = rs.getString("title"),
                localDate = rs.getObject("local_date", LocalDate::class.java),
                localTime = rs.getObject("local_time", LocalTime::class.java),
                zoneId = rs.getString("zone_id"),
                startsAt = rs.getTimestamp("starts_at").toInstant(),
                durationMinutes = rs.getInt("duration_minutes"),
                status = GameStatus.valueOf(rs.getString("status")),
                confirmedCount = rs.getInt("confirmed_count"),
                capacity = rs.getInt("capacity"),
                venueName = rs.getString("venue_name"),
                pendingChargeCount = rs.getInt("pending_count"),
                pendingChargeCents = rs.getLong("pending_cents"),
            )
        }
        .list()

    override fun hasAny(groupId: UUID, now: Instant): Boolean = jdbc.sql("SELECT EXISTS (SELECT 1 FROM games g WHERE $PAST)")
        .param("group", groupId).param("now", Timestamp.from(now))
        .query(Boolean::class.java).single()

    // Trava a linha da presença: marcar e desfazer ao mesmo tempo não se cruzam.
    override fun noShowTarget(groupId: UUID, gameId: UUID, memberId: UUID, guestSeq: Int): NoShowTarget? = jdbc.sql(
        """
        SELECT g.status AS game_status, g.starts_at, g.duration_minutes, a.status::text AS attendance_status
        FROM games g
        JOIN access_groups ag ON ag.id = g.group_id AND ag.deleted_at IS NULL
        LEFT JOIN game_attendance a ON a.game_id = g.id AND a.group_id = g.group_id
            AND a.member_user_id = :member AND a.guest_seq = :guestSeq
        WHERE g.group_id = :group AND g.id = :game
        FOR UPDATE OF g
        """.trimIndent(),
    )
        .param("group", groupId).param("game", gameId).param("member", memberId).param("guestSeq", guestSeq)
        .query { rs, _ ->
            NoShowTarget(
                GameStatus.valueOf(rs.getString("game_status")),
                rs.getTimestamp("starts_at").toInstant().plusSeconds(rs.getLong("duration_minutes") * 60),
                rs.getString("attendance_status"),
            )
        }
        .optional().orElse(null)

    override fun setNoShow(groupId: UUID, gameId: UUID, memberId: UUID, guestSeq: Int, marked: Boolean, actorId: UUID, at: Instant) {
        val sql = if (marked) {
            "UPDATE game_attendance SET no_show_marked_at = :at, no_show_marked_by = :actor " +
                "WHERE group_id = :group AND game_id = :game AND member_user_id = :member AND guest_seq = :guestSeq " +
                "AND status = 'CONFIRMED' AND no_show_marked_at IS NULL"
        } else {
            "UPDATE game_attendance SET no_show_marked_at = NULL, no_show_marked_by = NULL " +
                "WHERE group_id = :group AND game_id = :game AND member_user_id = :member AND guest_seq = :guestSeq " +
                "AND no_show_marked_at IS NOT NULL"
        }
        jdbc.sql(sql).param("group", groupId).param("game", gameId).param("member", memberId).param("guestSeq", guestSeq)
            .let { if (marked) it.param("at", Timestamp.from(at)).param("actor", actorId) else it }
            .update()
    }

    private companion object {
        // JOGO-011: cancelado só se chegou a ser publicado (rascunho de série cancelado em lote fica fora).
        const val PAST = "g.group_id = :group AND g.starts_at < :now " +
            "AND (g.status = 'COMPLETED' OR (g.status = 'CANCELLED' AND g.published_at IS NOT NULL)) " +
            "AND EXISTS (SELECT 1 FROM access_groups ag WHERE ag.id = g.group_id AND ag.deleted_at IS NULL)"
        const val ROLE = "SELECT CASE WHEN g.owner_user_id=:actor THEN 'OWNER' ELSE m.role::text END FROM access_groups g " +
            "LEFT JOIN group_memberships m ON m.group_id=g.id AND m.user_id=:actor " +
            "WHERE g.id=:group AND g.deleted_at IS NULL AND (g.owner_user_id=:actor OR m.user_id IS NOT NULL)"
    }
}
