package br.com.saqz.groups.adapter.output.jdbc.attendance.share

import br.com.saqz.groups.application.attendance.share.AttendanceLinkAttemptWindow
import br.com.saqz.groups.application.attendance.share.AttendanceLinkRepository
import br.com.saqz.groups.application.attendance.share.AttendanceLinkResolvableTarget
import br.com.saqz.groups.application.attendance.share.AttendanceLinkRotatableTarget
import br.com.saqz.groups.application.attendance.share.AttendanceShareSnapshot
import br.com.saqz.groups.application.attendance.share.AttendanceShareSnapshotAccess
import br.com.saqz.groups.application.attendance.share.AttendanceShareSnapshotPerson
import br.com.saqz.groups.application.attendance.share.AttendanceShareSnapshotRepository
import br.com.saqz.groups.application.attendance.share.RecordInvalidAttendanceLinkAttempt
import br.com.saqz.groups.application.attendance.share.RotateAttendanceLinkCommand
import br.com.saqz.groups.application.attendance.share.AttendanceLinkTokenDigest
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcAttendanceLinkRepository(dataSource: DataSource) : AttendanceLinkRepository, AttendanceShareSnapshotRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lockRotatableTarget(
        actorId: UUID,
        groupId: UUID,
        gameId: UUID,
    ): AttendanceLinkRotatableTarget? = jdbc.sql(LOCK_ROTATABLE_TARGET)
        .param("actor", actorId)
        .param("group", groupId)
        .param("game", gameId)
        .query { result, _ ->
            AttendanceLinkRotatableTarget(
                groupId = result.getObject("group_id", UUID::class.java),
                gameId = result.getObject("id", UUID::class.java),
                actorRole = GroupRole.valueOf(result.getString("actor_role")),
                status = GameStatus.valueOf(result.getString("status")),
                confirmationDeadline = result.getTimestamp("confirmation_deadline").toInstant(),
            )
        }
        .optional()
        .orElse(null)

    override fun rotate(command: RotateAttendanceLinkCommand) {
        jdbc.sql(ROTATE)
            .param("game", command.gameId)
            .param("group", command.groupId)
            .param("digest", command.digest.toByteArray())
            .param("createdByUserId", command.createdByUserId)
            .update()
    }

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

    override fun findSnapshotAccess(
        actorId: UUID,
        groupId: UUID,
        gameId: UUID,
    ): AttendanceShareSnapshotAccess? = jdbc.sql(SNAPSHOT_ACCESS)
        .param("actor", actorId)
        .param("group", groupId)
        .param("game", gameId)
        .query { result, _ ->
            AttendanceShareSnapshotAccess(GroupRole.valueOf(result.getString("actor_role")))
        }
        .optional()
        .orElse(null)

    override fun readSnapshot(groupId: UUID, gameId: UUID): AttendanceShareSnapshot {
        val rows = jdbc.sql(SNAPSHOT_ROWS)
            .param("group", groupId)
            .param("game", gameId)
            .query { result, _ ->
                SnapshotRow(
                    title = result.getString("title"),
                    startsAt = result.getTimestamp("starts_at").toInstant(),
                    timeZone = result.getString("zone_id"),
                    venue = result.getString("venue_name"),
                    capacity = result.getInt("capacity"),
                    memberId = result.getObject("member_user_id", UUID::class.java),
                    displayName = result.getString("display_name"),
                    status = result.getString("attendance_status"),
                    waitlistSequence = result.getObject("waitlist_sequence", Long::class.javaObjectType),
                )
            }
            .list()
        val header = rows.firstOrNull() ?: error("Attendance snapshot target vanished during read")
        return AttendanceShareSnapshot(
            title = header.title,
            startsAt = header.startsAt,
            timeZone = header.timeZone,
            venue = header.venue,
            capacity = header.capacity,
            confirmed = rows.filter { it.status == "CONFIRMED" }.sortedWith(compareBy<SnapshotRow>({ it.displayName?.lowercase() }, { it.displayName }, { it.memberId }))
                .map { AttendanceShareSnapshotPerson(requireNotNull(it.displayName)) },
            waitlisted = rows.filter { it.status == "WAITLISTED" }.sortedWith(compareBy<SnapshotRow>({ it.waitlistSequence }, { it.memberId }))
                .map { AttendanceShareSnapshotPerson(requireNotNull(it.displayName), requireNotNull(it.waitlistSequence)) },
            declined = rows.filter { it.status == "DECLINED" }.sortedWith(compareBy<SnapshotRow>({ it.displayName?.lowercase() }, { it.displayName }, { it.memberId }))
                .map { AttendanceShareSnapshotPerson(requireNotNull(it.displayName)) },
        )
    }

    private companion object {
        const val LOCK_ROTATABLE_TARGET = """
            SELECT g.id, g.group_id, g.status, g.confirmation_deadline,
                   CASE WHEN ag.owner_user_id = :actor THEN 'OWNER' ELSE membership.role END AS actor_role
            FROM games g
            JOIN access_groups ag ON ag.id = g.group_id
            LEFT JOIN group_memberships membership
                ON membership.group_id = g.group_id AND membership.user_id = :actor
            WHERE g.group_id = :group AND g.id = :game
              AND (ag.owner_user_id = :actor OR membership.user_id IS NOT NULL)
            FOR UPDATE OF g
        """

        const val ROTATE = """
            INSERT INTO game_attendance_links (
                game_id, group_id, token_digest, created_by_user_id, created_at, updated_at
            )
            VALUES (:game, :group, :digest, :createdByUserId, now(), now())
            ON CONFLICT (game_id) DO UPDATE SET
                token_digest = EXCLUDED.token_digest,
                updated_at = EXCLUDED.updated_at
        """

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
            SELECT g.group_id, g.id AS game_id, g.status, g.confirmation_deadline
            FROM game_attendance_links link
            JOIN games g ON g.id = link.game_id AND g.group_id = link.group_id
            JOIN access_groups ag ON ag.id = link.group_id
            LEFT JOIN group_memberships member ON member.group_id = link.group_id AND member.user_id = :actor
            WHERE link.token_digest = :digest
              AND (ag.owner_user_id = :actor OR member.user_id IS NOT NULL)
        """

        const val SNAPSHOT_ACCESS = """
            SELECT CASE WHEN ag.owner_user_id = :actor THEN 'OWNER' ELSE membership.role END AS actor_role
            FROM games g
            JOIN access_groups ag ON ag.id = g.group_id
            LEFT JOIN group_memberships membership
                ON membership.group_id = g.group_id AND membership.user_id = :actor
            WHERE g.group_id = :group AND g.id = :game
              AND (ag.owner_user_id = :actor OR membership.user_id IS NOT NULL)
        """

        const val SNAPSHOT_ROWS = """
            SELECT g.title, g.starts_at, g.zone_id, g.venue_name, g.capacity,
                   attendance.member_user_id, attendance.status AS attendance_status,
                   attendance.waitlist_sequence, user_account.display_name
            FROM games g
            LEFT JOIN game_attendance attendance
                ON attendance.game_id = g.id AND attendance.group_id = g.group_id
               AND attendance.status IN ('CONFIRMED', 'WAITLISTED', 'DECLINED')
            LEFT JOIN access_users user_account ON user_account.id = attendance.member_user_id
            WHERE g.group_id = :group AND g.id = :game
        """
    }

    private data class SnapshotRow(
        val title: String,
        val startsAt: Instant,
        val timeZone: String,
        val venue: String,
        val capacity: Int,
        val memberId: UUID?,
        val displayName: String?,
        val status: String?,
        val waitlistSequence: Long?,
    )
}
