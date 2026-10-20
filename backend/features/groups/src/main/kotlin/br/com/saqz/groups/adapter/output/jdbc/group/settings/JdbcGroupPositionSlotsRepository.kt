package br.com.saqz.groups.adapter.output.jdbc.group.settings

import br.com.saqz.groups.application.settings.GroupPositionSlotsRepository
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.GameSystem
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupPositionSlotsRepository(dataSource: DataSource) : GroupPositionSlotsRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun read(groupId: UUID): List<Pair<AthletePosition, Int>> =
        jdbc.sql("SELECT position, slots FROM group_position_slots WHERE group_id = :id")
            .param("id", groupId)
            .query { rs, _ -> AthletePosition.valueOf(rs.getString("position")) to rs.getInt("slots") }
            .list().sortedBy { it.first.ordinal }

    override fun gameSystem(groupId: UUID): GameSystem? =
        jdbc.sql("SELECT game_system FROM access_groups WHERE id = :id AND deleted_at IS NULL")
            .param("id", groupId)
            .query { rs, _ -> rs.getString("game_system")?.let(GameSystem::valueOf) }
            .optional().orElse(null)

    override fun replace(groupId: UUID, expectedVersion: Long, system: GameSystem?, slots: List<Pair<AthletePosition, Int>>): Boolean {
        val total = slots.sumOf { it.second }
        val changed = jdbc.sql(
            """
            UPDATE access_groups
            SET game_system = :system,
                default_capacity = CASE WHEN :total > 0 THEN :total ELSE default_capacity END,
                version = version + 1, updated_at = now()
            WHERE id = :id AND version = :version AND deleted_at IS NULL
            """.trimIndent(),
        ).param("id", groupId).param("version", expectedVersion)
            .param("system", system?.name, Types.VARCHAR).param("total", total).update()
        if (changed == 0) return false
        jdbc.sql("DELETE FROM group_position_slots WHERE group_id = :id").param("id", groupId).update()
        slots.forEach { (position, count) ->
            jdbc.sql("INSERT INTO group_position_slots (group_id, position, slots) VALUES (:id, :position, :slots)")
                .param("id", groupId).param("position", position.name, Types.OTHER).param("slots", count).update()
        }
        return true
    }
}
