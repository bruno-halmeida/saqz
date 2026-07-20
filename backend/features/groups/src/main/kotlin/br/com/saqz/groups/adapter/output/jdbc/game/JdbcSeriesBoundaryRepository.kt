package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.application.game.series.FutureBoundaryCommand
import br.com.saqz.groups.application.game.series.OnlyThisBoundaryCommand
import br.com.saqz.groups.application.game.series.SeriesBoundaryAction
import br.com.saqz.groups.application.game.series.SeriesBoundaryRepository
import br.com.saqz.groups.application.game.series.SeriesBoundaryResult
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import javax.sql.DataSource

fun interface SeriesBoundaryFailureInjector { fun afterRevisionClosed() }

class JdbcSeriesBoundaryRepository(
    private val dataSource: DataSource,
    private val failureInjector: SeriesBoundaryFailureInjector = SeriesBoundaryFailureInjector {},
) : SeriesBoundaryRepository {
    override fun applyOnlyThis(command: OnlyThisBoundaryCommand): SeriesBoundaryResult = transaction { connection ->
        val row = connection.prepareStatement(LOCK_GAME).use { statement ->
            statement.setObject(1, command.groupId)
            statement.setObject(2, command.gameId)
            statement.executeQuery().use { result ->
                if (!result.next()) return@transaction SeriesBoundaryResult.NotFound
                LockedGame(result.getLong("version"), result.getObject("local_date", java.time.LocalDate::class.java), result.getString("status"), result.getBoolean("detached_from_series"))
            }
        }
        if (row.detached && row.version == command.expectedVersion) return@transaction SeriesBoundaryResult.Replay
        if (row.version != command.expectedVersion) return@transaction SeriesBoundaryResult.VersionConflict
        if (row.date < command.today || row.status == "COMPLETED") return@transaction SeriesBoundaryResult.InvalidBoundary
        val updated = connection.prepareStatement(if (command.action == SeriesBoundaryAction.CANCEL) CANCEL_ONE else EDIT_ONE).use { statement ->
            if (command.action == SeriesBoundaryAction.EDIT) statement.bindSnapshot(requireNotNull(command.replacement), 1)
            val offset = if (command.action == SeriesBoundaryAction.EDIT) 16 else 1
            statement.setObject(offset, command.groupId)
            statement.setObject(offset + 1, command.gameId)
            statement.setLong(offset + 2, command.expectedVersion)
            statement.executeUpdate()
        }
        if (updated == 1) SeriesBoundaryResult.Applied else SeriesBoundaryResult.VersionConflict
    }

    override fun applyThisAndFuture(command: FutureBoundaryCommand): SeriesBoundaryResult = transaction { connection ->
        if (successorExists(connection, command)) return@transaction SeriesBoundaryResult.Replay
        val current = lockRevision(connection, command) ?: return@transaction SeriesBoundaryResult.NotFound
        if (current.version != command.expectedVersion) return@transaction SeriesBoundaryResult.VersionConflict
        if (command.boundary < current.start || current.end?.let { command.boundary > it } == true) {
            return@transaction SeriesBoundaryResult.InvalidBoundary
        }
        insertSuccessor(connection, command)
        command.successorRule.slots.forEach { insertSlot(connection, command, it) }
        connection.prepareStatement(CLOSE_REVISION).use { statement ->
            statement.setObject(1, command.boundary.minusDays(1))
            statement.setObject(2, command.currentRevisionId)
            statement.setLong(3, command.expectedVersion)
            check(statement.executeUpdate() == 1) { "series revision changed while locked" }
        }
        failureInjector.afterRevisionClosed()
        if (command.action == SeriesBoundaryAction.CANCEL) {
            moveCancelledFuture(connection, command)
        } else {
            regenerateFuture(connection, command)
        }
        SeriesBoundaryResult.Applied
    }

    private fun regenerateFuture(connection: Connection, command: FutureBoundaryCommand) {
        val identities = command.occurrences.map { it.occurrence.localDate to it.occurrence.slot.slotKey }.toSet()
        command.occurrences.forEach { value ->
            val occurrence = value.occurrence
            val updated = connection.prepareStatement(REGENERATE).use { statement ->
                statement.bindRegenerated(value)
                statement.setObject(16, command.groupId)
                statement.setObject(17, command.successorRule.seriesId)
                statement.setObject(18, occurrence.localDate)
                statement.setObject(19, occurrence.slot.slotKey)
                statement.executeUpdate()
            }
            if (updated == 0 && !occurrenceExists(connection, command, occurrence.localDate, occurrence.slot.slotKey)) {
                insertOccurrence(connection, value)
            }
        }
        connection.prepareStatement(SELECT_FUTURE_IDENTITIES).use { statement ->
            statement.setObject(1, command.groupId)
            statement.setObject(2, command.successorRule.seriesId)
            statement.setObject(3, command.boundary)
            statement.executeQuery().use { result ->
                val removed = mutableListOf<Pair<java.time.LocalDate, java.util.UUID>>()
                while (result.next()) {
                    val identity = result.getObject(1, java.time.LocalDate::class.java) to result.getObject(2, java.util.UUID::class.java)
                    if (identity !in identities) removed += identity
                }
                removed.forEach { cancelIdentity(connection, command, it) }
            }
        }
    }

    private fun moveCancelledFuture(connection: Connection, command: FutureBoundaryCommand) {
        connection.prepareStatement(CANCEL_FUTURE).use { statement ->
            statement.setObject(1, command.successorRule.revisionId)
            statement.setObject(2, command.groupId)
            statement.setObject(3, command.successorRule.seriesId)
            statement.setObject(4, command.boundary)
            statement.executeUpdate()
        }
    }

    private fun cancelIdentity(connection: Connection, command: FutureBoundaryCommand, identity: Pair<java.time.LocalDate, java.util.UUID>) {
        connection.prepareStatement(CANCEL_IDENTITY).use { statement ->
            statement.setObject(1, command.groupId)
            statement.setObject(2, command.successorRule.seriesId)
            statement.setObject(3, identity.first)
            statement.setObject(4, identity.second)
            statement.executeUpdate()
        }
    }

    private fun insertOccurrence(connection: Connection, value: MaterializedGameOccurrence) {
        connection.prepareStatement(INSERT_OCCURRENCE).use { statement -> statement.bindInserted(value); statement.executeUpdate() }
    }

    private fun occurrenceExists(
        connection: Connection,
        command: FutureBoundaryCommand,
        date: java.time.LocalDate,
        slotKey: java.util.UUID,
    ): Boolean = connection.prepareStatement(OCCURRENCE_EXISTS).use { statement ->
        statement.setObject(1, command.groupId)
        statement.setObject(2, command.successorRule.seriesId)
        statement.setObject(3, date)
        statement.setObject(4, slotKey)
        statement.executeQuery().next()
    }

    private fun successorExists(connection: Connection, command: FutureBoundaryCommand): Boolean = connection.prepareStatement(
        "SELECT 1 FROM game_series WHERE group_id = ? AND id = ?",
    ).use { statement -> statement.setObject(1, command.groupId); statement.setObject(2, command.successorRule.revisionId); statement.executeQuery().next() }

    private fun lockRevision(connection: Connection, command: FutureBoundaryCommand): LockedRevision? = connection.prepareStatement(LOCK_REVISION).use { statement ->
        statement.setObject(1, command.groupId); statement.setObject(2, command.currentRevisionId)
        statement.executeQuery().use { result -> if (result.next()) LockedRevision(result.getLong("version"), result.getObject("local_start_date", java.time.LocalDate::class.java), result.getObject("local_end_date", java.time.LocalDate::class.java)) else null }
    }

    private fun insertSuccessor(connection: Connection, command: FutureBoundaryCommand) {
        val rule = command.successorRule
        connection.prepareStatement(INSERT_SUCCESSOR).use { statement ->
            statement.setObject(1, rule.revisionId); statement.setObject(2, rule.seriesId); statement.setObject(3, rule.groupId)
            statement.setObject(4, command.currentRevisionId); statement.setInt(5, command.revisionNumber); statement.setString(6, rule.zoneId)
            statement.setObject(7, rule.localStartDate); statement.setObject(8, rule.localEndDate)
            statement.setObject(9, if (command.action == SeriesBoundaryAction.CANCEL) command.boundary.minusDays(1) else rule.activeThroughDate)
            statement.executeUpdate()
        }
    }

    private fun insertSlot(connection: Connection, command: FutureBoundaryCommand, slot: WeeklySlotRule) {
        connection.prepareStatement(INSERT_SLOT).use { statement ->
            statement.setObject(1, command.successorRule.revisionId); statement.setObject(2, command.groupId); statement.setObject(3, slot.slotKey)
            statement.setString(4, slot.title); statement.setInt(5, slot.weekday.value); statement.setObject(6, slot.localTime); statement.setInt(7, slot.durationMinutes)
            statement.setObject(8, slot.venue.venueId); statement.setString(9, slot.venue.name); statement.setString(10, slot.venue.address); statement.setString(11, slot.venue.court)
            statement.setInt(12, slot.capacity); statement.setInt(13, slot.confirmationLeadMinutes); statement.setObject(14, slot.gameFeeCents)
            statement.executeUpdate()
        }
    }

    private fun PreparedStatement.bindSnapshot(snapshot: GameSnapshot, start: Int) {
        setString(start, snapshot.title); setObject(start + 1, snapshot.localDate); setObject(start + 2, snapshot.localTime); setString(start + 3, snapshot.zoneId.value)
        setTimestamp(start + 4, Timestamp.from(snapshot.startsAt)); setInt(start + 5, snapshot.durationMinutes); setTimestamp(start + 6, Timestamp.from(snapshot.confirmationDeadline))
        setObject(start + 7, snapshot.venue.venueId); setString(start + 8, snapshot.venue.name); setString(start + 9, snapshot.venue.address); setString(start + 10, snapshot.venue.court)
        setInt(start + 11, snapshot.capacity); setObject(start + 12, snapshot.gameFeeCents, Types.BIGINT); setString(start + 13, snapshot.notes); setBoolean(start + 14, true)
    }

    private fun PreparedStatement.bindRegenerated(value: MaterializedGameOccurrence) {
        val occurrence = value.occurrence; val slot = occurrence.slot
        setObject(1, occurrence.revisionId); setString(2, slot.title); setObject(3, occurrence.localDate); setObject(4, occurrence.localTime)
        setString(5, occurrence.zoneId.value); setTimestamp(6, Timestamp.from(occurrence.startsAt)); setInt(7, slot.durationMinutes)
        setTimestamp(8, Timestamp.from(occurrence.confirmationDeadline)); setObject(9, slot.venue.venueId); setString(10, slot.venue.name)
        setString(11, slot.venue.address); setString(12, slot.venue.court); setInt(13, slot.capacity); setObject(14, slot.gameFeeCents, Types.BIGINT)
        setTimestamp(15, Timestamp.from(value.createdAt))
    }

    private fun PreparedStatement.bindInserted(value: MaterializedGameOccurrence) {
        val occurrence = value.occurrence; val slot = occurrence.slot
        setObject(1, value.id); setObject(2, occurrence.groupId); setObject(3, occurrence.seriesId); setObject(4, occurrence.revisionId); setObject(5, slot.slotKey)
        setString(6, slot.title); setObject(7, occurrence.localDate); setObject(8, occurrence.localTime); setString(9, occurrence.zoneId.value)
        setTimestamp(10, Timestamp.from(occurrence.startsAt)); setInt(11, slot.durationMinutes); setTimestamp(12, Timestamp.from(occurrence.confirmationDeadline))
        setObject(13, slot.venue.venueId); setString(14, slot.venue.name); setString(15, slot.venue.address); setString(16, slot.venue.court)
        setInt(17, slot.capacity); setObject(18, slot.gameFeeCents, Types.BIGINT); setTimestamp(19, Timestamp.from(value.createdAt)); setTimestamp(20, Timestamp.from(value.createdAt))
    }

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try { block(connection).also { connection.commit() } } catch (failure: Exception) { connection.rollback(); throw failure }
    }

    private data class LockedGame(val version: Long, val date: java.time.LocalDate, val status: String, val detached: Boolean)
    private data class LockedRevision(val version: Long, val start: java.time.LocalDate, val end: java.time.LocalDate?)

    private companion object {
        const val LOCK_GAME = "SELECT version, local_date, status, detached_from_series FROM games WHERE group_id = ? AND id = ? AND series_id IS NOT NULL FOR UPDATE"
        const val LOCK_REVISION = "SELECT version, local_start_date, local_end_date FROM game_series WHERE group_id = ? AND id = ? FOR UPDATE"
        const val EDIT_ONE = """UPDATE games SET title=?, local_date=?, local_time=?, zone_id=?, starts_at=?, duration_minutes=?, confirmation_deadline=?, venue_id=?, venue_name=?, venue_address=?, venue_court=?, capacity=?, game_fee_cents=?, notes=?, detached_from_series=?, version=version+1, updated_at=now() WHERE group_id=? AND id=? AND version=?"""
        const val CANCEL_ONE = "UPDATE games SET status='CANCELLED', detached_from_series=true, version=version+1, updated_at=now() WHERE group_id=? AND id=? AND version=?"
        const val INSERT_SUCCESSOR = """INSERT INTO game_series (id,lineage_id,group_id,previous_revision_id,revision_number,zone_id,local_start_date,local_end_date,active_through_date,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,now(),now())"""
        const val INSERT_SLOT = """INSERT INTO game_series_slots (series_revision_id,group_id,slot_key,title,weekday,local_time,duration_minutes,venue_id,venue_name,venue_address,venue_court,capacity,confirmation_lead_minutes,game_fee_cents,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())"""
        const val CLOSE_REVISION = "UPDATE game_series SET active_through_date=?, version=version+1, updated_at=now() WHERE id=? AND version=?"
        const val REGENERATE = """UPDATE games SET series_revision_id=?, title=?, local_date=?, local_time=?, zone_id=?, starts_at=?, duration_minutes=?, confirmation_deadline=?, venue_id=?, venue_name=?, venue_address=?, venue_court=?, capacity=?, game_fee_cents=?, detached_from_series=false, version=version+1, updated_at=? WHERE group_id=? AND series_id=? AND local_date=? AND slot_key=? AND status <> 'COMPLETED'"""
        const val INSERT_OCCURRENCE = """INSERT INTO games (id,group_id,series_id,series_revision_id,slot_key,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_id,venue_name,venue_address,venue_court,capacity,game_fee_cents,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?)"""
        const val OCCURRENCE_EXISTS = "SELECT 1 FROM games WHERE group_id=? AND series_id=? AND local_date=? AND slot_key=?"
        const val SELECT_FUTURE_IDENTITIES = "SELECT local_date,slot_key FROM games WHERE group_id=? AND series_id=? AND local_date>=? AND status<>'COMPLETED'"
        const val CANCEL_IDENTITY = "UPDATE games SET status='CANCELLED', version=version+1, updated_at=now() WHERE group_id=? AND series_id=? AND local_date=? AND slot_key=? AND status<>'COMPLETED'"
        const val CANCEL_FUTURE = "UPDATE games SET series_revision_id=?, status='CANCELLED', version=version+1, updated_at=now() WHERE group_id=? AND series_id=? AND local_date>=? AND status<>'COMPLETED'"
    }
}
