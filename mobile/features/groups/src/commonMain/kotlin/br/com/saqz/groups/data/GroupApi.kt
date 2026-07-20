package br.com.saqz.groups.data

import br.com.saqz.groups.model.GroupCreateCommand
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupUpdateCommand
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class GroupRoleDto {
    OWNER,
    ADMIN,
    ATHLETE,
}

@Serializable
enum class GroupModalityDto { COURT_VOLLEYBALL, BEACH_VOLLEYBALL, FOOTVOLLEY }

@Serializable
enum class GroupCompositionDto { WOMEN, MEN, MIXED }

@Serializable
enum class GroupLevelDto { BEGINNER, INTERMEDIATE, ADVANCED, MIXED_LEVELS, CUSTOM }

@Serializable
enum class GroupPlayStyleDto { SIX_ZERO, FOUR_TWO, FIVE_ONE, CUSTOM }

@Serializable
enum class GroupProfileStatusDto { COMPLETE, INCOMPLETE }

@Serializable
enum class GroupPrivacyDto { PRIVATE }

@Serializable
enum class GroupCurrencyDto { BRL }

@Serializable
enum class GroupWeekdayDto { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@Serializable
data class GroupVenueDto(val id: String, val name: String, val address: String, val court: String? = null)

@Serializable
data class GroupRegularSlotDto(
    val id: String,
    val weekday: GroupWeekdayDto,
    val startTime: String,
    val durationMinutes: Int,
)

@Serializable
data class GroupProfileDto(
    val modality: GroupModalityDto? = null,
    val composition: GroupCompositionDto? = null,
    val description: String? = null,
    val city: String? = null,
    val level: GroupLevelDto? = null,
    val customLevel: String? = null,
    val playStyle: GroupPlayStyleDto? = null,
    val customPlayStyle: String? = null,
    val defaultVenue: GroupVenueDto? = null,
    val regularSlots: List<GroupRegularSlotDto> = emptyList(),
    val defaultCapacity: Int? = null,
    val defaultConfirmationLeadMinutes: Int? = null,
)

@Serializable
data class GroupFinanceDefaultsDto(
    val defaultGameFeeCents: Long? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val timeZone: String,
    val version: Long,
    val role: GroupRoleDto,
    val profileStatus: GroupProfileStatusDto = GroupProfileStatusDto.COMPLETE,
    val privacy: GroupPrivacyDto = GroupPrivacyDto.PRIVATE,
    val currency: GroupCurrencyDto = GroupCurrencyDto.BRL,
    val profile: GroupProfileDto? = null,
    val financeDefaults: GroupFinanceDefaultsDto? = null,
)

data class VersionedGroupDto(
    val group: GroupDto,
    val etag: String,
)

@Serializable
private data class CreateGroupRequestDto(
    val requestId: String,
    val name: String,
    val timeZone: String,
)

@Serializable
private data class UpdateGroupSettingsRequestDto(
    val name: String,
    val timeZone: String,
)

@Serializable
private data class GroupVenueRequestDto(
    val id: String? = null,
    val name: String,
    val address: String,
    val court: String? = null,
)

@Serializable
private data class GroupRegularSlotRequestDto(
    val id: String? = null,
    val weekday: String,
    val startTime: String,
    val durationMinutes: Int,
)

@Serializable
private data class CompleteGroupRequestDto(
    val requestId: String? = null,
    val name: String,
    val modality: String,
    val composition: String,
    val description: String? = null,
    val city: String? = null,
    val level: String? = null,
    val customLevel: String? = null,
    val playStyle: String? = null,
    val customPlayStyle: String? = null,
    val defaultVenue: GroupVenueRequestDto? = null,
    val regularSlots: List<GroupRegularSlotRequestDto> = emptyList(),
    val defaultCapacity: Int? = null,
    val defaultConfirmationLeadMinutes: Int? = null,
    val defaultGameFeeCents: Long? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
    val timeZone: String? = null,
)

interface GroupGateway {
    suspend fun create(requestId: String, name: String, timeZone: String): NetworkResult<GroupDto>

