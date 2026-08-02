package br.com.saqz.profile.data

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkMediaUpload
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import br.com.saqz.profile.domain.AthleteMembership
import br.com.saqz.profile.domain.AthleteProfile
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.domain.Profile
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.ProfileMembership
import br.com.saqz.profile.domain.ProfileStats
import br.com.saqz.profile.domain.ProfileUser
import br.com.saqz.profile.domain.UpdateField
import br.com.saqz.profile.domain.UpdateSessionProfileRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val USER_PHOTO_PATH = "api/session/photo"

private const val SESSION_PATH = "api/session"
private const val SESSION_PROFILE_PATH = "api/session/profile"
private const val PROFILE_STATS_PATH = "api/profile/stats"
private const val ATHLETE_PROFILE_PATH = "api/athletes/me"

@Serializable
internal data class ProfileUserTransport(
    val id: String = "",
    val email: String? = null,
    val displayName: String = "",
    val nickname: String? = null,
    val phone: String? = null,
    val phoneRequired: Boolean = false,
    val phoneVisibility: String = "",
    val city: String? = null,
    val emailVerified: Boolean = false,
    val photoUrl: String? = null,
)

@Serializable
internal data class ProfileMembershipTransport(
    val groupId: String = "",
    val groupName: String = "",
    val role: String = "",
)

@Serializable
internal data class ProfileTransport(
    val user: ProfileUserTransport,
    val memberships: List<ProfileMembershipTransport> = emptyList(),
)

@Serializable
internal data class ProfileStatsTransport(
    val games: Int? = null,
    val attendanceRate: Int? = null,
    val groups: Int? = null,
)

@Serializable
internal data class AthleteMembershipTransport(
    val groupId: String = "",
    val groupName: String = "",
    val role: String = "",
    val position: String? = null,
    val membershipType: String = "",
    val active: Boolean? = null,
)

@Serializable
internal data class AthleteProfileTransport(
    val userId: String = "",
    val displayName: String = "",
    val phone: String? = null,
    val memberships: List<AthleteMembershipTransport> = emptyList(),
)

class KtorProfileGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : ProfileGateway {
    override suspend fun bootstrap(): SaqzResult<Profile, DataError> =
        network.execute(HttpMethod.Put, SESSION_PATH, ProfileTransport.serializer()).toProfileResult()

    override suspend fun updateProfile(
        request: UpdateSessionProfileRequest,
    ): SaqzResult<Profile, DataError> = network.execute(
        HttpMethod.Patch,
        SESSION_PROFILE_PATH,
        ProfileTransport.serializer(),
        NetworkRequest(body = request.toJson()),
    ).toProfileResult()

    override suspend fun stats(): SaqzResult<ProfileStats, DataError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, PROFILE_STATS_PATH, ProfileStatsTransport.serializer())
        }.toStatsResult()

    override suspend fun athleteProfile(): SaqzResult<AthleteProfile, DataError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, ATHLETE_PROFILE_PATH, AthleteProfileTransport.serializer())
        }.toAthleteProfileResult()

    override suspend fun deleteSession(): SaqzResult<Unit, DataError> =
        network.executeNoContent(HttpMethod.Delete, SESSION_PATH).toEmptyResult()

    override suspend fun uploadPhoto(bytes: ByteArray, mediaType: String): SaqzResult<Unit, DataError> =
        network.uploadMedia(
            HttpMethod.Put,
            USER_PHOTO_PATH,
            NetworkMediaUpload(
                fileName = "user-photo",
                contentType = ContentType.parse(mediaType),
                contentLength = bytes.size.toLong(),
                openChannel = { ByteReadChannel(bytes) },
            ),
        ).toEmptyResult()

    override suspend fun deletePhoto(): SaqzResult<Unit, DataError> =
        network.executeNoContent(HttpMethod.Delete, USER_PHOTO_PATH).toEmptyResult()

    private fun UpdateSessionProfileRequest.toJson(): String = buildJsonObject {
        putString("displayName", displayName)
        putNullableString("nickname", nickname)
        putNullableString("phone", phone)
        putNullableString("city", city)
        putPhoneVisibility("phoneVisibility", phoneVisibility)
    }.toString()

    private fun JsonObjectBuilder.putString(field: String, value: UpdateField<String>) {
        if (value is UpdateField.Set) put(field, JsonPrimitive(value.value))
    }

    private fun JsonObjectBuilder.putNullableString(field: String, value: UpdateField<String?>) {
        if (value is UpdateField.Set) put(field, value.value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun JsonObjectBuilder.putPhoneVisibility(
        field: String,
        value: UpdateField<PhoneVisibility>,
    ) {
        if (value is UpdateField.Set) put(field, JsonPrimitive(value.value.name))
    }
}

private fun NetworkResult<ProfileTransport>.toProfileResult(): SaqzResult<Profile, DataError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDataError())
    is NetworkResult.Success -> value.toDomain()
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult<ProfileStatsTransport>.toStatsResult(): SaqzResult<ProfileStats, DataError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDataError())
    is NetworkResult.Success -> value.toDomain()
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult<AthleteProfileTransport>.toAthleteProfileResult(): SaqzResult<AthleteProfile, DataError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDataError())
    is NetworkResult.Success -> value.toDomain()
        ?.let { SaqzResult.Success(it) }
        ?: invalidResponse()
}

