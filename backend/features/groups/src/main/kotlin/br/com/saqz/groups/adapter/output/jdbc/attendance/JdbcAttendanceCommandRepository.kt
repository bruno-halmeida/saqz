package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.application.game.GameAttendanceCountSource
import br.com.saqz.groups.application.game.GameAttendanceCounts
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.sql.Timestamp
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class JdbcAttendanceCommandRepository(
    dataSource: DataSource,
    private val changeNotice: AttendanceChangeNotice = AttendanceChangeNotice { _, _ -> },
) :
    AttendanceCommandRepository, AttendanceDetailQuery, AttendanceRosterQuery, GameAttendanceCountSource {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int): AttendanceAggregate? {
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
            .param("guest", guestSeq)
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
                    rs.getTimestamp("starts_at").toInstant(),
                )
            }
            .optional()
            .orElse(null)
    }

    override fun earliestWaitlisted(groupId: UUID, gameId: UUID, now: Instant): AttendanceRecord? =
        earliest(EARLIEST_WAITLISTED, groupId, gameId, now, null)

    override fun earliestWaitlistedIn(groupId: UUID, gameId: UUID, now: Instant, position: AthletePosition): AttendanceRecord? =
        earliest(EARLIEST_WAITLISTED_IN_POSITION, groupId, gameId, now, position)

    override fun positionLoads(gameId: UUID): List<PositionLoad> =
        jdbc.sql(POSITION_LOADS).param("game", gameId).query { rs, _ ->
            PositionLoad(
                AthletePosition.valueOf(rs.getString("position")), rs.getInt("slots"),
                rs.getInt("confirmed"), rs.getInt("waitlisted"),
            )
        }.list().sortedBy { it.position.ordinal }

    private fun earliest(sql: String, groupId: UUID, gameId: UUID, now: Instant, position: AthletePosition?): AttendanceRecord? =
        jdbc.sql(sql)
            .param("group", groupId)
            .param("game", gameId)
            .param("now", Timestamp.from(now))
            .apply { if (position != null) param("position", position.name, Types.OTHER) }
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
                    guestSeq = rs.getInt("guest_seq"),
                    position = rs.position("position"),
                    forced = rs.getBoolean("forced"),
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
                    guestSeq = rs.getInt("guest_seq"),
                    position = rs.position("attendance_position"),
                    forced = rs.getBoolean("attendance_forced"),
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
                    guestSeq = rs.getInt("guest_seq"),
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
                    guestSeq = rs.getInt("guest_seq"),
                    position = rs.position("attendance_position"),
                    forced = rs.getBoolean("attendance_forced"),
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
                    guestSeq = rs.getInt("guest_seq"),
                ),
            )
        }
        .optional()
        .orElse(null)

    override fun save(record: AttendanceRecord) = write(record, null)

    override fun saveGuest(record: AttendanceRecord, guestName: String) = write(record, guestName)

    /**
     * Versão 1 é a linha nova (INSERT); versão maior é a resposta que já existe (UPDATE com a versão anterior).
     * Os dois comandos ficam separados de propósito: um `INSERT ... ON CONFLICT DO UPDATE` dispara o gatilho
     * `enforce_monotonic_waitlist_sequence` duas vezes (BEFORE INSERT e depois BEFORE UPDATE), e a segunda
     * confere a sequência contra o alocador que a primeira já avançou, o que quebrava toda volta à espera.
     */
    private fun write(record: AttendanceRecord, guestName: String?) {
        val written = if (record.version == 1L) insert(record, guestName) else update(record)
        check(written == 1) { "attendance optimistic write lost" }
    }

    private fun insert(record: AttendanceRecord, guestName: String?): Int =
        jdbc.sql(INSERT)
            .param("game", record.gameId)
            .param("group", record.groupId)
            .param("member", record.memberId)
            .param("status", record.status.name, Types.OTHER)
            .param("sequence", record.waitlistSequence, java.sql.Types.BIGINT)
            .param("responded", Timestamp.from(record.respondedAt))
            .param("updated", Timestamp.from(record.updatedAt))
            .param("version", record.version)
            .param("guest", record.guestSeq)
            .param("guestName", guestName, java.sql.Types.VARCHAR)
            .param("position", record.position?.name, Types.OTHER)
            .param("forced", record.forced)
            .update()

    private fun update(record: AttendanceRecord): Int =
        jdbc.sql(UPDATE)
            .param("game", record.gameId)
            .param("group", record.groupId)
            .param("member", record.memberId)
            .param("status", record.status.name, Types.OTHER)
            .param("sequence", record.waitlistSequence, java.sql.Types.BIGINT)
            .param("updated", Timestamp.from(record.updatedAt))
            .param("version", record.version)
            .param("guest", record.guestSeq)
            .param("position", record.position?.name, Types.OTHER)
            .param("forced", record.forced)
            .update()

    override fun nextGuestSeq(gameId: UUID, hostId: UUID): Int =
        requireNotNull(
            jdbc.sql("SELECT coalesce(max(guest_seq),0)+1 FROM game_attendance WHERE game_id=:game AND member_user_id=:host")
                .param("game", gameId)
                .param("host", hostId)
                .query(Int::class.java)
                .single(),
        )

    override fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID): List<AttendanceRecord> =
        jdbc.sql(ACTIVE_GUESTS)
            .param("group", groupId)
            .param("game", gameId)
            .param("host", hostId)
            .query { rs, _ ->
                AttendanceRecord(
                    rs.getObject("game_id", UUID::class.java),
                    rs.getObject("group_id", UUID::class.java),
                    rs.getObject("member_user_id", UUID::class.java),
                    AttendanceStatus.valueOf(rs.getString("status")),
                    rs.getObject("waitlist_sequence", Long::class.javaObjectType),
                    rs.getTimestamp("responded_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getLong("version"),
                    guestSeq = rs.getInt("guest_seq"),
                    position = rs.position("position"),
                    forced = rs.getBoolean("forced"),
                )
            }
            .list()

    override fun futureGamesWithActiveAttendance(groupId: UUID, memberId: UUID, after: Instant): List<UUID> =
        jdbc.sql(
            """
            SELECT DISTINCT g.id FROM games g
            JOIN game_attendance a ON a.game_id=g.id AND a.group_id=g.group_id AND a.member_user_id=:member
                AND a.status IN ('CONFIRMED','WAITLISTED')
            WHERE g.group_id=:group AND g.status IN ('PUBLISHED','DRAFT') AND g.starts_at > :after
            ORDER BY g.id
            """.trimIndent(),
        )
            .param("group", groupId)
            .param("member", memberId)
            .param("after", Timestamp.from(after))
            .query(UUID::class.java)
            .list()
            .filterNotNull()

    override fun append(event: AttendanceEvent) {
        val appended = jdbc.sql(APPEND)
            .param("id", event.id)
            .param("game", event.gameId)
            .param("group", event.groupId)
            .param("member", event.memberId)
            .param("actor", event.actorId)
            .param("source", event.source.name, Types.OTHER)
            .param("old", event.oldStatus?.name, Types.OTHER)
            .param("new", event.newStatus.name, Types.OTHER)
            .param("reason", event.reason, java.sql.Types.VARCHAR)
            .param("occurred", Timestamp.from(event.occurredAt))
            .param("request", event.requestId, java.sql.Types.OTHER)
            .param("guest", event.guestSeq)
            .update()
        check(appended == 1) { "Grupo de presença excluído ou inexistente" }
        // Ponto único de toda mudança de presença (resposta, override, promoção, prazo, capacidade, saída).
        event.changeKind()?.let { changeNotice.changed(event, it) }
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
                        waitlistPosition = rs.getObject("own_waitlist_position", Long::class.javaObjectType),
                        position = rs.position("own_position"),
                        forced = rs.getBoolean("own_forced"),
                    )
                }
                val confirmed = rs.getInt("confirmed_count")
                val capacity = rs.getInt("capacity")
                AttendanceDetail(
                    own, confirmed, (capacity - confirmed).coerceAtLeast(0),
                    rs.getInt("waitlist_count"), capacity, rs.getLong("game_version"),
                    rs.getInt("declined_count"),
                    rs.getInt("pending_count"),
                )
            }
            .optional()
            .orElse(null)
            ?.let { it.copy(positions = positionLoads(gameId)) }

    override fun ownByGame(actorId: UUID, groupId: UUID): Map<UUID, AttendanceRecord> =
        jdbc.sql(OWN_BY_GAME)
            .param("actor", actorId)
            .param("group", groupId)
            .query { rs, _ ->
                AttendanceRecord(
                    rs.getObject("game_id", UUID::class.java), groupId, actorId,
                    AttendanceStatus.valueOf(rs.getString("status")),
                    rs.getObject("waitlist_sequence", Long::class.javaObjectType),
                    rs.getTimestamp("responded_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getLong("version"),
                    waitlistPosition = rs.getObject("own_waitlist_position", Long::class.javaObjectType),
                    position = rs.position("position"),
                    forced = rs.getBoolean("forced"),
                )
            }
            .list()
            .associateBy(AttendanceRecord::gameId)

    override fun positionsByGame(groupId: UUID, gameIds: Set<UUID>): Map<UUID, List<PositionLoad>> =
        if (gameIds.isEmpty()) emptyMap()
        else jdbc.sql(POSITION_LOADS_BY_GAME).param("group", groupId).param("games", gameIds.toList()).query { rs, _ ->
            rs.getObject("game_id", UUID::class.java) to PositionLoad(
                AthletePosition.valueOf(rs.getString("position")), rs.getInt("slots"),
                rs.getInt("confirmed"), rs.getInt("waitlisted"),
            )
        }.list().groupBy({ it.first }, { it.second }).mapValues { (_, loads) -> loads.sortedBy { it.position.ordinal } }

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
                    rs.getInt("guest_seq"),
                    rs.getString("host_display_name"),
                    rs.position("position"),
                    rs.getBoolean("forced"),
                )
            }
            .list()
        // The header row survives a game with no responses, so an empty result
        // means the game is absent or hidden from this actor.
        if (rows.isEmpty()) return null
        val responded = rows.filter { it.memberId != null }
        return AttendanceRoster(
            responded.filter { it.status == "CONFIRMED" }.map(RosterRow::member),
            // A consulta já vem na ordem de promoção; a posição mostrada é essa ordem, não a sequência gravada.
            // Com posições é a vez na fila da própria posição; sem posições, a fila única (position nula agrupa todos).
            responded.filter { it.status == "WAITLISTED" }.let { waiting ->
                val seen = mutableMapOf<AthletePosition?, Long>()
                waiting.map { row -> row.member(position = seen.merge(row.position, 1L, Long::plus)) }
            },
            positionLoads(gameId),
            declined(groupId, gameId),
        )
    }

    // Consulta à parte para não mexer na ordem de confirmados e espera do ROSTER (DEB-116).
    // Só quem ainda é do grupo (membro ou dono): quem saiu tem a linha de group_memberships apagada.
    private fun declined(groupId: UUID, gameId: UUID): List<AttendanceRosterMember> =
        jdbc.sql(DECLINED_ROSTER).param("group", groupId).param("game", gameId).query { rs, _ ->
            AttendanceRosterMember(
                memberId = rs.getObject("member_user_id", UUID::class.java),
                displayName = rs.getString("member_display_name"),
                position = rs.position("position"),
            )
        }.list()

    // Uma consulta para todos os jogos: a lista de jogos chamava isto com o histórico inteiro do grupo.
    override fun counts(gameIds: Set<UUID>): Map<UUID, GameAttendanceCounts> {
        if (gameIds.isEmpty()) return emptyMap()
        val counted = jdbc.sql(
            "SELECT attendance.game_id," +
                "count(*) FILTER (WHERE attendance.status='CONFIRMED') AS confirmed," +
                "count(*) FILTER (WHERE attendance.status='WAITLISTED') AS waitlisted " +
                "FROM game_attendance attendance " +
                "JOIN games game ON game.id = attendance.game_id AND game.group_id = attendance.group_id " +
                "JOIN access_groups ag ON ag.id = game.group_id AND ag.deleted_at IS NULL " +
                "WHERE attendance.game_id IN (:games) GROUP BY attendance.game_id",
        ).param("games", gameIds).query { rs, _ ->
            rs.getObject("game_id", UUID::class.java) to GameAttendanceCounts(rs.getInt("confirmed"), rs.getInt("waitlisted"))
        }.list().toMap()
        return gameIds.associateWith { counted[it] ?: GameAttendanceCounts(0, 0) }
    }

    private data class RosterRow(
        val memberId: UUID?,
        val displayName: String?,
        val status: String?,
        val waitlistSequence: Long?,
        val guestSeq: Int,
        val hostDisplayName: String?,
        val position: AthletePosition?,
        val forced: Boolean,
    ) {
        fun member(position: Long? = waitlistSequence) = AttendanceRosterMember(
            requireNotNull(memberId), requireNotNull(displayName), position, guestSeq, hostDisplayName,
            this.position, forced,
        )
    }

    private fun ResultSet.position(column: String): AthletePosition? = getString(column)?.let(AthletePosition::valueOf)

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
                guestSeq = rs.getInt("guest_seq"),
                position = rs.position("attendance_position"),
                forced = rs.getBoolean("attendance_forced"),
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
            guestSeq = rs.getInt("guest_seq"),
            guestName = rs.getString("guest_name").takeIf { rs.getInt("guest_seq") > 0 },
            startsAt = rs.getTimestamp("starts_at").toInstant(),
            position = current?.position,
            profilePosition = rs.position("profile_position"),
            profileSecondaryPosition = rs.position("profile_secondary_position"),
        )
    }

    private companion object {
        const val AGGREGATE = """
            SELECT g.id,g.group_id,g.status AS game_status,g.confirmation_deadline,g.starts_at,g.capacity,
                   g.game_fee_cents,g.local_date,target.user_id AS target_user_id,target.membership_type,
                   ag.mensalista_priority,ag.promotion_mode,
                   :actor::uuid AS actor_id,
                   CASE WHEN ag.owner_user_id=:actor THEN 'OWNER' ELSE actor.role::text END AS actor_role,
                   a.status AS attendance_status,a.waitlist_sequence,a.responded_at,
                   a.updated_at AS attendance_updated_at,a.version AS attendance_version,
                   a.position AS attendance_position,a.forced AS attendance_forced,
                   target.position AS profile_position,target.secondary_position AS profile_secondary_position,
                   (SELECT count(*) FROM game_attendance c WHERE c.game_id=g.id AND c.status='CONFIRMED') AS confirmed_count,
                   :guest::smallint AS guest_seq, a.member_display_name AS guest_name
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            JOIN group_memberships target ON target.group_id=g.group_id AND target.user_id=:member
            LEFT JOIN group_memberships actor ON actor.group_id=g.group_id AND actor.user_id=:actor
            LEFT JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=:member AND a.guest_seq=:guest
            WHERE g.group_id=:group AND g.id=:game
        """
        // Linha nova: sem ON CONFLICT DO UPDATE (ver [write]). Conflito = 0 linhas = escrita perdida.
        const val INSERT = """
            INSERT INTO game_attendance
                (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,version,member_display_name,guest_seq,position,forced)
            SELECT :game,:group,:member,:status,:sequence,:responded,:updated,:version,
                   CASE WHEN :guest > 0 THEN coalesce(:guestName, (SELECT member_display_name FROM game_attendance WHERE game_id=:game AND member_user_id=:member AND guest_seq=:guest))
                        ELSE (SELECT coalesce(nickname, display_name) FROM access_users WHERE id=:member) END,
                   :guest,:position,:forced
            WHERE EXISTS (
                SELECT 1 FROM access_groups
                WHERE id=:group AND deleted_at IS NULL
            )
            ON CONFLICT (game_id,member_user_id,guest_seq) DO NOTHING
        """
        // Resposta existente: só status, fila, data e versão; nome do snapshot e responded_at ficam como foram gravados.
        const val UPDATE = """
            UPDATE game_attendance
            SET status=:status,waitlist_sequence=:sequence,updated_at=:updated,version=:version,position=:position,forced=:forced
            WHERE game_id=:game AND member_user_id=:member AND guest_seq=:guest AND group_id=:group
              AND version=:version-1
              AND EXISTS (
                  SELECT 1 FROM access_groups
                  WHERE id=:group AND deleted_at IS NULL
              )
        """
        const val APPEND = """
            INSERT INTO attendance_events
                (id,game_id,group_id,member_user_id,actor_user_id,source,old_status,new_status,reason,occurred_at,request_id,guest_seq)
            SELECT :id,:game,:group,:member,:actor,:source,:old,:new,:reason,:occurred,:request,:guest
            WHERE EXISTS (
                SELECT 1 FROM access_groups
                WHERE id=:group AND deleted_at IS NULL
            )
        """
        const val PROMOTION_REPLAY = """
            SELECT event.id AS event_id,event.game_id,event.group_id,event.member_user_id,event.actor_user_id,
                   event.source,event.old_status,event.new_status,event.reason,event.occurred_at,event.request_id,
                   event.guest_seq,
                   attendance.status AS attendance_status,
                   attendance.waitlist_sequence AS attendance_waitlist_sequence,
                   attendance.responded_at AS attendance_responded_at,
                   attendance.updated_at AS attendance_updated_at,
                   attendance.version AS attendance_version,
                   attendance.position AS attendance_position,attendance.forced AS attendance_forced
            FROM attendance_events event
            JOIN game_attendance attendance
              ON attendance.game_id=event.game_id AND attendance.member_user_id=event.member_user_id
              AND attendance.guest_seq=event.guest_seq
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
                   event.guest_seq,
                   attendance.status AS attendance_status,
                   attendance.waitlist_sequence AS attendance_waitlist_sequence,
                   attendance.responded_at AS attendance_responded_at,
                   attendance.updated_at AS attendance_updated_at,
                   attendance.version AS attendance_version,
                   attendance.position AS attendance_position,attendance.forced AS attendance_forced
            FROM attendance_events event
            JOIN game_attendance attendance
              ON attendance.game_id=event.game_id AND attendance.member_user_id=event.member_user_id
              AND attendance.guest_seq=event.guest_seq
            JOIN access_groups ag ON ag.id=event.group_id AND ag.deleted_at IS NULL
            WHERE event.group_id=:group AND event.game_id=:game
              AND event.actor_user_id=:actor AND event.request_id=:request
              AND event.source='SELF'
              AND NOT EXISTS (
                  SELECT 1 FROM attendance_events later
                  WHERE later.game_id=event.game_id AND later.member_user_id=event.member_user_id
                    AND later.guest_seq=event.guest_seq AND later.seq>event.seq
              )
            ORDER BY event.seq DESC
            LIMIT 1
        """
        const val CAPACITY_AGGREGATE = """
            SELECT g.id,g.group_id,g.status AS game_status,g.confirmation_deadline,g.capacity,
                   g.version,g.game_fee_cents,g.local_date,g.starts_at,ag.mensalista_priority,ag.promotion_mode,
                   CASE WHEN ag.owner_user_id=:actor THEN 'OWNER' ELSE actor.role::text END AS actor_role,
                   (SELECT count(*) FROM game_attendance c WHERE c.game_id=g.id AND c.status='CONFIRMED') AS confirmed_count
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            LEFT JOIN group_memberships actor ON actor.group_id=g.group_id AND actor.user_id=:actor
            WHERE g.group_id=:group AND g.id=:game
        """
        const val ACTIVE_GUESTS = """
            SELECT game_id,group_id,member_user_id,guest_seq,status,waitlist_sequence,responded_at,updated_at,version,position,forced
            FROM game_attendance
            WHERE group_id=:group AND game_id=:game AND member_user_id=:host AND guest_seq>0
              AND status IN ('CONFIRMED','WAITLISTED')
            ORDER BY guest_seq
            FOR UPDATE
        """
        const val EARLIEST_WAITLISTED = """
            SELECT attendance.game_id,attendance.group_id,attendance.member_user_id,attendance.guest_seq,
                   attendance.waitlist_sequence,attendance.responded_at,attendance.updated_at,attendance.version,
                   attendance.position,attendance.forced
            FROM game_attendance attendance
            JOIN games game ON game.id = attendance.game_id AND game.group_id = attendance.group_id
            JOIN access_groups ag ON ag.id = game.group_id AND ag.deleted_at IS NULL
            LEFT JOIN group_memberships membership
                ON membership.group_id = attendance.group_id AND membership.user_id = attendance.member_user_id
            WHERE attendance.group_id=:group AND attendance.game_id=:game AND attendance.status='WAITLISTED'
            ORDER BY CASE
                       WHEN game.confirmation_deadline >= :now AND game.confirmation_deadline < game.starts_at AND ag.mensalista_priority
                            AND membership.membership_type='MENSALISTA' AND attendance.guest_seq=0 THEN 0
                       ELSE 1
                     END,
                     attendance.waitlist_sequence
            LIMIT 1
            FOR UPDATE OF attendance
        """
        // Mesma ordem de promoção, só entre quem espera na posição (DEB-102): vaga que abre é só daquela posição.
        val EARLIEST_WAITLISTED_IN_POSITION = EARLIEST_WAITLISTED.replace(
            "AND attendance.status='WAITLISTED'", "AND attendance.status='WAITLISTED' AND attendance.position=:position",
        )
        const val POSITION_LOADS = """
            SELECT s.position,s.slots,
                   count(a.member_user_id) FILTER (WHERE a.status='CONFIRMED') AS confirmed,
                   count(a.member_user_id) FILTER (WHERE a.status='WAITLISTED') AS waitlisted
            FROM game_position_slots s
            LEFT JOIN game_attendance a ON a.game_id=s.game_id AND a.position=s.position
            WHERE s.game_id=:game
            GROUP BY s.position,s.slots
        """
        // As mesmas cargas de POSITION_LOADS, só dos jogos que a listagem devolve, numa consulta. Parte das vagas por
        // posição (índice da chave primária): jogo sem posições não tem linha e nunca soma presença.
        const val POSITION_LOADS_BY_GAME = """
            SELECT s.game_id,s.position,s.slots,
                   count(a.member_user_id) FILTER (WHERE a.status='CONFIRMED') AS confirmed,
                   count(a.member_user_id) FILTER (WHERE a.status='WAITLISTED') AS waitlisted
            FROM game_position_slots s
            JOIN games g ON g.id=s.game_id AND g.group_id=:group
            LEFT JOIN game_attendance a ON a.game_id=s.game_id AND a.position=s.position
            WHERE s.game_id IN (:games)
            GROUP BY s.game_id,s.position,s.slots
        """
        val DETAIL = """
            SELECT g.capacity,g.version AS game_version,a.status AS own_status,a.waitlist_sequence,
                   a.position AS own_position,a.forced AS own_forced,
                   a.responded_at,a.updated_at AS attendance_updated_at,a.version AS attendance_version,
                   ${WaitlistPositionSql.of("a", "member", "g", "ag")} AS own_waitlist_position,
                   (SELECT count(*) FROM game_attendance c WHERE c.game_id=g.id AND c.status='CONFIRMED') AS confirmed_count,
                   (SELECT count(*) FROM game_attendance w WHERE w.game_id=g.id AND w.status='WAITLISTED') AS waitlist_count,
                   (SELECT count(*) FROM game_attendance d WHERE d.game_id=g.id AND d.status='DECLINED' AND d.guest_seq=0) AS declined_count,
                   -- Todo membro ativo joga, inclusive dono e admin: o papel administrativo
                   -- não dispensa ninguém de responder.
                   (SELECT count(*) FROM group_memberships pending
                    WHERE pending.group_id=g.group_id AND pending.active
                      AND NOT EXISTS (
                          SELECT 1 FROM game_attendance response
                          WHERE response.game_id=g.id AND response.member_user_id=pending.user_id
                      )) AS pending_count
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            LEFT JOIN group_memberships member ON member.group_id=g.group_id AND member.user_id=:actor
            LEFT JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=:actor AND a.guest_seq=0
            WHERE g.group_id=:group AND g.id=:game
              AND (ag.owner_user_id=:actor OR member.user_id IS NOT NULL)
        """
        // ponytail: traz as respostas do ator em todos os jogos do grupo (uma linha por jogo
        // respondido), não só nos jogos visíveis; a listagem não pagina. Se paginar, filtrar
        // por a.game_id IN (:games).
        val OWN_BY_GAME = """
            SELECT a.game_id,a.status,a.waitlist_sequence,a.responded_at,a.updated_at,a.version,a.position,a.forced,
                   ${WaitlistPositionSql.of("a", "am", "g", "ag")} AS own_waitlist_position
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
            JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=:actor AND a.guest_seq=0
            LEFT JOIN group_memberships am ON am.group_id=g.group_id AND am.user_id=:actor
            WHERE g.group_id=:group
        """
        const val DECLINED_ROSTER = """
            SELECT a.member_user_id,a.member_display_name,a.position
            FROM game_attendance a
            JOIN access_groups ag ON ag.id=a.group_id
            LEFT JOIN group_memberships m ON m.group_id=a.group_id AND m.user_id=a.member_user_id
            WHERE a.group_id=:group AND a.game_id=:game AND a.status='DECLINED' AND a.guest_seq=0
              AND (m.user_id IS NOT NULL OR ag.owner_user_id=a.member_user_id)
            ORDER BY lower(a.member_display_name),a.member_display_name,a.member_user_id
        """
        const val ROSTER = """
            SELECT a.member_user_id,a.member_display_name,a.status AS attendance_status,a.waitlist_sequence,
                   m.membership_type,a.guest_seq,a.position,coalesce(a.forced,false) AS forced,
                   (SELECT coalesce(u.nickname, u.display_name) FROM access_users u WHERE u.id=a.member_user_id AND a.guest_seq>0) AS host_display_name
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
                            AND m.membership_type='MENSALISTA' AND a.guest_seq=0 THEN 1
                       WHEN a.status='WAITLISTED' AND g.confirmation_deadline >= now() AND ag.mensalista_priority
                            AND (m.membership_type IS NULL OR m.membership_type<>'MENSALISTA' OR a.guest_seq>0) THEN 2
                       ELSE 1
                     END,
                     a.waitlist_sequence NULLS FIRST,
                     lower(a.member_display_name),a.member_display_name,a.member_user_id,a.guest_seq
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

    override fun guestRemoved(aggregate: AttendanceAggregate, actorId: UUID) =
        charges.cancelGuest(aggregate.groupId, aggregate.gameId, aggregate.memberId, aggregate.guestSeq, actorId)

    private fun charge(aggregate: AttendanceAggregate, actorId: UUID, outcome: AttendanceBillingOutcome) {
        charges.attendance(
            GameChargeInput(
                aggregate.groupId,
                aggregate.gameId,
                aggregate.memberId,
                aggregate.gameFeeCents,
                aggregate.gameDate,
                outcome,
                guestSeq = aggregate.guestSeq,
            ),
            actorId,
        )
    }
}