    suspend fun read(groupId: String): NetworkResult<VersionedGroupDto>

    suspend fun update(groupId: String, etag: String, name: String, timeZone: String): NetworkResult<VersionedGroupDto>
}

interface GroupProfileGateway {
    suspend fun createProfile(command: GroupCreateCommand): NetworkResult<GroupDto>
    suspend fun updateProfile(command: GroupUpdateCommand): NetworkResult<VersionedGroupDto>
}

class GroupApi(
    private val network: AuthenticatedNetworkClient,
) : GroupGateway, GroupProfileGateway {
    private val json = Json { explicitNulls = false }

    override suspend fun create(requestId: String, name: String, timeZone: String): NetworkResult<GroupDto> =
        network.execute(
            HttpMethod.Post,
            "api/groups",
            GroupDto.serializer(),
            NetworkRequest(json.encodeToString(CreateGroupRequestDto(requestId, name, timeZone))),
        )

    override suspend fun read(groupId: String): NetworkResult<VersionedGroupDto> =
        network.execute(HttpMethod.Get, "api/groups/$groupId", GroupDto.serializer()).versioned()

    override suspend fun update(
        groupId: String,
        etag: String,
        name: String,
        timeZone: String,
    ): NetworkResult<VersionedGroupDto> = network.execute(
        HttpMethod.Put,
        "api/groups/$groupId/settings",
        GroupDto.serializer(),
        NetworkRequest(
            body = json.encodeToString(UpdateGroupSettingsRequestDto(name, timeZone)),
            headers = mapOf(HttpHeaders.IfMatch to etag),
        ),
    ).versioned()

    override suspend fun createProfile(command: GroupCreateCommand): NetworkResult<GroupDto> =
        network.execute(
            HttpMethod.Post,
            "api/groups",
            GroupDto.serializer(),
            NetworkRequest(
                json.encodeToString(
                    command.form.toRequest(requestId = command.commandKey, timeZone = command.timeZone.id),
                ),
            ),
        )

    override suspend fun updateProfile(command: GroupUpdateCommand): NetworkResult<VersionedGroupDto> =
        network.execute(
            HttpMethod.Put,
            "api/groups/${command.groupId}",
            GroupDto.serializer(),
            NetworkRequest(
                body = json.encodeToString(command.form.toRequest()),
                headers = mapOf(HttpHeaders.IfMatch to command.etag),
            ),
        ).versioned()

    private fun NetworkResult<GroupDto>.versioned(): NetworkResult<VersionedGroupDto> = when (this) {
        is NetworkResult.Failure -> this
        is NetworkResult.Success -> {
            val etag = metadata.header(HttpHeaders.ETag)
                ?: return NetworkResult.Failure(NetworkError.InvalidResponse)
            NetworkResult.Success(VersionedGroupDto(value, etag), metadata)
        }
    }
}

private fun GroupSetupForm.toRequest(requestId: String? = null, timeZone: String? = null): CompleteGroupRequestDto {
    val form = cleaned()
    return CompleteGroupRequestDto(
        requestId = requestId,
        name = form.name,
        modality = form.modality.name,
        composition = form.composition.name,
        description = form.description,
        city = form.city,
        level = form.level?.name,
        customLevel = form.customLevel,
        playStyle = form.playStyle?.name,
        customPlayStyle = form.customPlayStyle,
        defaultVenue = form.defaultVenue?.let { GroupVenueRequestDto(it.id, it.name, it.address, it.court) },
        regularSlots = form.regularSlots.map {
            GroupRegularSlotRequestDto(it.id, it.weekday.name, it.startTime, it.durationMinutes)
        },
        defaultCapacity = form.defaultCapacity,
        defaultConfirmationLeadMinutes = form.defaultConfirmationLeadMinutes,
        defaultGameFeeCents = form.defaultGameFeeCents,
        monthlyFeeCents = form.monthlyFeeCents,
        monthlyDueDay = form.monthlyDueDay,
        timeZone = timeZone,
    )
}
