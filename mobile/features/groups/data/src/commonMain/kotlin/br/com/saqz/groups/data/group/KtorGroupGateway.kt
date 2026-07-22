package br.com.saqz.groups.data.group

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.group.*
import br.com.saqz.network.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class GroupRoleDto { OWNER, ADMIN, ATHLETE }

@Serializable
internal enum class GroupModalityDto { COURT_VOLLEYBALL, BEACH_VOLLEYBALL, FOOTVOLLEY }

@Serializable
internal enum class GroupCompositionDto { WOMEN, MEN, MIXED }

@Serializable
internal enum class GroupLevelDto { BEGINNER, INTERMEDIATE, ADVANCED, MIXED_LEVELS, CUSTOM }

@Serializable
internal enum class GroupPlayStyleDto { SIX_ZERO, FOUR_TWO, FIVE_ONE, CUSTOM }

@Serializable
internal enum class GroupProfileStatusDto { COMPLETE, INCOMPLETE }

@Serializable
internal enum class GroupPrivacyDto { PRIVATE }

@Serializable
internal enum class GroupCurrencyDto { BRL }

@Serializable
internal enum class GroupWeekdayDto { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@Serializable
internal data class GroupVenueDto(
    val id: String? = null,
    val name: String = "",
    val address: String = "",
    val court: String? = null,
)

@Serializable
internal data class GroupRegularSlotDto(
    val id: String? = null,
    val weekday: GroupWeekdayDto? = null,
    val startTime: String = "",
    val durationMinutes: Int = 0,
)

@Serializable
internal data class GroupProfileDto(
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
internal data class GroupFinanceDefaultsDto(
    val defaultGameFeeCents: Long? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

@Serializable
internal data class GroupDto(
    val id: String = "",
    val name: String = "",
    val timeZone: String = "",
    val version: Long? = null,
    val role: GroupRoleDto? = null,
    val profileStatus: GroupProfileStatusDto = GroupProfileStatusDto.COMPLETE,
    val privacy: GroupPrivacyDto = GroupPrivacyDto.PRIVATE,
    val currency: GroupCurrencyDto = GroupCurrencyDto.BRL,
    val profile: GroupProfileDto? = null,
    val financeDefaults: GroupFinanceDefaultsDto? = null,
)

@Serializable
private data class CreateGroupRequestDto(val requestId: String, val name: String, val timeZone: String)

@Serializable
private data class UpdateGroupSettingsRequestDto(val name: String, val timeZone: String)

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

class KtorGroupGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : GroupGateway, GroupProfileGateway {
    override suspend fun create(command: CreateGroupCommand): SaqzResult<Group, GroupProfileError> =
        retryTransport(RetrySafety.IdempotentWrite, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "api/groups",
                GroupDto.serializer(),
                NetworkRequest(
                    json.encodeToString(
                        CreateGroupRequestDto(command.commandKey, command.name, command.timeZone.id),
                    ),
                ),
            )
        }.toGroupResult()

    override suspend fun read(groupId: GroupId): SaqzResult<VersionedGroup, GroupProfileError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, "api/groups/${groupId.value}", GroupDto.serializer())
        }.toVersionedResult()

    override suspend fun update(command: UpdateGroupSettingsCommand): SaqzResult<VersionedGroup, GroupProfileError> =
        network.execute(
            HttpMethod.Put,
            "api/groups/${command.groupId.value}/settings",
            GroupDto.serializer(),
            NetworkRequest(
                json.encodeToString(UpdateGroupSettingsRequestDto(command.name, command.timeZone.id)),
                mapOf(HttpHeaders.IfMatch to command.versionToken.value),
            ),
        ).toVersionedResult()

    override suspend fun createProfile(command: CreateGroupProfileCommand): SaqzResult<Group, GroupProfileError> =
        retryTransport(RetrySafety.IdempotentWrite, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "api/groups",
                GroupDto.serializer(),
                NetworkRequest(
                    json.encodeToString(command.form.toRequest(command.commandKey, command.timeZone.id)),
                ),
            )
        }.toGroupResult()

    override suspend fun readProfile(groupId: GroupId) = read(groupId)

    override suspend fun updateProfile(command: UpdateGroupProfileCommand): SaqzResult<VersionedGroup, GroupProfileError> =
        network.execute(
            HttpMethod.Put,
            "api/groups/${command.groupId.value}",
            GroupDto.serializer(),
            NetworkRequest(
                json.encodeToString(command.form.toRequest()),
                mapOf(HttpHeaders.IfMatch to command.versionToken.value),
            ),
        ).toVersionedResult()
}

