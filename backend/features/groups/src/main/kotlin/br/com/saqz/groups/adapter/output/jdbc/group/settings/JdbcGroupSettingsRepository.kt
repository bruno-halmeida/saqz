package br.com.saqz.groups.adapter.output.jdbc.group.settings

import br.com.saqz.groups.application.settings.GroupSettingsRepository
import br.com.saqz.groups.application.settings.SettingsWriteResult
import br.com.saqz.groups.application.settings.StoredGroupSettings
import br.com.saqz.groups.application.settings.UpdateGroupSettingsCommand
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.IanaTimeZone
import java.sql.ResultSet
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupSettingsRepository(
    dataSource: DataSource,
) : GroupSettingsRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun update(command: UpdateGroupSettingsCommand): SettingsWriteResult {
        if (command.profile != null) return updateProfile(command)
        return updateThinSettings(command)
    }

    private fun updateThinSettings(command: UpdateGroupSettingsCommand): SettingsWriteResult {
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
            RETURNING id, name, time_zone, version, profile_status
            """.trimIndent(),
        )
            .param("name", command.name.value)
            .param("timeZone", command.timeZone.value)
            .param("groupId", command.groupId)
            .param("expectedVersion", command.expectedVersion)
            .query { result, _ ->
                result.toStoredSettings()
            }
            .optional()
            .orElse(null)
        return if (updated == null) {
            SettingsWriteResult.VersionConflict
        } else {
            SettingsWriteResult.Updated(updated)
        }
    }

    private fun updateProfile(command: UpdateGroupSettingsCommand): SettingsWriteResult {
        val profile = requireNotNull(command.profile)
        val updated = jdbc.sql(
            """
            UPDATE access_groups
            SET
                name = :name,
                profile_status = 'COMPLETE',
                modality = :modality,
                composition = :composition,
                description = :description,
                city = :city,
                level = :level,
                custom_level = :customLevel,
                play_style = :playStyle,
                custom_play_style = :customPlayStyle,
                default_capacity = :defaultCapacity,
                default_confirmation_lead_minutes = :defaultConfirmationLeadMinutes,
                default_game_fee_cents = :defaultGameFeeCents,
                monthly_fee_cents = :monthlyFeeCents,
                monthly_due_day = :monthlyDueDay,
                default_venue_id = null,
                version = version + 1,
                updated_at = now()
            WHERE id = :groupId
                AND version = :expectedVersion
            RETURNING id, name, time_zone, version, profile_status
            """.trimIndent(),
        )
            .param("name", profile.name)
            .param("modality", profile.modality.name)
            .param("composition", profile.composition.name)
            .param("description", profile.description)
            .param("city", profile.city)
            .param("level", profile.level?.name)
            .param("customLevel", profile.customLevel)
            .param("playStyle", profile.playStyle?.name)
            .param("customPlayStyle", profile.customPlayStyle)
            .param("defaultCapacity", profile.defaultCapacity)
            .param("defaultConfirmationLeadMinutes", profile.defaultConfirmationLeadMinutes)
            .param("defaultGameFeeCents", profile.defaultGameFeeCents)
            .param("monthlyFeeCents", profile.monthlyFeeCents)
            .param("monthlyDueDay", profile.monthlyDueDay)
            .param("groupId", command.groupId)
            .param("expectedVersion", command.expectedVersion)
            .query { result, _ -> result.toStoredSettings() }
            .optional()
            .orElse(null)
            ?: return SettingsWriteResult.VersionConflict

        jdbc.sql("DELETE FROM group_regular_slots WHERE group_id = :groupId")
            .param("groupId", command.groupId)
            .update()
        jdbc.sql("DELETE FROM group_venues WHERE group_id = :groupId")
            .param("groupId", command.groupId)
            .update()

        val defaultVenueId = profile.defaultVenue?.let { venue ->
            val venueId = command.defaultVenueId ?: UUID.randomUUID()
            jdbc.sql(
                """
                INSERT INTO group_venues (
                    id, group_id, name, address, court, version, created_at, updated_at
                ) VALUES (
                    :id, :groupId, :name, :address, :court, 1, now(), now()
                )
                """.trimIndent(),
            )
                .param("id", venueId)
                .param("groupId", command.groupId)
                .param("name", venue.name)
                .param("address", venue.address)
                .param("court", venue.court)
                .update()
            venueId
        }
        if (defaultVenueId != null) {
            jdbc.sql("UPDATE access_groups SET default_venue_id = :venueId WHERE id = :groupId")
                .param("venueId", defaultVenueId)
                .param("groupId", command.groupId)
                .update()
        }

        profile.regularSlots.forEachIndexed { index, slot ->
            jdbc.sql(
                """
                INSERT INTO group_regular_slots (
                    id, group_id, venue_id, weekday, start_time, duration_minutes,
                    position, version, created_at, updated_at
                ) VALUES (
                    :id, :groupId, null, :weekday, :startTime, :durationMinutes,
                    :position, 1, now(), now()
                )
                """.trimIndent(),
            )
                .param("id", command.regularSlotIds.getOrNull(index) ?: UUID.randomUUID())
                .param("groupId", command.groupId)
                .param("weekday", slot.weekday.value)
                .param("startTime", slot.startTime)
                .param("durationMinutes", slot.durationMinutes)
                .param("position", index)
                .update()
        }

        return SettingsWriteResult.Updated(updated)
    }

    private fun ResultSet.toStoredSettings() = StoredGroupSettings(
        id = getObject("id", UUID::class.java),
        name = AccessName.from(getString("name")),
        timeZone = IanaTimeZone.from(getString("time_zone")),
        version = getLong("version"),
        profileStatus = GroupProfileStatus.valueOf(getString("profile_status")),
    )
}
