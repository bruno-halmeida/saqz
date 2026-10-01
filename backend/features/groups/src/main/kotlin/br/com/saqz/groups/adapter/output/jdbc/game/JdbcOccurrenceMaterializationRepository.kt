package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.GameScheduleConflictWriteException
import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.application.game.recurrence.OccurrenceMaterializationRepository
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import javax.sql.DataSource

class JdbcOccurrenceMaterializationRepository(private val dataSource: DataSource) : OccurrenceMaterializationRepository {
    override fun insertIfAbsent(occurrences: List<MaterializedGameOccurrence>): Int {
        if (occurrences.isEmpty()) return 0
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                // "Ausente" também vale para o horário: jogo avulso no mesmo `starts_at` (o "marcar
                // jogo" com repetição) conta como a ocorrência, em vez de derrubar o lote inteiro.
                val occupied = occupiedStartsAt(connection, occurrences)
                val inserted = connection.prepareStatement(INSERT).use { statement ->
                    occurrences.filterNot { it.occurrence.startsAt in occupied }
                        .forEach { occurrence -> statement.bind(occurrence).addBatch() }
                    statement.executeBatch().sumOf { count ->
                        when {
                            count == Statement.SUCCESS_NO_INFO -> 1
                            count > 0 -> count
                            else -> 0
                        }
                    }
                }
                connection.commit()
                return inserted
            } catch (failure: Exception) {
                connection.rollback()
                if (failure.isGameScheduleConflict()) throw GameScheduleConflictWriteException()
                throw failure
            }
        }
    }

    private fun occupiedStartsAt(connection: Connection, occurrences: List<MaterializedGameOccurrence>): Set<Instant> {
        val groups = occurrences.map { it.occurrence.groupId }.distinct()
        val starts = occurrences.map { it.occurrence.startsAt }
        return groups.flatMapTo(mutableSetOf()) { group ->
            connection.prepareStatement(OCCUPIED).use { statement ->
                statement.setObject(1, group)
                statement.setTimestamp(2, Timestamp.from(starts.min()))
                statement.setTimestamp(3, Timestamp.from(starts.max()))
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getTimestamp(1).toInstant()) }
                }
            }
        }
    }

    private fun PreparedStatement.bind(value: MaterializedGameOccurrence): PreparedStatement = apply {
        val occurrence = value.occurrence
        val slot = occurrence.slot
        setObject(1, value.id)
        setObject(2, occurrence.groupId)
        setObject(3, occurrence.seriesId)
        setObject(4, occurrence.revisionId)
        setObject(5, slot.slotKey)
        setString(6, slot.title.trim())
        setObject(7, occurrence.localDate)
        setObject(8, occurrence.localTime)
        setString(9, occurrence.zoneId.value)
        setTimestamp(10, Timestamp.from(occurrence.startsAt))
        setInt(11, slot.durationMinutes)
        setTimestamp(12, Timestamp.from(occurrence.confirmationDeadline))
        setObject(13, slot.venue.venueId)
        setString(14, slot.venue.name)
        setString(15, slot.venue.address)
        setString(16, slot.venue.court)
        setInt(17, slot.capacity)
        setObject(18, slot.gameFeeCents)
        setObject(19, value.status.name, Types.OTHER)
        setTimestamp(20, Timestamp.from(value.createdAt))
        setTimestamp(21, Timestamp.from(value.createdAt))
    }

    private companion object {
        const val OCCUPIED = """
            SELECT starts_at FROM games
            WHERE group_id = ? AND starts_at BETWEEN ? AND ? AND status IN ('DRAFT', 'PUBLISHED')
        """
        const val INSERT = """
            INSERT INTO games (
                id, group_id, series_id, series_revision_id, slot_key, title,
                local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_id, venue_name, venue_address,
                venue_court, capacity, game_fee_cents, status, created_at, updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            ON CONFLICT (series_id, local_date, slot_key) DO NOTHING
        """
    }
}
