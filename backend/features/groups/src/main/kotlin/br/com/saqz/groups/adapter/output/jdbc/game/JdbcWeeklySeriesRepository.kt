package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.application.game.series.SeriesOccurrenceView
import br.com.saqz.groups.application.game.series.WeeklySeriesRepository
import br.com.saqz.groups.application.game.series.WeeklySeriesView
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import java.sql.Connection
import java.sql.Timestamp
import java.time.DayOfWeek
import java.util.UUID
import javax.sql.DataSource

class JdbcWeeklySeriesRepository(private val dataSource: DataSource) : WeeklySeriesRepository {
    override fun role(actor: UUID, groupId: UUID): GroupRole? = dataSource.connection.use { c -> c.prepareStatement(ROLE).use { s -> s.setObject(1,actor);s.setObject(2,actor);s.setObject(3,groupId);s.setObject(4,actor);s.setObject(5,actor);s.executeQuery().use { r -> if(r.next()) GroupRole.valueOf(r.getString(1)) else null } } }

    override fun create(rule: WeeklySeriesRule, occurrences: List<MaterializedGameOccurrence>): Boolean = transaction { c ->
        val inserted = c.prepareStatement(INSERT_SERIES).use { s -> s.setObject(1,rule.revisionId);s.setObject(2,rule.seriesId);s.setObject(3,rule.groupId);s.setString(4,rule.zoneId);s.setObject(5,rule.localStartDate);s.setObject(6,rule.localEndDate);s.setObject(7,rule.activeThroughDate);s.executeUpdate()==1 }
        if (!inserted) return@transaction false
        rule.slots.forEach { slot -> c.prepareStatement(INSERT_SLOT).use { s -> s.setObject(1,rule.revisionId);s.setObject(2,rule.groupId);s.setObject(3,slot.slotKey);s.setString(4,slot.title);s.setInt(5,slot.weekday.value);s.setObject(6,slot.localTime);s.setInt(7,slot.durationMinutes);s.setObject(8,slot.venue.venueId);s.setString(9,slot.venue.name);s.setString(10,slot.venue.address);s.setString(11,slot.venue.court);s.setInt(12,slot.capacity);s.setInt(13,slot.confirmationLeadMinutes);s.setObject(14,slot.gameFeeCents);s.executeUpdate() } }
        occurrences.forEach { value -> val o=value.occurrence;val slot=o.slot;c.prepareStatement(INSERT_GAME).use { s -> s.setObject(1,value.id);s.setObject(2,o.groupId);s.setObject(3,o.seriesId);s.setObject(4,o.revisionId);s.setObject(5,slot.slotKey);s.setString(6,slot.title);s.setObject(7,o.localDate);s.setObject(8,o.localTime);s.setString(9,o.zoneId.value);s.setTimestamp(10,Timestamp.from(o.startsAt));s.setInt(11,slot.durationMinutes);s.setTimestamp(12,Timestamp.from(o.confirmationDeadline));s.setObject(13,slot.venue.venueId);s.setString(14,slot.venue.name);s.setString(15,slot.venue.address);s.setString(16,slot.venue.court);s.setInt(17,slot.capacity);s.setObject(18,slot.gameFeeCents);s.setTimestamp(19,Timestamp.from(value.createdAt));s.setTimestamp(20,Timestamp.from(value.createdAt));s.executeUpdate() } }
        true
    }

    override fun find(groupId: UUID, lineageId: UUID): WeeklySeriesView? = dataSource.connection.use { c ->
        c.prepareStatement(FIND_SERIES).use { s -> s.setObject(1,groupId);s.setObject(2,lineageId);s.executeQuery().use { r ->
            if(!r.next()) return@use null
            val revision=r.getObject("id",UUID::class.java)
            val slots=slots(c,revision)
            val rule=WeeklySeriesRule(groupId,lineageId,revision,r.getString("zone_id"),r.getObject("local_start_date",java.time.LocalDate::class.java),r.getObject("local_end_date",java.time.LocalDate::class.java),r.getObject("active_through_date",java.time.LocalDate::class.java),slots)
            WeeklySeriesView(rule,r.getInt("revision_number"),r.getLong("version"),occurrences(c,groupId,lineageId))
        } }
    }

    private fun slots(c:Connection, revision:UUID)=c.prepareStatement(FIND_SLOTS).use{s->s.setObject(1,revision);s.executeQuery().use{r->buildList{while(r.next())add(WeeklySlotRule(r.getObject("slot_key",UUID::class.java),DayOfWeek.of(r.getInt("weekday")),r.getObject("local_time",java.time.LocalTime::class.java),r.getInt("duration_minutes"),GameVenueSnapshot(r.getObject("venue_id",UUID::class.java),r.getString("venue_name"),r.getString("venue_address"),r.getString("venue_court")),r.getInt("capacity"),r.getInt("confirmation_lead_minutes"),r.getObject("game_fee_cents",java.lang.Long::class.java)?.toLong(),r.getString("title")))}}}
    private fun occurrences(c:Connection, group:UUID,lineage:UUID)=c.prepareStatement(FIND_GAMES).use{s->s.setObject(1,group);s.setObject(2,lineage);s.executeQuery().use{r->buildList{while(r.next())add(SeriesOccurrenceView(r.getObject("id",UUID::class.java),r.getObject("local_date",java.time.LocalDate::class.java),r.getObject("local_time",java.time.LocalTime::class.java),r.getTimestamp("starts_at").toInstant(),GameStatus.valueOf(r.getString("status")),r.getLong("version")))}}}
    private fun <T> transaction(block:(Connection)->T):T=dataSource.connection.use{c->c.autoCommit=false;try{block(c).also{c.commit()}}catch(e:Exception){c.rollback();throw e}}
    private companion object {
        const val ROLE="SELECT CASE WHEN g.owner_user_id=? THEN 'OWNER' ELSE m.role END FROM access_groups g LEFT JOIN group_memberships m ON m.group_id=g.id AND m.user_id=? WHERE g.id=? AND (g.owner_user_id=? OR m.user_id=?)"
        const val INSERT_SERIES="INSERT INTO game_series (id,lineage_id,group_id,revision_number,zone_id,local_start_date,local_end_date,active_through_date,created_at,updated_at) VALUES (?,?,?,1,?,?,?,?,now(),now()) ON CONFLICT (id) DO NOTHING"
        const val INSERT_SLOT="INSERT INTO game_series_slots (series_revision_id,group_id,slot_key,title,weekday,local_time,duration_minutes,venue_id,venue_name,venue_address,venue_court,capacity,confirmation_lead_minutes,game_fee_cents,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())"
        const val INSERT_GAME="INSERT INTO games (id,group_id,series_id,series_revision_id,slot_key,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_id,venue_name,venue_address,venue_court,capacity,game_fee_cents,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?)"
        const val FIND_SERIES="SELECT * FROM game_series WHERE group_id=? AND lineage_id=? ORDER BY revision_number DESC LIMIT 1"
        const val FIND_SLOTS="SELECT * FROM game_series_slots WHERE series_revision_id=? ORDER BY weekday,local_time,slot_key"
        const val FIND_GAMES="SELECT id,local_date,local_time,starts_at,status,version FROM games WHERE group_id=? AND series_id=? ORDER BY local_date,local_time,id LIMIT 256"
    }
}
