package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.adapter.output.jdbc.attendance.PersonNameSql
import br.com.saqz.groups.application.game.AttendanceHistoryCursor
import br.com.saqz.groups.application.game.AttendanceHistoryRepository
import br.com.saqz.groups.application.game.AttendanceHistoryRow
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class JdbcAttendanceHistoryRepository(dataSource: DataSource) : AttendanceHistoryRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun role(actorId: UUID, groupId: UUID): GroupRole? = jdbc.sql(ROLE)
        .param("actor", actorId).param("group", groupId)
        .query(String::class.java).optional().map(GroupRole::valueOf).orElse(null)

    override fun gameExists(groupId: UUID, gameId: UUID): Boolean =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM games WHERE group_id = :group AND id = :game)")
            .param("group", groupId).param("game", gameId)
            .query(Boolean::class.java).single()

    override fun page(groupId: UUID, gameId: UUID, after: AttendanceHistoryCursor?, limit: Int): List<AttendanceHistoryRow> = jdbc.sql(
        """
        SELECT * FROM (
            SELECT e.id, e.occurred_at, e.member_user_id, e.guest_seq, e.actor_user_id, e.source, e.old_status, e.new_status,
                   e.reason, $RANK AS rank,
                   coalesce(${PersonNameSql.member("e.group_id", "e.member_user_id")}, host.member_display_name) AS member_name,
                   guest.member_display_name AS guest_name,
                   ${PersonNameSql.member("e.group_id", "e.actor_user_id")} AS actor_name,
                   e.occurred_at > g.confirmation_deadline AS after_deadline,
                   freed.count AS freed_count, freed.name AS freed_name, freed.guest AS freed_guest
            FROM attendance_events e
            JOIN games g ON g.group_id = e.group_id AND g.id = e.game_id
            LEFT JOIN game_attendance host ON host.game_id = e.game_id AND host.member_user_id = e.member_user_id AND host.guest_seq = 0
            LEFT JOIN game_attendance guest ON e.guest_seq > 0 AND guest.game_id = e.game_id
                AND guest.member_user_id = e.member_user_id AND guest.guest_seq = e.guest_seq
            -- Subida automática: a vaga é de quem saiu de confirmado no MESMO instante (mesma transação). Com mais de
            -- uma saída nesse instante não dá para saber de quem era; fica sem nome.
            LEFT JOIN LATERAL (
                SELECT count(*) AS count, max(${PersonNameSql.member("l.group_id", "l.member_user_id")}) AS name, bool_or(l.guest_seq > 0) AS guest
                FROM attendance_events l
                WHERE e.source = 'SYSTEM' AND e.old_status = 'WAITLISTED' AND e.new_status = 'CONFIRMED'
                  AND l.group_id = e.group_id AND l.game_id = e.game_id AND l.occurred_at = e.occurred_at
                  AND l.old_status = 'CONFIRMED' AND l.new_status <> 'CONFIRMED'
            ) freed ON true
            WHERE e.group_id = :group AND e.game_id = :game
        ) h
        ${if (after == null) "" else "WHERE h.occurred_at < :afterAt OR (h.occurred_at = :afterAt AND (h.rank > :afterRank OR (h.rank = :afterRank AND h.id < :afterId)))"}
        ORDER BY h.occurred_at DESC, h.rank ASC, h.id DESC
        LIMIT :limit
        """.trimIndent(),
    )
        .param("group", groupId).param("game", gameId).param("limit", limit)
        .let {
            if (after == null) it
            else it.param("afterAt", Timestamp.from(after.occurredAt)).param("afterRank", after.rank).param("afterId", after.id)
        }
        .query { rs, _ ->
            val guestSeq = rs.getInt("guest_seq")
            val freedOne = rs.getInt("freed_count") == 1
            AttendanceHistoryRow(
                id = rs.getObject("id", UUID::class.java),
                occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                rank = rs.getInt("rank"),
                memberId = rs.getObject("member_user_id", UUID::class.java),
                memberName = rs.getString("member_name") ?: "",
                guestSeq = guestSeq,
                guestName = rs.getString("guest_name").takeIf { guestSeq > 0 },
                actorId = rs.getObject("actor_user_id", UUID::class.java),
                actorName = rs.getString("actor_name") ?: "",
                source = AttendanceSource.valueOf(rs.getString("source")),
                oldStatus = rs.getString("old_status")?.let(AttendanceStatus::valueOf),
                newStatus = AttendanceStatus.valueOf(rs.getString("new_status")),
                reason = rs.getString("reason"),
                afterDeadline = rs.getBoolean("after_deadline"),
                freedByName = rs.getString("freed_name").takeIf { freedOne },
                freedByGuest = freedOne && rs.getBoolean("freed_guest"),
            )
        }
        .list()

    private companion object {
        // No mesmo instante, a subida automática vem antes (mais recente) da saída que abriu a vaga.
        const val RANK = "CASE WHEN e.source = 'SYSTEM' AND e.new_status = 'CONFIRMED' THEN 0 ELSE 1 END"
        const val ROLE = "SELECT CASE WHEN g.owner_user_id=:actor THEN 'OWNER' ELSE m.role::text END FROM access_groups g " +
            "LEFT JOIN group_memberships m ON m.group_id=g.id AND m.user_id=:actor " +
            "WHERE g.id=:group AND g.deleted_at IS NULL AND (g.owner_user_id=:actor OR m.user_id IS NOT NULL)"
    }
}
