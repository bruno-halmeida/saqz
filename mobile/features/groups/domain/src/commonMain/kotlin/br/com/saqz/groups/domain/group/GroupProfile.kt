package br.com.saqz.groups.domain.group

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import kotlin.jvm.JvmInline

enum class GroupRole { OWNER, ADMIN, ATHLETE }
enum class GroupModality { COURT_VOLLEYBALL, BEACH_VOLLEYBALL, FOOTVOLLEY }
enum class GroupComposition { WOMEN, MEN, MIXED }
enum class GroupLevel { BEGINNER, INTERMEDIATE, ADVANCED, MIXED_LEVELS, CUSTOM }
enum class GroupPlayStyle { SIX_ZERO, FOUR_TWO, FIVE_ONE, CUSTOM }
enum class GroupProfileStatus { COMPLETE, INCOMPLETE }
enum class GroupPrivacy { PRIVATE }
enum class GroupCurrency { BRL }
enum class GroupWeekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@JvmInline value class GroupVersionToken(val value: String)
@JvmInline value class GroupTimeZone(val id: String)

data class GroupVenue(
    val id: String?,
    val name: String,
    val address: String,
    val court: String?,
)

data class GroupRegularSlot(
    val id: String?,
    val weekday: GroupWeekday,
    val startTime: String,
    val durationMinutes: Int,
)

data class GroupProfile(
    val modality: GroupModality?,
    val composition: GroupComposition?,
    val description: String?,
    val city: String?,
    val level: GroupLevel?,
    val customLevel: String?,
    val playStyle: GroupPlayStyle?,
    val customPlayStyle: String?,
    val defaultVenue: GroupVenue?,
    val regularSlots: List<GroupRegularSlot>,
    val defaultCapacity: Int?,
    val defaultConfirmationLeadMinutes: Int?,
)

data class GroupFinanceDefaults(
    val defaultGameFeeCents: Long?,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
)

data class Group(
    val id: GroupId,
    val name: String,
    val timeZone: GroupTimeZone,
    val version: Long,
    val role: GroupRole,
    val profileStatus: GroupProfileStatus,
    val privacy: GroupPrivacy,
    val currency: GroupCurrency,
    val profile: GroupProfile?,
    val financeDefaults: GroupFinanceDefaults?,
)

data class VersionedGroup(val group: Group, val versionToken: GroupVersionToken)

data class GroupSetupForm(
    val name: String = "",
    val modality: GroupModality? = null,
    val composition: GroupComposition? = null,
    val description: String? = null,
    val city: String? = null,
    val level: GroupLevel? = null,
    val customLevel: String? = null,
    val playStyle: GroupPlayStyle? = null,
    val customPlayStyle: String? = null,
    val defaultVenue: GroupVenue? = null,
    val regularSlots: List<GroupRegularSlot> = emptyList(),
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
        customPlayStyle = if (modality == GroupModality.COURT_VOLLEYBALL && playStyle == GroupPlayStyle.CUSTOM) {
            customPlayStyle.trimmedOrNull()
        } else null,
        defaultVenue = defaultVenue?.copy(
            name = defaultVenue.name.trim(),
            address = defaultVenue.address.trim(),
            court = defaultVenue.court.trimmedOrNull(),
        ),
    )
}

data class CreateGroupCommand(
    val commandKey: String,
    val name: String,
    val timeZone: GroupTimeZone,
)

data class UpdateGroupSettingsCommand(
    val groupId: GroupId,
    val versionToken: GroupVersionToken,
    val name: String,
    val timeZone: GroupTimeZone,
)

data class CreateGroupProfileCommand(
    val commandKey: String,
    val timeZone: GroupTimeZone,
    val form: GroupSetupForm,
)

data class UpdateGroupProfileCommand(
    val groupId: GroupId,
    val versionToken: GroupVersionToken,
    val form: GroupSetupForm,
)

sealed interface GroupProfileError : SaqzError {
    data class Validation(val details: ValidationDetails) : GroupProfileError
    data class Conflict(val currentVersion: GroupVersionToken? = null) : GroupProfileError
    data class DataFailure(val error: DataError) : GroupProfileError
}

interface GroupGateway {
    suspend fun create(command: CreateGroupCommand): SaqzResult<Group, GroupProfileError>
    suspend fun read(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError>
    suspend fun update(command: UpdateGroupSettingsCommand): SaqzResult<VersionedGroup, GroupProfileError>
}

interface GroupProfileGateway {
    suspend fun createProfile(command: CreateGroupProfileCommand): SaqzResult<Group, GroupProfileError>
    suspend fun readProfile(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError>
    suspend fun updateProfile(command: UpdateGroupProfileCommand): SaqzResult<VersionedGroup, GroupProfileError>
}

private fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
