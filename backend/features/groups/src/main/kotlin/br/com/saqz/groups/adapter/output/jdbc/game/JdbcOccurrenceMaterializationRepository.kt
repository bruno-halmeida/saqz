package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.application.game.recurrence.OccurrenceMaterializationRepository
import java.sql.PreparedStatement
import java.sql.Statement
import java.sql.Timestamp
import javax.sql.DataSource

class JdbcOccurrenceMaterializationRepository(private val dataSource: DataSource) : OccurrenceMaterializationRepository {
    override fun insertIfAbsent(occurrences: List<MaterializedGameOccurrence>): Int {
        if (occurrences.isEmpty()) return 0
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val inserted = connection.prepareStatement(INSERT).use { statement ->
                    occurrences.forEach { occurrence -> statement.bind(occurrence).addBatch() }
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
                throw failure
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
        setString(19, value.status.name)
        setTimestamp(20, Timestamp.from(value.createdAt))
        setTimestamp(21, Timestamp.from(value.createdAt))
    }

    private companion object {
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