private fun NetworkResult<GroupDto>.toGroupResult(): SaqzResult<Group, GroupProfileError> = when (this) {
    is NetworkResult.Success -> value.toDomain()?.let { group -> SaqzResult.Success(group) } ?: invalidResponse()
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
}

private fun NetworkResult<GroupDto>.toVersionedResult(): SaqzResult<VersionedGroup, GroupProfileError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomainError())
    is NetworkResult.Success -> {
        val group = value.toDomain()
        val token = metadata.header(HttpHeaders.ETag)
        if (group == null || token.isNullOrBlank()) {
            invalidResponse()
        } else {
            SaqzResult.Success(VersionedGroup(group, GroupVersionToken(token)))
        }
    }
}

private fun GroupDto.toDomain(): Group? {
    if (id.isBlank() || name.isBlank() || timeZone.isBlank() || version == null || role == null) return null
    val domainProfile = profile?.toDomain() ?: if (profile == null) null else return null
    return Group(
        id = GroupId(id),
        name = name,
        timeZone = GroupTimeZone(timeZone),
        version = version,
        role = GroupRole.valueOf(role.name),
        profileStatus = GroupProfileStatus.valueOf(profileStatus.name),
        privacy = GroupPrivacy.valueOf(privacy.name),
        currency = GroupCurrency.valueOf(currency.name),
        profile = domainProfile,
        financeDefaults = financeDefaults?.let {
            GroupFinanceDefaults(it.defaultGameFeeCents, it.monthlyFeeCents, it.monthlyDueDay)
        },
    )
}

private fun GroupProfileDto.toDomain(): GroupProfile? {
    if (defaultVenue?.let { it.name.isBlank() || it.address.isBlank() } == true) return null
    if (regularSlots.any { it.weekday == null || it.startTime.isBlank() || it.durationMinutes <= 0 }) return null
    return GroupProfile(
        modality = modality?.let { GroupModality.valueOf(it.name) },
        composition = composition?.let { GroupComposition.valueOf(it.name) },
        description = description,
        city = city,
        level = level?.let { GroupLevel.valueOf(it.name) },
        customLevel = customLevel,
        playStyle = playStyle?.let { GroupPlayStyle.valueOf(it.name) },
        customPlayStyle = customPlayStyle,
        defaultVenue = defaultVenue?.let { GroupVenue(it.id, it.name, it.address, it.court) },
        regularSlots = regularSlots.map {
            GroupRegularSlot(
                it.id,
                GroupWeekday.valueOf(requireNotNull(it.weekday).name),
                it.startTime,
                it.durationMinutes,
            )
        },
        defaultCapacity = defaultCapacity,
        defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
    )
}

private fun GroupSetupForm.toRequest(requestId: String? = null, timeZone: String? = null): CompleteGroupRequestDto {
    val form = cleaned()
    return CompleteGroupRequestDto(
        requestId = requestId,
        name = form.name,
        modality = requireNotNull(form.modality).name,
        composition = requireNotNull(form.composition).name,
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

private fun NetworkError.toDomainError(): GroupProfileError = when (this) {
    is NetworkError.ApiProblemError -> when {
        problem.code == "VERSION_CONFLICT" || problem.status == 409 -> GroupProfileError.Conflict()
        problem.code == "VALIDATION_FAILED" || problem.status == 400 -> GroupProfileError.Validation(
            ValidationDetails(emptyList(), problem.fieldErrors.orEmpty()),
        )
        else -> GroupProfileError.DataFailure(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> GroupProfileError.DataFailure(status.toDataError())
    NetworkError.Timeout -> GroupProfileError.DataFailure(DataError.Timeout)
    NetworkError.Connectivity -> GroupProfileError.DataFailure(DataError.Connectivity)
    NetworkError.InvalidResponse -> GroupProfileError.DataFailure(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> GroupProfileError.DataFailure(DataError.PayloadTooLarge)
    NetworkError.Unavailable, NetworkError.Unknown -> GroupProfileError.DataFailure(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun invalidResponse() = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.InvalidResponse))
