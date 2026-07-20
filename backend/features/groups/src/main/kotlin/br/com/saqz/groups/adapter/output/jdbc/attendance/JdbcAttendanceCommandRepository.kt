package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.game.GameStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class JdbcAttendanceCommandRepository(dataSource: DataSource) : AttendanceCommandRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID): AttendanceAggregate? {
        val locked = jdbc.sql("SELECT id FROM games WHERE group_id=:group AND id=:game FOR UPDATE")
            .param("group", groupId)
            .param("game", gameId)
            .query(UUID::class.java)
            .optional()
            .orElse(null)
            ?: return null
        check(locked == gameId)
        return jdbc.sql(AGGREGATE)
            .param("group", groupId)
            .param("game", gameId)
            .param("member", memberId)
            .param("actor", actorId)
            .query(::aggregate)
            .optional()
            .orElse(null)
    }

    override fun nextWaitlistSequence(groupId: UUID, gameId: UUID): Long =
        requireNotNull(
            jdbc.sql("SELECT waitlist_sequence_allocator + 1 FROM games WHERE group_id=:group AND id=:game")
                .param("group", groupId)
                .param("game", gameId)
                .query(Long::class.java)
                .single(),
        )

    override fun save(record: AttendanceRecord) {
        check(
            jdbc.sql(SAVE)
                .param("game", record.gameId)
                .param("group", record.groupId)
                .param("member", record.memberId)
                .param("status", record.status.name)
                .param("sequence", record.waitlistSequence, java.sql.Types.BIGINT)
                .param("responded", Timestamp.from(record.respondedAt))
                .param("updated", Timestamp.from(record.updatedAt))
                .param("version", record.version)
                .update() == 1,
        ) { "attendance optimistic write lost" }
    }

    override fun append(event: AttendanceEvent) {
        jdbc.sql(APPEND)
            .param("id", event.id)
            .param("game", event.gameId)
            .param("group", event.groupId)
            .param("member", event.memberId)
            .param("actor", event.actorId)
            .param("source", event.source.name)
            .param("old", event.oldStatus?.name, java.sql.Types.VARCHAR)
            .param("new", event.newStatus.name)
            .param("reason", event.reason, java.sql.Types.VARCHAR)
            .param("occurred", Timestamp.from(event.occurredAt))
            .update()
    }

    private fun aggregate(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): AttendanceAggregate {
        val currentStatus = rs.getString("attendance_status")?.let(AttendanceStatus::valueOf)
        val current = currentStatus?.let {
            AttendanceRecord(
                rs.getObject("id", UUID::class.java),
                rs.getObject("group_id", UUID::class.java),
                rs.getObject("target_user_id", UUID::class.java),
                it,
                rs.getObject("waitlist_sequence", Long::class.javaObjectType),
                rs.getTimestamp("responded_at").toInstant(),
                rs.getTimestamp("attendance_updated_at").toInstant(),
                rs.getLong("attendance_version"),
            )
        }
        return AttendanceAggregate(
            rs.getObject("group_id", UUID::class.java),
            rs.getObject("id", UUID::class.java),
            rs.getObject("target_user_id", UUID::class.java),
            rs.getObject("actor_id", UUID::class.java),
            rs.getString("actor_role")?.let(GroupRole::valueOf),
            GameStatus.valueOf(rs.getString("game_status")),
            rs.getTimestamp("confirmation_deadline").toInstant(),
            rs.getInt("capacity"),
            rs.getInt("confirmed_count"),
            current,
            rs.getObject("game_fee_cents", Long::class.javaObjectType),
            rs.getObject("local_date", java.time.LocalDate::class.java),
        )
    }

    private companion object {
        const val AGGREGATE = """
            SELECT g.id,g.group_id,g.status AS game_status,g.confirmation_deadline,g.capacity,
                   g.game_fee_cents,g.local_date,target.user_id AS target_user_id,:actor::uuid AS actor_id,
                   CASE WHEN ag.owner_user_id=:actor THEN 'OWNER' ELSE actor.role END AS actor_role,
                   a.status AS attendance_status,a.waitlist_sequence,a.responded_at,
                   a.updated_at AS attendance_updated_at,a.version AS attendance_version,
                   (SELECT count(*) FROM game_attendance c WHERE c.game_id=g.id AND c.status='CONFIRMED') AS confirmed_count
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id
            JOIN group_memberships target ON target.group_id=g.group_id AND target.user_id=:member
            LEFT JOIN group_memberships actor ON actor.group_id=g.group_id AND actor.user_id=:actor
            LEFT JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=:member
            WHERE g.group_id=:group AND g.id=:game
        """
        const val SAVE = """
            INSERT INTO game_attendance
                (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,version)
            VALUES (:game,:group,:member,:status,:sequence,:responded,:updated,:version)
            ON CONFLICT (game_id,member_user_id) DO UPDATE SET
                status=EXCLUDED.status,waitlist_sequence=EXCLUDED.waitlist_sequence,
                updated_at=EXCLUDED.updated_at,version=EXCLUDED.version
            WHERE game_attendance.version=EXCLUDED.version-1
        """
        const val APPEND = """
            INSERT INTO attendance_events
                (id,game_id,group_id,member_user_id,actor_user_id,source,old_status,new_status,reason,occurred_at)
            VALUES (:id,:game,:group,:member,:actor,:source,:old,:new,:reason,:occurred)
        """
    }
}

class AttendanceChargeAdapter(private val charges: ChargeTransactions) : AttendanceChargePort {
    override fun confirmed(aggregate: AttendanceAggregate, actorId: UUID) {
        charges.attendance(
            GameChargeInput(
                aggregate.groupId,
                aggregate.gameId,
                aggregate.memberId,
                aggregate.gameFeeCents,
                aggregate.gameDate,
                AttendanceBillingOutcome.CONFIRMED,
            ),
            actorId,
        )
    }
}
