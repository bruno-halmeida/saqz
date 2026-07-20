package br.com.saqz.groups.model

import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable
enum class GroupModality { COURT_VOLLEYBALL, BEACH_VOLLEYBALL, FOOTVOLLEY }

@Serializable
enum class GroupComposition { WOMEN, MEN, MIXED }

@Serializable
enum class GroupLevel { BEGINNER, INTERMEDIATE, ADVANCED, MIXED_LEVELS, CUSTOM }

@Serializable
enum class GroupPlayStyle { SIX_ZERO, FOUR_TWO, FIVE_ONE, CUSTOM }

@Serializable
enum class GroupWeekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@Serializable
data class GroupVenueForm(
    val id: String? = null,
    val name: String,
    val address: String,
    val court: String? = null,
)

@Serializable
data class GroupRegularSlotForm(
    val id: String? = null,
    val weekday: GroupWeekday,
    val startTime: String,
    val durationMinutes: Int,
)

@Serializable
data class GroupSetupForm(
    val name: String,
    val modality: GroupModality,
    val composition: GroupComposition,
    val description: String? = null,
    val city: String? = null,
    val level: GroupLevel? = null,
    val customLevel: String? = null,
    val playStyle: GroupPlayStyle? = null,
    val customPlayStyle: String? = null,
    val defaultVenue: GroupVenueForm? = null,
    val regularSlots: List<GroupRegularSlotForm> = emptyList(),
    val defaultCapacity: Int? = null,
    val defaultConfirmationLeadMinutes: Int? = null,
    val defaultGameFeeCents: Long? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
) {
    fun cleaned(): GroupSetupForm = copy(
        name = name.trim(),
        description = description.trimmedOrNull(),
        city = city.trimmedOrNull(),
        customLevel = if (level == GroupLevel.CUSTOM) customLevel.trimmedOrNull() else null,
        playStyle = if (modality == GroupModality.COURT_VOLLEYBALL) playStyle else null,
        customPlayStyle = if (
            modality == GroupModality.COURT_VOLLEYBALL && playStyle == GroupPlayStyle.CUSTOM
        ) customPlayStyle.trimmedOrNull() else null,
        defaultVenue = defaultVenue?.copy(
            name = defaultVenue.name.trim(),
            address = defaultVenue.address.trim(),
            court = defaultVenue.court.trimmedOrNull(),
        ),
    )
}

class GroupTimeZone private constructor(val id: String) {
    sealed interface ParseResult {
        data class Valid(val value: GroupTimeZone) : ParseResult
        data object Invalid : ParseResult
    }

    companion object {
        fun parse(raw: String): ParseResult = runCatching { TimeZone.of(raw) }
            .fold(
                onSuccess = { ParseResult.Valid(GroupTimeZone(it.id)) },
                onFailure = { ParseResult.Invalid },
            )
    }

    override fun equals(other: Any?): Boolean = other is GroupTimeZone && id == other.id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id
}

data class GroupCreateCommand(
    val commandKey: String,
    val timeZone: GroupTimeZone,
    val form: GroupSetupForm,
)

data class GroupUpdateCommand(
    val groupId: String,
    val etag: String,
    val form: GroupSetupForm,
)

@Serializable
enum class GroupDraftResource { CREATE_GROUP, UPDATE_GROUP }

@Serializable
data class GroupDraftKey(
    val resource: GroupDraftResource,
    val groupId: String?,
)

@Serializable
data class GroupSetupDraft(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val resource: GroupDraftResource,
    val groupId: String?,
    val groupVersion: Long?,
    val etag: String?,
    val commandKey: String,
    val form: GroupSetupForm,
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
