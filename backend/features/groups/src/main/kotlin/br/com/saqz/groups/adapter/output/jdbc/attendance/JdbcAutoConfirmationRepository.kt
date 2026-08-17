package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.application.attendance.AutoConfirmationGame
import br.com.saqz.groups.application.attendance.AutoConfirmationOptInUpdate
import br.com.saqz.groups.application.attendance.AutoConfirmationRepository
import br.com.saqz.groups.application.attendance.AttendanceEvent
import br.com.saqz.groups.application.attendance.AttendanceRecord
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AutoConfirmationCandidate
import br.com.saqz.groups.domain.game.GameStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class JdbcAutoConfirmationRepository(dataSource: DataSource) : AutoConfirmationRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun updateOwnOptIn(groupId: UUID, memberId: UUID, enabled: Boolean): AutoConfirmationOptInUpdate {
        val group = jdbc.sql(OWN_MEMBERSHIP)
            .param("group", groupId)
            .param("member", memberId)
            .query { rs, _ -> rs.getString("membership_type") to rs.getBoolean("auto_confirm_enabled") }
            .optional()
            .orElse(null)
            ?: return AutoConfirmationOptInUpdate.GroupNotFound
        if (group.first != AthleteMembershipType.MENSALISTA.name) return AutoConfirmationOptInUpdate.NotMensalista
        if (!group.second) return AutoConfirmationOptInUpdate.FeatureDisabled

        jdbc.sql(
            "UPDATE group_memberships SET auto_confirm_enabled=:enabled, updated_at=now() " +
                "WHERE group_id=:group AND user_id=:member",
        )
            .param("enabled", enabled)
            .param("group", groupId)
            .param("member", memberId)
            .update()
        return AutoConfirmationOptInUpdate.Success(enabled)
    }

    override fun lockGame(groupId: UUID, gameId: UUID): AutoConfirmationGame? = jdbc.sql(GAME + " WHERE g.group_id=:group AND g.id=:game FOR UPDATE OF g")
        .param("group", groupId)
        .param("game", gameId)
        .query(::mapGame)
        .optional()
        .orElse(null)

    override fun lockOccurrence(
        groupId: UUID,
        seriesId: UUID,
        localDate: LocalDate,
        slotKey: UUID,
    ): AutoConfirmationGame? = jdbc.sql(
        GAME + " WHERE g.group_id=:group AND g.series_id=:series AND g.local_date=:localDate AND g.slot_key=:slotKey FOR UPDATE OF g",
    )
        .param("group", groupId)
        .param("series", seriesId)
        .param("localDate", localDate)
        .param("slotKey", slotKey)
        .query(::mapGame)
        .optional()
        .orElse(null)

    override fun candidates(gameId: UUID): List<AutoConfirmationCandidate> = jdbc.sql(
        """
        SELECT m.user_id, m.membership_type, m.auto_confirm_enabled, m.created_at
        FROM games g
        JOIN group_memberships m ON m.group_id=g.group_id AND m.active
        LEFT JOIN game_attendance a ON a.game_id=g.id AND a.member_user_id=m.user_id
        WHERE g.id=:game AND a.member_user_id IS NULL
        ORDER BY m.created_at, m.user_id
        """.trimIndent(),
    )
        .param("game", gameId)
        .query { rs, _ ->
            AutoConfirmationCandidate(
                rs.getObject("user_id", UUID::class.java),
                AthleteMembershipType.valueOf(rs.getString("membership_type")),
                rs.getBoolean("auto_confirm_enabled"),
                rs.getTimestamp("created_at").toInstant(),
            )
        }
        .list()

    override fun nextWaitlistSequence(groupId: UUID, gameId: UUID): Long = requireNotNull(
        jdbc.sql(
            "SELECT waitlist_sequence_allocator + 1 FROM games WHERE group_id=:group AND id=:game",
        )
            .param("group", groupId)
            .param("game", gameId)
            .query(Long::class.java)
            .optional()
            .orElse(null),
    )

    override fun save(record: AttendanceRecord) {
        check(
            jdbc.sql(SAVE)
                .param("game", record.gameId)
                .param("group", record.groupId)
                .param("member", record.memberId)
                .param("status", record.status.name, Types.OTHER)
                .param("sequence", record.waitlistSequence, Types.BIGINT)
                .param("responded", Timestamp.from(record.respondedAt))
                .param("updated", Timestamp.from(record.updatedAt))
                .param("version", record.version)
                .update() == 1,
        ) { "attendance optimistic write lost" }
    }

    override fun append(event: AttendanceEvent) {
        check(
            jdbc.sql(APPEND)
                .param("id", event.id)
                .param("game", event.gameId)
                .param("group", event.groupId)
                .param("member", event.memberId)
                .param("actor", event.actorId)
                .param("source", event.source.name, Types.OTHER)
                .param("old", event.oldStatus?.name, Types.OTHER)
                .param("new", event.newStatus.name, Types.OTHER)
                .param("reason", event.reason, Types.VARCHAR)
                .param("occurred", Timestamp.from(event.occurredAt))
                .update() == 1,
        ) { "Grupo de presença excluído ou inexistente" }
    }

    private fun mapGame(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = AutoConfirmationGame(
        rs.getObject("group_id", UUID::class.java),
        rs.getObject("id", UUID::class.java),
        rs.getObject("owner_user_id", UUID::class.java),
        GameStatus.valueOf(rs.getString("game_status")),
        rs.getInt("capacity"),
        rs.getInt("confirmed_count"),
        rs.getBoolean("auto_confirm_enabled"),
    )

    private companion object {
        const val OWN_MEMBERSHIP = """
            SELECT m.membership_type, g.auto_confirm_enabled
            FROM access_groups g
            JOIN group_memberships m ON m.group_id=g.id AND m.user_id=:member
            WHERE g.id=:group AND g.deleted_at IS NULL
            FOR UPDATE OF g
        """
        const val GAME = """
            SELECT g.id,g.group_id,g.status AS game_status,g.capacity,ag.owner_user_id,
                   ag.auto_confirm_enabled,
                   (SELECT count(*) FROM game_attendance a WHERE a.game_id=g.id AND a.status='CONFIRMED') AS confirmed_count
            FROM games g
            JOIN access_groups ag ON ag.id=g.group_id AND ag.deleted_at IS NULL
        """
        const val SAVE = """
            INSERT INTO game_attendance
                (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,version,member_display_name)
            SELECT :game,:group,:member,:status,:sequence,:responded,:updated,:version,
                   (SELECT coalesce(nickname, display_name) FROM access_users WHERE id=:member)
            WHERE EXISTS (SELECT 1 FROM access_groups WHERE id=:group AND deleted_at IS NULL)
            ON CONFLICT (game_id,member_user_id) DO UPDATE SET
                status=EXCLUDED.status,waitlist_sequence=EXCLUDED.waitlist_sequence,
                updated_at=EXCLUDED.updated_at,version=EXCLUDED.version
            WHERE game_attendance.version=EXCLUDED.version-1
              AND EXISTS (SELECT 1 FROM access_groups WHERE id=:group AND deleted_at IS NULL)
        """
        const val APPEND = """
            INSERT INTO attendance_events
                (id,game_id,group_id,member_user_id,actor_user_id,source,old_status,new_status,reason,occurred_at)
            SELECT :id,:game,:group,:member,:actor,:source,:old,:new,:reason,:occurred
            WHERE EXISTS (SELECT 1 FROM access_groups WHERE id=:group AND deleted_at IS NULL)
        """
    }
}