private fun NetworkResult<Unit>.toEmptyResult(): SaqzResult<Unit, DataError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDataError())
    is NetworkResult.Success -> SaqzResult.Success(Unit)
}

private fun ProfileTransport.toDomain(): Profile? {
    val visibility = user.phoneVisibility.toEnumOrNull<PhoneVisibility>() ?: return null
    if (user.id.isBlank() || user.displayName.isBlank()) return null
    if (memberships.any { it.groupId.isBlank() || it.groupName.isBlank() || it.role.isBlank() }) return null
    return Profile(
        user = ProfileUser(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            nickname = user.nickname,
            phone = user.phone,
            phoneRequired = user.phoneRequired,
            phoneVisibility = visibility,
            city = user.city,
            emailVerified = user.emailVerified,
            photoUrl = user.photoUrl,
        ),
        memberships = memberships.map {
            ProfileMembership(GroupId(it.groupId), it.groupName, it.role)
        },
    )
}

private fun ProfileStatsTransport.toDomain(): ProfileStats? {
    val games = games ?: return null
    val groups = groups ?: return null
    if (games < 0 || groups < 0) return null
    if (attendanceRate != null && attendanceRate !in 0..100) return null
    return ProfileStats(games, attendanceRate, groups)
}

private fun AthleteProfileTransport.toDomain(): AthleteProfile? {
    if (userId.isBlank() || displayName.isBlank()) return null
    if (memberships.any { it.toDomain() == null }) return null
    return AthleteProfile(userId, displayName, phone, memberships.map { it.toDomain()!! })
}

private fun AthleteMembershipTransport.toDomain(): AthleteMembership? {
    if (groupId.isBlank() || groupName.isBlank() || role.isBlank() || membershipType.isBlank()) return null
    if (position != null && position.isBlank()) return null
    return AthleteMembership(
        groupId = GroupId(groupId),
        groupName = groupName,
        role = role,
        position = position,
        membershipType = membershipType,
        active = active ?: return null,
    )
}

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()

private fun NetworkError.toDataError(): DataError = when (this) {
    is NetworkError.ApiProblemError -> when {
        problem.code == "VALIDATION_FAILED" || problem.status == 400 -> DataError.Validation(
            ValidationDetails(emptyList(), problem.fieldErrors.orEmpty()),
        )
        else -> problem.status.toDataError()
    }
    is NetworkError.HttpStatus -> status.toDataError()
    NetworkError.Timeout -> DataError.Timeout
    NetworkError.Connectivity -> DataError.Connectivity
    NetworkError.InvalidResponse -> DataError.InvalidResponse
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
    NetworkError.Unavailable,
    NetworkError.Unknown,
    -> DataError.Unknown
}

private fun Int.toDataError(): DataError = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun <T> invalidResponse(): SaqzResult<T, DataError> =
    SaqzResult.Failure(DataError.InvalidResponse)
