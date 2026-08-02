package br.com.saqz.profile.domain

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

enum class PhoneVisibility {
    EVERYONE,
    ADMINS,
    NOBODY,
}

data class ProfileUser(
    val id: String,
    val email: String?,
    val displayName: String,
    val nickname: String?,
    val phone: String?,
    val phoneRequired: Boolean,
    val phoneVisibility: PhoneVisibility,
    val city: String?,
    val emailVerified: Boolean,
    val photoUrl: String?,
)

data class ProfileMembership(
    val groupId: GroupId,
    val groupName: String,
    val role: String,
)

data class Profile(
    val user: ProfileUser,
    val memberships: List<ProfileMembership>,
)

typealias UserProfile = Profile

data class ProfileStats(
    val games: Int,
    val attendanceRate: Int?,
    val groups: Int,
)

typealias ProfileStatistics = ProfileStats

data class AthleteMembership(
    val groupId: GroupId,
    val groupName: String,
    val role: String,
    val position: String?,
    val membershipType: String,
    val active: Boolean,
)

data class AthleteProfile(
    val userId: String,
    val displayName: String,
    val phone: String?,
    val memberships: List<AthleteMembership>,
)

typealias OwnAthleteProfile = AthleteProfile

sealed interface UpdateField<out T> {
    data object Unchanged : UpdateField<Nothing>

    data class Set<out T>(val value: T) : UpdateField<T>
}

data class UpdateSessionProfileRequest(
    val displayName: UpdateField<String> = UpdateField.Unchanged,
    val nickname: UpdateField<String?> = UpdateField.Unchanged,
    val phone: UpdateField<String?> = UpdateField.Unchanged,
    val city: UpdateField<String?> = UpdateField.Unchanged,
    val phoneVisibility: UpdateField<PhoneVisibility> = UpdateField.Unchanged,
)

interface ProfileGateway {
    suspend fun bootstrap(): SaqzResult<Profile, DataError>

    suspend fun updateProfile(
        request: UpdateSessionProfileRequest,
    ): SaqzResult<Profile, DataError>

    suspend fun stats(): SaqzResult<ProfileStats, DataError>

    suspend fun athleteProfile(): SaqzResult<AthleteProfile, DataError>

    suspend fun deleteSession(): SaqzResult<Unit, DataError>

    suspend fun uploadPhoto(bytes: ByteArray, mediaType: String): SaqzResult<Unit, DataError>

    suspend fun deletePhoto(): SaqzResult<Unit, DataError>

    suspend fun loadProfile(): SaqzResult<Profile, DataError> = bootstrap()

    suspend fun profileStats(): SaqzResult<ProfileStats, DataError> = stats()

    suspend fun ownAthleteProfile(): SaqzResult<AthleteProfile, DataError> = athleteProfile()
}
