package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.attendance.AttendanceChangeKind
import br.com.saqz.groups.application.attendance.AttendanceChangeNotice
import br.com.saqz.groups.application.attendance.AttendanceEvent
import br.com.saqz.groups.application.communication.attendanceConfirmedBody
import br.com.saqz.groups.application.communication.attendanceRemovedBody
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Aviso individual na central e no push, no canal AUTO_CONFIRMATION: os apps das lojas já o conhecem, ele segue a
 * preferência de avisos, não entra na fila do grupo de WhatsApp e não tem jogo vinculado (o CHECK de
 * `group_messages` exige jogo só nos canais de jogo), então o texto leva dia, hora e local. O dono do grupo assina.
 * Um aviso por evento: o `request_id` sai do id do evento e o UNIQUE derruba a repetição.
 */
class JdbcAttendanceChangeNotice(dataSource: DataSource) : AttendanceChangeNotice {
    private val jdbc = JdbcClient.create(dataSource)

    override fun changed(event: AttendanceEvent, kind: AttendanceChangeKind) = guarded {
        // Convidado: o aviso é do anfitrião (member_user_id do evento) e cita o nome do convidado.
        val game = jdbc.sql(
            """
            SELECT game.local_date, game.local_time, game.venue_name,
                   (SELECT a.member_display_name FROM game_attendance a
                    WHERE a.game_id = game.id AND a.member_user_id = :member AND a.guest_seq = :guest) AS guest_name
            FROM games game
            JOIN group_memberships gm ON gm.group_id = game.group_id AND gm.user_id = :member AND gm.active
            WHERE game.id = :game AND game.group_id = :group AND game.status = 'PUBLISHED' AND game.starts_at > now()
            """.trimIndent(),
        ).param("game", event.gameId).param("group", event.groupId).param("member", event.memberId).param("guest", event.guestSeq)
            .query { rs, _ ->
                Triple(
                    rs.getObject("local_date", LocalDate::class.java) to rs.getObject("local_time", LocalTime::class.java),
                    rs.getString("venue_name"),
                    rs.getString("guest_name")?.takeIf { event.guestSeq > 0 },
                )
            }.optional().orElse(null) ?: return@guarded
        val (schedule, venue, guestName) = game
        val body = when (kind) {
            AttendanceChangeKind.CONFIRMED -> attendanceConfirmedBody(schedule.first, schedule.second, venue, guestName)
            AttendanceChangeKind.REMOVED -> attendanceRemovedBody(schedule.first, schedule.second, guestName)
        }
        val message = UUID.randomUUID()
        val inserted = jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body)
            SELECT :id, g.id, g.owner_user_id, u.display_name, CAST('AUTO_CONFIRMATION' AS group_message_channel), :request, :body
            FROM access_groups g JOIN access_users u ON u.id = g.owner_user_id
            WHERE g.id = :group AND g.deleted_at IS NULL
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).param("id", message).param("group", event.groupId)
            .param("request", UUID.nameUUIDFromBytes("attendance-change:${event.id}".toByteArray())).param("body", body).update()
        if (inserted == 0) return@guarded
        jdbc.sql("INSERT INTO group_notifications (recipient_id, message_id) VALUES (:recipient, :id)")
            .param("recipient", event.memberId).param("id", message).update()
        jdbc.sql(
            "UPDATE group_messages SET recipient_count = (SELECT count(*) FROM group_notifications WHERE message_id = :id AND in_app) WHERE id = :id",
        ).param("id", message).update()
    }

    /** O aviso nunca desfaz a mudança de presença: roda num SAVEPOINT e, se falhar, volta só até ele. */
    private fun guarded(block: () -> Unit) {
        jdbc.sql("SAVEPOINT attendance_change_notice").update()
        try {
            block()
            jdbc.sql("RELEASE SAVEPOINT attendance_change_notice").update()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            jdbc.sql("ROLLBACK TO SAVEPOINT attendance_change_notice").update()
            log.warn("attendance change notice failed", failure)
        }
    }

    private companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(JdbcAttendanceChangeNotice::class.java)
    }
}
