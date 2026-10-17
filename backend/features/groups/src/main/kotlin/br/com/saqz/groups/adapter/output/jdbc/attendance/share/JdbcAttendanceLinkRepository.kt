package br.com.saqz.groups.adapter.output.jdbc.attendance.share

import br.com.saqz.groups.application.attendance.share.AttendanceLinkAttemptWindow
import br.com.saqz.groups.application.attendance.share.AttendanceLinkRepository
import br.com.saqz.groups.application.attendance.share.AttendanceLinkResolvableTarget
import br.com.saqz.groups.application.attendance.share.RecordInvalidAttendanceLinkAttempt
import br.com.saqz.groups.application.attendance.share.AttendanceLinkTokenDigest
import br.com.saqz.groups.domain.game.GameStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcAttendanceLinkRepository(dataSource: DataSource) : AttendanceLinkRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): AttendanceLinkAttemptWindow {
        jdbc.sql(INSERT_ATTEMPT_WINDOW)
            .param("userId", userId)
            .param("windowStartedAt", Timestamp.from(initializedAt))
            .update()
        return jdbc.sql(LOCK_ATTEMPT_WINDOW)
            .param("userId", userId)
            .query { result, _ ->
                AttendanceLinkAttemptWindow(
                    result.getTimestamp("window_started_at").toInstant(),
                    result.getInt("invalid_count"),
                )
            }
            .single()
    }

    override fun findResolvableTarget(
        actorId: UUID,
        digest: AttendanceLinkTokenDigest,
    ): AttendanceLinkResolvableTarget? = jdbc.sql(RESOLVABLE_TARGET)
        .param("actor", actorId)
        .param("digest", digest.toByteArray())
        .query { result, _ ->
            AttendanceLinkResolvableTarget(
                result.getObject("group_id", UUID::class.java),
                result.getObject("game_id", UUID::class.java),
                GameStatus.valueOf(result.getString("status")),
                result.getTimestamp("confirmation_deadline").toInstant(),
                result.getBoolean("registration_required"),
                result.getTimestamp("starts_at").toInstant(),
            )
        }
        .optional()
        .orElse(null)

    override fun recordInvalidAttempt(command: RecordInvalidAttendanceLinkAttempt) {
        val changed = jdbc.sql(UPDATE_ATTEMPT_WINDOW)
            .param("userId", command.userId)
            .param("windowStartedAt", Timestamp.from(command.windowStartedAt))
            .param("invalidCount", command.invalidCount)
            .update()
        check(changed == 1) { "Attendance-link attempt window was not locked" }
    }

    private companion object {
        const val INSERT_ATTEMPT_WINDOW = """
            INSERT INTO attendance_link_resolution_limits (user_id, window_started_at, invalid_count)
            VALUES (:userId, :windowStartedAt, 0)
            ON CONFLICT (user_id) DO NOTHING
        """

        const val LOCK_ATTEMPT_WINDOW = """
            SELECT window_started_at, invalid_count
            FROM attendance_link_resolution_limits
            WHERE user_id = :userId
            FOR UPDATE
        """

        const val UPDATE_ATTEMPT_WINDOW = """
            UPDATE attendance_link_resolution_limits
            SET window_started_at = :windowStartedAt,
                invalid_count = :invalidCount
            WHERE user_id = :userId
        """

        const val RESOLVABLE_TARGET = """
            SELECT m.group_id, game.id AS game_id, game.status, game.confirmation_deadline,
                   NOT member.athlete_registration_completed AS registration_required, game.starts_at
            FROM notification_attendance_links link
            JOIN group_messages m ON m.id = link.message_id
            JOIN games game ON game.id = m.game_id AND game.group_id = m.group_id
            JOIN access_groups ag ON ag.id = m.group_id AND ag.deleted_at IS NULL
            JOIN group_memberships member ON member.group_id = m.group_id AND member.user_id = :actor AND member.active
            WHERE sha256(convert_to(link.code, 'UTF8')) = :digest
        """
    }
}
