package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.GameCommandContext
import br.com.saqz.groups.application.game.GameCommandRepository
import br.com.saqz.groups.application.game.GameCreationContext
import br.com.saqz.groups.application.game.GameQueryRepository
import br.com.saqz.groups.application.game.GameWriteResult
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.GroupGameDefaults
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class JdbcGameOccurrenceRepository(dataSource: DataSource) : GameCommandRepository, GameQueryRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun creationContext(actor: UUID, groupId: UUID): GameCreationContext? = jdbc.sql(CREATION_CONTEXT)
        .param("actor", actor)
        .param("groupId", groupId)
        .query { rs, _ ->
            GameCreationContext(
                GroupRole.valueOf(rs.getString("actor_role")),
                GroupGameDefaults(
                    title = rs.getString("name"),
                    venue = rs.getString("venue_name")?.let {
                        GameVenueSnapshot(
                            rs.getObject("default_venue_id", UUID::class.java),
                            it,
                            rs.getString("venue_address"),
                            rs.getString("venue_court"),
                        )
                    },
                    durationMinutes = rs.getObject("default_duration", Integer::class.java)?.toInt(),
                    capacity = rs.getObject("default_capacity", Integer::class.java)?.toInt(),
                    confirmationLeadMinutes = rs.getObject("default_confirmation_lead_minutes", Integer::class.java)?.toInt(),
                    gameFeeCents = rs.getObject("default_game_fee_cents", java.lang.Long::class.java)?.toLong(),
                ),
            )
        }
        .optional()
        .orElse(null)

    override fun find(actor: UUID, groupId: UUID, gameId: UUID): GameCommandContext? {
        val role = role(actor, groupId) ?: return null
        return find(groupId, gameId)?.let { GameCommandContext(role, it) }
    }

    override fun create(game: Game): GameWriteResult {
        bind(jdbc.sql(INSERT_GAME), game).update()
        return GameWriteResult.Saved(game)
    }

    override fun update(game: Game, expectedVersion: Long): GameWriteResult {
        val updated = bind(jdbc.sql(UPDATE_GAME), game)
            .param("expectedVersion", expectedVersion)
            .update()
        if (updated == 0) {
            return if (find(game.groupId, game.id) == null) GameWriteResult.NotFound else GameWriteResult.VersionConflict
        }
        return GameWriteResult.Saved(game.copy(version = expectedVersion + 1))
    }

    override fun role(actor: UUID, groupId: UUID): GroupRole? = jdbc.sql(ROLE)
        .param("actor", actor)
        .param("groupId", groupId)
        .query(String::class.java)
        .optional()
        .map(GroupRole::valueOf)
        .orElse(null)

    override fun list(groupId: UUID): List<Game> = jdbc.sql("$SELECT_GAME WHERE g.group_id = :groupId ORDER BY g.starts_at, g.id")
        .param("groupId", groupId)
        .query(::mapGame)
        .list()

    override fun find(groupId: UUID, gameId: UUID): Game? = jdbc.sql("$SELECT_GAME WHERE g.group_id = :groupId AND g.id = :gameId")
        .param("groupId", groupId)
        .param("gameId", gameId)
        .query(::mapGame)
        .optional()
        .orElse(null)

    private fun bind(statement: JdbcClient.StatementSpec, game: Game): JdbcClient.StatementSpec {
        val snapshot = game.snapshot
        return statement
            .param("id", game.id)
            .param("groupId", game.groupId)
            .param("title", snapshot.title)
            .param("localDate", snapshot.localDate)
            .param("localTime", snapshot.localTime)
            .param("zoneId", snapshot.zoneId.value)
            .param("startsAt", Timestamp.from(snapshot.startsAt))
            .param("durationMinutes", snapshot.durationMinutes)
            .param("confirmationDeadline", Timestamp.from(snapshot.confirmationDeadline))
            .param("venueId", snapshot.venue.venueId, Types.OTHER)
            .param("venueName", snapshot.venue.name)
            .param("venueAddress", snapshot.venue.address)
            .param("venueCourt", snapshot.venue.court, Types.VARCHAR)
            .param("capacity", snapshot.capacity)
            .param("gameFeeCents", snapshot.gameFeeCents, Types.BIGINT)
            .param("notes", snapshot.notes, Types.VARCHAR)
            .param("status", game.status.name)
    }

    private fun mapGame(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): Game = Game(
        id = rs.getObject("id", UUID::class.java),
        groupId = rs.getObject("group_id", UUID::class.java),
        snapshot = GameSnapshot(
            title = rs.getString("title"),
            venue = GameVenueSnapshot(
                rs.getObject("venue_id", UUID::class.java),
                rs.getString("venue_name"),
                rs.getString("venue_address"),
                rs.getString("venue_court"),
            ),
            localDate = rs.getObject("local_date", java.time.LocalDate::class.java),
            localTime = rs.getObject("local_time", java.time.LocalTime::class.java),
            zoneId = IanaTimeZone.from(rs.getString("zone_id")),
            startsAt = rs.getTimestamp("starts_at").toInstant(),
            durationMinutes = rs.getInt("duration_minutes"),
            capacity = rs.getInt("capacity"),
            confirmationDeadline = rs.getTimestamp("confirmation_deadline").toInstant(),
            gameFeeCents = rs.getObject("game_fee_cents", java.lang.Long::class.java)?.toLong(),
            notes = rs.getString("notes"),
        ),
        status = GameStatus.valueOf(rs.getString("status")),
        version = rs.getLong("version"),
    )

    private companion object {
        const val ROLE = """
            SELECT CASE WHEN g.owner_user_id = :actor THEN 'OWNER' ELSE m.role END
            FROM access_groups g
            LEFT JOIN group_memberships m ON m.group_id = g.id AND m.user_id = :actor
            WHERE g.id = :groupId AND (g.owner_user_id = :actor OR m.user_id IS NOT NULL)
        """

        const val CREATION_CONTEXT = """
            SELECT g.name, g.default_venue_id, g.default_capacity,
                   g.default_confirmation_lead_minutes, g.default_game_fee_cents,
                   v.name AS venue_name, v.address AS venue_address, v.court AS venue_court,
                   (SELECT s.duration_minutes FROM group_regular_slots s
                    WHERE s.group_id = g.id ORDER BY s.position, s.weekday, s.start_time LIMIT 1) AS default_duration,
                   CASE WHEN g.owner_user_id = :actor THEN 'OWNER' ELSE m.role END AS actor_role
            FROM access_groups g
            LEFT JOIN group_memberships m ON m.group_id = g.id AND m.user_id = :actor
            LEFT JOIN group_venues v ON v.group_id = g.id AND v.id = g.default_venue_id
            WHERE g.id = :groupId AND (g.owner_user_id = :actor OR m.user_id IS NOT NULL)
        """

        const val INSERT_GAME = """
            INSERT INTO games (
                id, group_id, title, local_date, local_time, zone_id, starts_at,
                duration_minutes, confirmation_deadline, venue_id, venue_name,
                venue_address, venue_court, capacity, game_fee_cents, notes,
                status, created_at, updated_at
            ) VALUES (
                :id, :groupId, :title, :localDate, :localTime, :zoneId, :startsAt,
                :durationMinutes, :confirmationDeadline, :venueId, :venueName,
                :venueAddress, :venueCourt, :capacity, :gameFeeCents, :notes,
                :status, now(), now()
            )
        """

        const val UPDATE_GAME = """
            UPDATE games SET
                title = :title, local_date = :localDate, local_time = :localTime,
                zone_id = :zoneId, starts_at = :startsAt, duration_minutes = :durationMinutes,
                confirmation_deadline = :confirmationDeadline, venue_id = :venueId,
                venue_name = :venueName, venue_address = :venueAddress, venue_court = :venueCourt,
                capacity = :capacity, game_fee_cents = :gameFeeCents, notes = :notes,
                status = :status, version = version + 1, updated_at = now()
            WHERE id = :id AND group_id = :groupId AND version = :expectedVersion
        """

        const val SELECT_GAME = """
            SELECT g.id, g.group_id, g.title, g.local_date, g.local_time, g.zone_id,
                   g.starts_at, g.duration_minutes, g.confirmation_deadline, g.venue_id,
                   g.venue_name, g.venue_address, g.venue_court, g.capacity,
                   g.game_fee_cents, g.notes, g.status, g.version
            FROM games g
        """
    }
}
