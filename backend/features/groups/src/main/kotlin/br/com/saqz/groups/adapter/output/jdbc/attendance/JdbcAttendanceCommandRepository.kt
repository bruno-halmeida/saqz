package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.application.game.GameAttendanceCountSource
import br.com.saqz.groups.application.game.GameAttendanceCounts
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class JdbcAttendanceCommandRepository(dataSource: DataSource) :
    AttendanceCommandRepository, AttendanceDetailQuery, AttendanceRosterQuery, GameAttendanceCountSource {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID): AttendanceAggregate? {
        val locked = jdbc.sql(
            "SELECT games.id FROM games " +
                "JOIN access_groups ag ON ag.id = games.group_id AND ag.deleted_at IS NULL " +
                "WHERE games.group_id=:group AND games.id=:game FOR UPDATE OF games, ag",
        )
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
            jdbc.sql(
                "SELECT games.waitlist_sequence_allocator + 1 FROM games " +
                    "JOIN access_groups ag ON ag.id = games.group_id AND ag.deleted_at IS NULL " +
                    "WHERE games.group_id=:group AND games.id=:game",
            )
                .param("group", groupId)
                .param("game", gameId)
                .query(Long::class.java)
                .single(),
        )

    override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate? {
        val locked = jdbc.sql(
            "SELECT games.id FROM games " +
                "JOIN access_groups ag ON ag.id = games.group_id AND ag.deleted_at IS NULL " +
                "WHERE games.group_id=:group AND games.id=:game FOR UPDATE OF games, ag",
        )
            .param("group", groupId)
            .param("game", gameId)
            .query(UUID::class.java)
            .optional()
            .orElse(null)
            ?: return null
        check(locked == gameId)
        return jdbc.sql(CAPACITY_AGGREGATE)
            .param("group", groupId)
            .param("game", gameId)
            .param("actor", actorId)
            .query { rs, _ ->
                CapacityAggregate(
                    rs.getObject("group_id", UUID::class.java),
                    rs.getObject("id", UUID::class.java),
                    actorId,
                    rs.getString("actor_role")?.let(GroupRole::valueOf),
                    GameStatus.valueOf(rs.getString("game_status")),
                    rs.getTimestamp("confirmation_deadline").toInstant(),
                    rs.getInt("capacity"),
                    rs.getInt("confirmed_count"),
                    rs.getLong("version"),
                    rs.getObject("game_fee_cents", Long::class.javaObjectType),
                    rs.getObject("local_date", java.time.LocalDate::class.java),
                    rs.getBoolean("mensalista_priority"),
                    PromotionMode.valueOf(rs.getString("promotion_mode")),
                )
            }
            .optional()
            .orElse(null)
    }

    override fun earliestWaitlisted(groupId: UUID, gameId: UUID): AttendanceRecord? =
        jdbc.sql(EARLIEST_WAITLISTED)
            .param("group", groupId)
            .param("game", gameId)
            .query { rs, _ ->
                AttendanceRecord(
                    rs.getObject("game_id", UUID::class.java),
                    rs.getObject("group_id", UUID::class.java),
                    rs.getObject("member_user_id", UUID::class.java),
                    AttendanceStatus.WAITLISTED,
                    rs.getLong("waitlist_sequence"),
                    rs.getTimestamp("responded_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getLong("version"),
                )
            }
            .optional()
            .orElse(null)

    override fun findPromotionReplay(
        groupId: UUID,
        gameId: UUID,
        actorId: UUID,
        requestId: UUID,
    ): AttendancePromotionReplay? = jdbc.sql(PROMOTION_REPLAY)
        .param("group", groupId)
        .param("game", gameId)
        .param("actor", actorId)
        .param("request", requestId)
        .query { rs, _ ->
            AttendancePromotionReplay(
                AttendanceRecord(
                    rs.getObject("game_id", UUID::class.java),
                    rs.getObject("group_id", UUID::class.java),
                    rs.getObject("member_user_id", UUID::class.java),
                    AttendanceStatus.valueOf(rs.getString("attendance_status")),
                    rs.getObject("attendance_waitlist_sequence", Long::class.javaObjectType),
                    rs.getTimestamp("attendance_responded_at").toInstant(),
                    rs.getTimestamp("attendance_updated_at").toInstant(),
                    rs.getLong("attendance_version"),
                ),
                AttendanceEvent(
                    rs.getObject("event_id", UUID::class.java),
                    rs.getObject("game_id", UUID::class.java),
                    rs.getObject("group_id", UUID::class.java),
                    rs.getObject("member_user_id", UUID::class.java),
                    rs.getObject("actor_user_id", UUID::class.java),
                    AttendanceSource.valueOf(rs.getString("source")),
                    rs.getString("old_status")?.let(AttendanceStatus::valueOf),
                    AttendanceStatus.valueOf(rs.getString("new_status")),
                    rs.getString("reason"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getObject("request_id", UUID::class.java),
                ),
            )
        }
        .optional()
        .orElse(null)

    override fun findResponseReplay(
        groupId: UUID,
        gameId: UUID,
        actorId: UUID,
        requestId: UUID,
    ): AttendanceResponseReplay? = jdbc.sql(RESPONSE_REPLAY)
        .param("group", groupId)
        .param("game", gameId)
        .param("actor", actorId)
        .param("request", requestId)
        .query { rs, _ ->
            AttendanceResponseReplay(
                AttendanceRecord(
                    rs.getObject("game_id", UUID::class.java),
                    rs.getObject("group_id", UUID::class.java),
                    rs.getObject("member_user_id", UUID::class.java),
                    AttendanceStatus.valueOf(rs.getString("attendance_status")),
                    rs.getObject("attendance_waitlist_sequence", Long::class.javaObjectType),
                    rs.getTimestamp("attendance_responded_at").toInstant(),
                    rs.getTimestamp("attendance_updated_at").toInstant(),
                    rs.getLong("attendance_version"),
                ),
                AttendanceEvent(
                    rs.getObject("event_id", UUID::class.java),
                    rs.getObject("game_id", UUID::class.java),
                    rs.getObject("group_id", UUID::class.java),
                    rs.getObject("member_user_id", UUID::class.java),
                    rs.getObject("actor_user_id", UUID::class.java),
                    AttendanceSource.valueOf(rs.getString("source")),
                    rs.getString("old_status")?.let(AttendanceStatus::valueOf),
                    AttendanceStatus.valueOf(rs.getString("new_status")),
                    rs.getString("reason"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getObject("request_id", UUID::class.java),
                ),
            )
        }
        .optional()
        .orElse(null)

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
        val appended = jdbc.sql(APPEND)
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
            .param("request", event.requestId, java.sql.Types.OTHER)
            .update()
        check(appended == 1) { "Grupo de presença excluído ou inexistente" }
    }

    override fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int): Boolean =
        jdbc.sql("UPDATE games SET capacity=:capacity,version=version+1,updated_at=now() WHERE id=:game AND version=:version AND EXISTS (SELECT 1 FROM access_groups WHERE id=(SELECT group_id FROM games WHERE id=:game) AND deleted_at IS NULL)")
            .param("capacity", capacity)
            .param("game", gameId)
            .param("version", expectedVersion)
            .update() == 1

    override fun find(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceDetail? =
        jdbc.sql(DETAIL)
            .param("actor", actorId)
            .param("group", groupId)
            .param("game", gameId)
            .query { rs, _ ->
                val ownStatus = rs.getString("own_status")?.let(AttendanceStatus::valueOf)
                val own = ownStatus?.let {
                    AttendanceRecord(
                        gameId, groupId, actorId, it,
                        rs.getObject("waitlist_sequence", Long::class.javaObjectType),
                        rs.getTimestamp("responded_at").toInstant(),
                        rs.getTimestamp("attendance_updated_at").toInstant(),
                        rs.getLong("attendance_version"),
                    )
                }
                val confirmed = rs.getInt("confirmed_count")
                val capacity = rs.getInt("capacity")
                AttendanceDetail(
                    own, confirmed, (capacity - confirmed).coerceAtLeast(0),
                    rs.getInt("waitlist_count"), capacity, rs.getLong("game_version"),
                    rs.getInt("declined_count"),
                    rs.getInt("pending_count"),
                    rs.getBoolean("auto_confirm_enabled"),
                )
            }
            .optional()
            .orElse(null)

    override fun roster(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceRoster? {
        val rows = jdbc.sql(ROSTER)
            .param("actor", actorId)
            .param("group", groupId)
            .param("game", gameId)
            .query { rs, _ ->
                RosterRow(
                    rs.getObject("member_user_id", UUID::class.java),
                    rs.getString("member_display_name"),
                    rs.getString("attendance_status"),
                    rs.getObject("waitlist_sequence", Long::class.javaObjectType),
                )
            }
            .list()
        // The header row survives a game with no responses, so an empty result
        // means the game is absent or hidden from this actor.
        if (rows.isEmpty()) return null
        val responded = rows.filter { it.memberId != null }
        return AttendanceRoster(
            responded.filter { it.status == "CONFIRMED" }.map(RosterRow::member),
            responded.filter { it.status == "WAITLISTED" }.map(RosterRow::member),
        )
    }

    override fun counts(gameIds: Set<UUID>): Map<UUID, GameAttendanceCounts> = gameIds.associateWith { gameId ->
        jdbc.sql(
            "SELECT count(*) FILTER (WHERE attendance.status='CONFIRMED') AS confirmed," +
                "count(*) FILTER (WHERE attendance.status='WAITLISTED') AS waitlisted " +
                "FROM game_attendance attendance " +
                "JOIN games game ON game.id = attendance.game_id AND game.group_id = attendance.group_id " +
                "JOIN access_groups ag ON ag.id = game.group_id AND ag.deleted_at IS NULL " +
                "WHERE attendance.game_id=:game",
        ).param("game", gameId).query { rs, _ -> GameAttendanceCounts(rs.getInt("confirmed"), rs.getInt("waitlisted")) }.single()
    }

    private data class RosterRow(
        val memberId: UUID?,
        val displayName: String?,
        val status: String?,
        val waitlistSequence: Long?,
    ) {
        fun member() = AttendanceRosterMember(requireNotNull(memberId), requireNotNull(displayName), waitlistSequence)
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
            AthleteMembershipType.valueOf(rs.getString("membership_type")),
            rs.getBoolean("mensalista_priority"),
            PromotionMode.valueOf(rs.getString("promotion_mode")),
        )
    }

    private companion object {
        const val AGGREGATE = """
            SELECT g.id,g.group_id,g.status AS game_status,g.confirmation_deadline,g.capacity,
                   g.game_fee_cents,g.local_date,target.user_id AS target_user_id,target.membership_type,
                   ag.mensalista_priority,ag.promotion_mode,
                   :actor::uuid AS actor_id,
                   CASE WHEN ag.owner_user_id=:actor THEN 'OWNER' ELSE actor.role END AS actor_role,
                   a.status AS attendance_status,a.waitlist_sequence,a.responded_at,
                   a.updated_at AS attendance_updated_at,a.version AS attendance_version,
                   (SELECT count(*) FROM game_attendance c WHERE c.game_id=g.id AND c.status='CONFIRMED') AS confirmed_count
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            JOIN group_memberships target ON target.group_id=g.group_id AND target.user_id=:member
            LEFT JOIN group_memberships actor ON actor.group_id=g.group_id AND actor.user_id=:actor
            LEFT JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=:member
            WHERE g.group_id=:group AND g.id=:game
        """
        const val SAVE = """
            INSERT INTO game_attendance
                (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,version,member_display_name)
            SELECT :game,:group,:member,:status,:sequence,:responded,:updated,:version,
                   (SELECT coalesce(nickname, display_name) FROM access_users WHERE id=:member)
            WHERE EXISTS (
                SELECT 1 FROM access_groups
                WHERE id=:group AND deleted_at IS NULL
            )
            ON CONFLICT (game_id,member_user_id) DO UPDATE SET
                status=EXCLUDED.status,waitlist_sequence=EXCLUDED.waitlist_sequence,
                updated_at=EXCLUDED.updated_at,version=EXCLUDED.version
            WHERE game_attendance.version=EXCLUDED.version-1
              AND EXISTS (
                  SELECT 1 FROM access_groups
                  WHERE id=:group AND deleted_at IS NULL
              )
        """
        const val APPEND = """
            INSERT INTO attendance_events
                (id,game_id,group_id,member_user_id,actor_user_id,source,old_status,new_status,reason,occurred_at,request_id)
            SELECT :id,:game,:group,:member,:actor,:source,:old,:new,:reason,:occurred,:request
            WHERE EXISTS (
                SELECT 1 FROM access_groups
                WHERE id=:group AND deleted_at IS NULL
            )
        """
        const val PROMOTION_REPLAY = """
            SELECT event.id AS event_id,event.game_id,event.group_id,event.member_user_id,event.actor_user_id,
                   event.source,event.old_status,event.new_status,event.reason,event.occurred_at,event.request_id,
                   attendance.status AS attendance_status,
                   attendance.waitlist_sequence AS attendance_waitlist_sequence,
                   attendance.responded_at AS attendance_responded_at,
                   attendance.updated_at AS attendance_updated_at,
                   attendance.version AS attendance_version
            FROM attendance_events event
            JOIN game_attendance attendance
              ON attendance.game_id=event.game_id AND attendance.member_user_id=event.member_user_id
            JOIN access_groups ag ON ag.id=event.group_id AND ag.deleted_at IS NULL
            WHERE event.group_id=:group AND event.game_id=:game
              AND event.actor_user_id=:actor AND event.request_id=:request
              AND event.source='ORGANIZER' AND event.new_status='CONFIRMED'
            ORDER BY event.occurred_at,event.id
            LIMIT 1
        """
        const val RESPONSE_REPLAY = """
            SELECT event.id AS event_id,event.game_id,event.group_id,event.member_user_id,event.actor_user_id,
                   event.source,event.old_status,event.new_status,event.reason,event.occurred_at,event.request_id,
                   attendance.status AS attendance_status,
                   attendance.waitlist_sequence AS attendance_waitlist_sequence,
                   attendance.responded_at AS attendance_responded_at,
                   attendance.updated_at AS attendance_updated_at,
                   attendance.version AS attendance_version
            FROM attendance_events event
            JOIN game_attendance attendance
              ON attendance.game_id=event.game_id AND attendance.member_user_id=event.member_user_id
            JOIN access_groups ag ON ag.id=event.group_id AND ag.deleted_at IS NULL
            WHERE event.group_id=:group AND event.game_id=:game
              AND event.actor_user_id=:actor AND event.request_id=:request
              AND event.source='SELF'
            ORDER BY event.occurred_at,event.id
            LIMIT 1
        """
        const val CAPACITY_AGGREGATE = """
            SELECT g.id,g.group_id,g.status AS game_status,g.confirmation_deadline,g.capacity,
                   g.version,g.game_fee_cents,g.local_date,ag.mensalista_priority,ag.promotion_mode,
                   CASE WHEN ag.owner_user_id=:actor THEN 'OWNER' ELSE actor.role END AS actor_role,
                   (SELECT count(*) FROM game_attendance c WHERE c.game_id=g.id AND c.status='CONFIRMED') AS confirmed_count
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            LEFT JOIN group_memberships actor ON actor.group_id=g.group_id AND actor.user_id=:actor
            WHERE g.group_id=:group AND g.id=:game
        """
        const val EARLIEST_WAITLISTED = """
            SELECT attendance.game_id,attendance.group_id,attendance.member_user_id,
                   attendance.waitlist_sequence,attendance.responded_at,attendance.updated_at,attendance.version
            FROM game_attendance attendance
            JOIN games game ON game.id = attendance.game_id AND game.group_id = attendance.group_id
            JOIN access_groups ag ON ag.id = game.group_id AND ag.deleted_at IS NULL
            LEFT JOIN group_memberships membership
                ON membership.group_id = attendance.group_id AND membership.user_id = attendance.member_user_id
            WHERE attendance.group_id=:group AND attendance.game_id=:game AND attendance.status='WAITLISTED'
            ORDER BY CASE
                       WHEN game.confirmation_deadline >= now() AND ag.mensalista_priority
                            AND membership.membership_type='MENSALISTA' THEN 0
                       ELSE 1
                     END,
                     attendance.waitlist_sequence
            LIMIT 1
            FOR UPDATE OF attendance
        """
        const val DETAIL = """
            SELECT g.capacity,g.version AS game_version,a.status AS own_status,a.waitlist_sequence,
                   a.responded_at,a.updated_at AS attendance_updated_at,a.version AS attendance_version,
                   (SELECT count(*) FROM game_attendance c WHERE c.game_id=g.id AND c.status='CONFIRMED') AS confirmed_count,
                   (SELECT count(*) FROM game_attendance w WHERE w.game_id=g.id AND w.status='WAITLISTED') AS waitlist_count,
                   (SELECT count(*) FROM game_attendance d WHERE d.game_id=g.id AND d.status='DECLINED') AS declined_count,
                   (SELECT count(*) FROM group_memberships pending
                    WHERE pending.group_id=g.group_id AND pending.role='ATHLETE' AND pending.active
                      AND NOT EXISTS (
                          SELECT 1 FROM game_attendance response
                          WHERE response.game_id=g.id AND response.member_user_id=pending.user_id
                      )) AS pending_count,
                   COALESCE(member.auto_confirm_enabled, false) AS auto_confirm_enabled
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            LEFT JOIN group_memberships member ON member.group_id=g.group_id AND member.user_id=:actor
            LEFT JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=:actor
            WHERE g.group_id=:group AND g.id=:game
              AND (ag.owner_user_id=:actor OR member.user_id IS NOT NULL)
        """
        const val ROSTER = """
            SELECT a.member_user_id,a.member_display_name,a.status AS attendance_status,a.waitlist_sequence,
                   m.membership_type
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            LEFT JOIN group_memberships member ON member.group_id=g.group_id AND member.user_id=:actor
            LEFT JOIN game_attendance a ON a.game_id=g.id AND a.group_id=g.group_id
                AND a.status IN ('CONFIRMED','WAITLISTED')
            LEFT JOIN group_memberships m ON m.group_id=a.group_id AND m.user_id=a.member_user_id
            WHERE g.group_id=:group AND g.id=:game
              AND (ag.owner_user_id=:actor OR member.user_id IS NOT NULL)
            ORDER BY CASE
                       WHEN a.status='CONFIRMED' THEN 0
                       WHEN a.status='WAITLISTED' AND g.confirmation_deadline >= now() AND ag.mensalista_priority
                            AND m.membership_type='MENSALISTA' THEN 1
                       WHEN a.status='WAITLISTED' AND g.confirmation_deadline >= now() AND ag.mensalista_priority
                            AND (m.membership_type IS NULL OR m.membership_type<>'MENSALISTA') THEN 2
                       ELSE 1
                     END,
                     a.waitlist_sequence NULLS FIRST,
                     lower(a.member_display_name),a.member_display_name,a.member_user_id
        """
    }
}

class AttendanceChargeAdapter(private val charges: ChargeTransactions) : AttendanceChargePort {
    override fun confirmed(aggregate: AttendanceAggregate, actorId: UUID) {
        charge(aggregate, actorId, AttendanceBillingOutcome.CONFIRMED)
    }

    override fun promoted(aggregate: AttendanceAggregate, actorId: UUID) {
        charge(aggregate, actorId, AttendanceBillingOutcome.PROMOTED)
    }

    private fun charge(aggregate: AttendanceAggregate, actorId: UUID, outcome: AttendanceBillingOutcome) {
        charges.attendance(
            GameChargeInput(
                aggregate.groupId,
                aggregate.gameId,
                aggregate.memberId,
                aggregate.gameFeeCents,
                aggregate.gameDate,
                outcome,
            ),
            actorId,
        )
    }
}
