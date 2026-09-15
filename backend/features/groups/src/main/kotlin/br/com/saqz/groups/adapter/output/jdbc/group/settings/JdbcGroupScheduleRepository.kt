package br.com.saqz.groups.adapter.output.jdbc.group.settings

import br.com.saqz.groups.application.settings.*
import br.com.saqz.groups.application.game.recurrence.ScheduleMaterializationPolicy
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.DayOfWeek
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupScheduleRepository(dataSource: DataSource) : GroupScheduleRepository, ScheduleMaterializationPolicy {
    private val jdbc = JdbcClient.create(dataSource)

    override fun isPaused(groupId: UUID): Boolean = jdbc.sql(
        "SELECT schedule_paused FROM access_groups WHERE id = :id AND deleted_at IS NULL FOR SHARE",
    ).param("id", groupId).query(Boolean::class.java).optional().orElse(true)

    override fun read(groupId: UUID): VersionedGroupSchedule? {
        // Keep the version and slots from the same snapshot while a schedule is being replaced.
        jdbc.sql("SELECT id FROM access_groups WHERE id = :id AND deleted_at IS NULL FOR SHARE")
            .param("id", groupId).query(UUID::class.java).optional().orElse(null) ?: return null
        val slots = jdbc.sql("SELECT weekday, start_time FROM group_regular_slots WHERE group_id = :id ORDER BY position")
            .param("id", groupId).query { row, _ ->
                ScheduleSlot(DayOfWeek.of(row.getInt("weekday")), row.getTime("start_time").toLocalTime())
            }.list()
        return jdbc.sql("""
            SELECT version, schedule_paused,
                COALESCE(default_confirmation_lead_minutes, 360) AS lead,
                COALESCE((SELECT duration_minutes FROM group_regular_slots
                    WHERE group_id = :id ORDER BY position LIMIT 1), default_duration_minutes, 120) AS duration
            FROM access_groups WHERE id = :id AND deleted_at IS NULL
        """.trimIndent()).param("id", groupId).query { row, _ ->
            VersionedGroupSchedule(
                GroupSchedule(slots.isNotEmpty(), slots, row.getInt("duration"), row.getInt("lead"), row.getBoolean("schedule_paused")),
                row.getLong("version"),
            )
        }.optional().orElse(null)
    }

    override fun update(groupId: UUID, expectedVersion: Long, schedule: GroupSchedule): Boolean {
        val changed = jdbc.sql("""
            UPDATE access_groups SET default_duration_minutes = :duration,
                default_confirmation_lead_minutes = :lead, schedule_paused = :paused,
                version = version + 1, updated_at = now()
            WHERE id = :id AND version = :version AND deleted_at IS NULL
        """.trimIndent()).param("id", groupId).param("version", expectedVersion)
            .param("duration", schedule.durationMinutes).param("lead", schedule.confirmationLeadMinutes)
            .param("paused", schedule.paused).update()
        if (changed == 0) return false
        jdbc.sql("DELETE FROM group_regular_slots WHERE group_id = :id").param("id", groupId).update()
        schedule.slots.forEachIndexed { index, slot ->
            jdbc.sql("""
                INSERT INTO group_regular_slots
                    (id, group_id, weekday, start_time, duration_minutes, position, version, created_at, updated_at)
                VALUES (:id, :group, :day, :time, :duration, :position, 1, now(), now())
            """.trimIndent()).param("id", UUID.randomUUID()).param("group", groupId)
                .param("day", slot.weekday.value).param("time", slot.startTime)
                .param("duration", schedule.durationMinutes).param("position", index).update()
        }
        return true
    }
}
