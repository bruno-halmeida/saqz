package br.com.saqz.groups.adapter.output.jdbc.group.read

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.application.read.GroupFinanceDefaultsReadModel
import br.com.saqz.groups.application.read.GroupProfileReadModel
import br.com.saqz.groups.application.read.GroupRegularSlotReadModel
import br.com.saqz.groups.application.read.GroupVenueReadModel
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.DayOfWeek
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupReadRepository(
    dataSource: DataSource,
) : GroupReadRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(key: GroupReadKey): GroupReadSnapshot? {
        val rows = jdbc.sql(
        """
        SELECT
            groups.id,
            groups.name,
            groups.time_zone,
            groups.version,
            CASE
                WHEN groups.modality IS NULL OR groups.composition IS NULL THEN 'INCOMPLETE'
                ELSE groups.profile_status
            END AS profile_status,
            groups.modality,
            groups.composition,
            groups.description,
            groups.city,
            groups.level,
            groups.custom_level,
            groups.play_style,
            groups.custom_play_style,
            groups.default_capacity,
            groups.default_confirmation_lead_minutes,
            CASE
                WHEN groups.owner_user_id = :actorUserId OR memberships.role = 'ADMIN'
                    THEN groups.default_game_fee_cents
                ELSE NULL
            END AS default_game_fee_cents,
            CASE
                WHEN groups.owner_user_id = :actorUserId OR memberships.role = 'ADMIN'
                    THEN groups.monthly_fee_cents
                ELSE NULL
            END AS monthly_fee_cents,
            CASE
                WHEN groups.owner_user_id = :actorUserId OR memberships.role = 'ADMIN'
                    THEN groups.monthly_due_day
                ELSE NULL
            END AS monthly_due_day,
            default_venue.id AS default_venue_id,
            default_venue.name AS default_venue_name,
            default_venue.address AS default_venue_address,
            default_venue.court AS default_venue_court,
            slots.id AS slot_id,
            slots.weekday AS slot_weekday,
            slots.start_time AS slot_start_time,
            slots.duration_minutes AS slot_duration_minutes,
            CASE
                WHEN groups.owner_user_id = :actorUserId THEN 'OWNER'
                ELSE memberships.role
            END AS resolved_role
        FROM access_groups groups
        LEFT JOIN group_memberships memberships
            ON memberships.group_id = groups.id
            AND memberships.user_id = :actorUserId
        LEFT JOIN group_venues default_venue
            ON default_venue.group_id = groups.id
            AND default_venue.id = groups.default_venue_id
        LEFT JOIN group_regular_slots slots
            ON slots.group_id = groups.id
        WHERE groups.id = :groupId
        ORDER BY slots.position, slots.weekday, slots.start_time
        """.trimIndent(),
        )
            .param("actorUserId", key.actorUserId)
            .param("groupId", key.groupId)
            .query { result, _ -> result.toRow() }
            .list()
        if (rows.isEmpty()) return null

        val first = rows.first()
        return GroupReadSnapshot(
            id = first.id,
            name = first.name,
            timeZone = first.timeZone,
            role = first.role,
            version = first.version,
            profileStatus = first.profileStatus,
            profile = GroupProfileReadModel(
                modality = first.modality,
                composition = first.composition,
                description = first.description,
                city = first.city,
                level = first.level,
                customLevel = first.customLevel,
                playStyle = first.playStyle,
                customPlayStyle = first.customPlayStyle,
                defaultVenue = first.defaultVenue,
                regularSlots = rows.mapNotNull { it.slot },
                defaultCapacity = first.defaultCapacity,
                defaultConfirmationLeadMinutes = first.defaultConfirmationLeadMinutes,
            ),
            financeDefaults = first.financeDefaults,
        )
    }

    private fun ResultSet.toRow() = Row(
        id = getObject("id", UUID::class.java),
        name = AccessName.from(getString("name")),
        timeZone = IanaTimeZone.from(getString("time_zone")),
        role = getString("resolved_role")?.let(GroupRole::valueOf),
        version = getLong("version"),
        profileStatus = GroupProfileStatus.valueOf(getString("profile_status")),
        modality = getString("modality")?.let(GroupModality::valueOf),
        composition = getString("composition")?.let(GroupComposition::valueOf),
        description = getString("description"),
        city = getString("city"),
        level = getString("level")?.let(GroupLevel::valueOf),
        customLevel = getString("custom_level"),
        playStyle = getString("play_style")?.let(CourtPlayStyle::valueOf),
        customPlayStyle = getString("custom_play_style"),
        defaultVenue = getObject("default_venue_id", UUID::class.java)?.let {
            GroupVenueReadModel(
                id = it,
                name = getString("default_venue_name"),
                address = getString("default_venue_address"),
                court = getString("default_venue_court"),
            )
        },
        slot = getObject("slot_id", UUID::class.java)?.let {
            GroupRegularSlotReadModel(
                id = it,
                weekday = DayOfWeek.of(getInt("slot_weekday")),
                startTime = getTime("slot_start_time").toLocalTime(),
                durationMinutes = getInt("slot_duration_minutes"),
            )
        },
        defaultCapacity = getNullableInt("default_capacity"),
        defaultConfirmationLeadMinutes = getNullableInt("default_confirmation_lead_minutes"),
        financeDefaults = GroupFinanceDefaultsReadModel(
            defaultGameFeeCents = getNullableLong("default_game_fee_cents"),
            monthlyFeeCents = getNullableLong("monthly_fee_cents"),
            monthlyDueDay = getNullableInt("monthly_due_day"),
        ),
    )

    private fun ResultSet.getNullableInt(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }

    private data class Row(
        val id: UUID,
        val name: AccessName,
        val timeZone: IanaTimeZone,
        val role: GroupRole?,
        val version: Long,
        val profileStatus: GroupProfileStatus,
        val modality: GroupModality?,
        val composition: GroupComposition?,
        val description: String?,
        val city: String?,
        val level: GroupLevel?,
        val customLevel: String?,
        val playStyle: CourtPlayStyle?,
        val customPlayStyle: String?,
        val defaultVenue: GroupVenueReadModel?,
        val slot: GroupRegularSlotReadModel?,
        val defaultCapacity: Int?,
        val defaultConfirmationLeadMinutes: Int?,
        val financeDefaults: GroupFinanceDefaultsReadModel,
    )
}
