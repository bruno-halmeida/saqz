package br.com.saqz.groups.adapter.output.jdbc.group.settings

import br.com.saqz.groups.application.settings.GroupSettingsRepository
import br.com.saqz.groups.application.settings.SettingsWriteResult
import br.com.saqz.groups.application.settings.StoredGroupSettings
import br.com.saqz.groups.application.settings.UpdateGroupSettingsCommand
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.IanaTimeZone
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupSettingsRepository(
    dataSource: DataSource,
) : GroupSettingsRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun update(command: UpdateGroupSettingsCommand): SettingsWriteResult {
        val updated = jdbc.sql(
            """
            UPDATE access_groups
            SET
                name = :name,
                time_zone = :timeZone,
                version = version + 1,
                updated_at = now()
            WHERE id = :groupId
                AND version = :expectedVersion
            RETURNING id, name, time_zone, version
            """.trimIndent(),
        )
            .param("name", command.name.value)
            .param("timeZone", command.timeZone.value)
            .param("groupId", command.groupId)
            .param("expectedVersion", command.expectedVersion)
            .query { result, _ ->
                StoredGroupSettings(
                    id = result.getObject("id", UUID::class.java),
                    name = AccessName.from(result.getString("name")),
                    timeZone = IanaTimeZone.from(result.getString("time_zone")),
                    version = result.getLong("version"),
                )
            }
            .optional()
            .orElse(null)
        return if (updated == null) {
            SettingsWriteResult.VersionConflict
        } else {
            SettingsWriteResult.Updated(updated)
        }
    }
}
