package br.com.saqz.groups.adapter.output.jdbc.group.create

import br.com.saqz.groups.application.create.CreateGroupCommand
import br.com.saqz.groups.application.create.GroupCreationRepository
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.application.create.StoredGroup
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.IanaTimeZone
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupCreationRepository(
    dataSource: DataSource,
    private val failureInjector: FailureInjector = FailureInjector.None,
) : GroupCreationRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun create(command: CreateGroupCommand): StoredGroup {
        val groupId = UUID.randomUUID()
        val inserted = insertGroup(groupId, command)
        if (inserted != null) {
            failureInjector.afterGroupInsert()
            val defaultVenueId = command.profile.defaultVenue?.let { venue ->
                val venueId = UUID.randomUUID()
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
                    .param("groupId", groupId)
                    .param("name", venue.name)
                    .param("address", venue.address)
                    .param("court", venue.court)
                    .update()
                venueId
            }
            if (defaultVenueId != null) {
                jdbc.sql(
                    """
                    UPDATE access_groups
                    SET default_venue_id = :defaultVenueId, updated_at = now()
                    WHERE id = :groupId
                    """.trimIndent(),
                )
                    .param("defaultVenueId", defaultVenueId)
                    .param("groupId", groupId)
                    .update()
            }
            command.profile.regularSlots.forEachIndexed { index, slot ->
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
                    .param("id", UUID.randomUUID())
                    .param("groupId", groupId)
                    .param("weekday", slot.weekday.value)
                    .param("startTime", slot.startTime)
                    .param("durationMinutes", slot.durationMinutes)
                    .param("position", index)
                    .update()
            }
            return loadByCreationKey(command)
        }
        return loadByCreationKey(command)
    }

    private fun insertGroup(groupId: UUID, command: CreateGroupCommand): StoredGroup? = jdbc.sql(
        """
        INSERT INTO access_groups (
            id, owner_user_id, creation_key, name, time_zone, version,
            privacy, currency, profile_status, modality, composition, description,
            city, level, custom_level, play_style, custom_play_style,
            default_capacity, default_confirmation_lead_minutes, default_game_fee_cents,
            monthly_fee_cents, monthly_due_day, created_at, updated_at
        ) VALUES (
            :id, :ownerUserId, :creationKey, :name, :timeZone, 1,
            'PRIVATE', 'BRL', 'COMPLETE', :modality, :composition, :description,
            :city, :level, :customLevel, :playStyle, :customPlayStyle,
            :defaultCapacity, :defaultConfirmationLeadMinutes, :defaultGameFeeCents,
            :monthlyFeeCents, :monthlyDueDay, now(), now()
        )
        ON CONFLICT (owner_user_id, creation_key) DO NOTHING
        RETURNING id, owner_user_id, creation_key, name, time_zone, version, profile_status
        """.trimIndent(),
    )
        .bindGroup(groupId, command)
        .query { result, _ -> result.toStoredGroup() }
        .optional()
        .orElse(null)

    private fun loadByCreationKey(command: CreateGroupCommand): StoredGroup = jdbc.sql(
        """
        SELECT id, owner_user_id, creation_key, name, time_zone, version, profile_status
        FROM access_groups
        WHERE owner_user_id = :ownerUserId AND creation_key = :creationKey
        """.trimIndent(),
    )
        .param("ownerUserId", command.ownerUserId)
        .param("creationKey", command.creationKey)
        .query { result, _ -> result.toStoredGroup() }
        .single()

    private fun JdbcClient.StatementSpec.bindGroup(
        groupId: UUID,
        command: CreateGroupCommand,
    ): JdbcClient.StatementSpec {
        val profile = command.profile
        return param("id", groupId)
            .param("ownerUserId", command.ownerUserId)
            .param("creationKey", command.creationKey)
            .param("name", profile.name)
            .param("timeZone", command.timeZone.value)
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
    }

    private fun ResultSet.toStoredGroup() = StoredGroup(
        id = getObject("id", UUID::class.java),
        ownerUserId = getObject("owner_user_id", UUID::class.java),
        creationKey = getObject("creation_key", UUID::class.java),
        name = AccessName.from(getString("name")),
        timeZone = IanaTimeZone.from(getString("time_zone")),
        version = getLong("version"),
        profileStatus = GroupProfileStatus.valueOf(getString("profile_status")),
    )

    fun interface FailureInjector {
        fun afterGroupInsert()

        object None : FailureInjector {
            override fun afterGroupInsert() = Unit
        }
    }
}
