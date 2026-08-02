package br.com.saqz.profile.domain

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails

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

sealed interface ProfileError : SaqzError {
    data class Validation(val details: ValidationDetails) : ProfileError
    data class DataFailure(val error: DataError) : ProfileError
}

interface ProfileGateway {
    suspend fun bootstrap(): SaqzResult<Profile, ProfileError>

    suspend fun updateProfile(
        request: UpdateSessionProfileRequest,
    ): SaqzResult<Profile, ProfileError>

    suspend fun stats(): SaqzResult<ProfileStats, ProfileError>

    suspend fun athleteProfile(): SaqzResult<AthleteProfile, ProfileError>

    suspend fun deleteSession(): SaqzResult<Unit, ProfileError>

    suspend fun uploadPhoto(bytes: ByteArray, mediaType: String): SaqzResult<Unit, ProfileError>

    suspend fun deletePhoto(): SaqzResult<Unit, ProfileError>

    suspend fun loadProfile(): SaqzResult<Profile, ProfileError> = bootstrap()

    suspend fun profileStats(): SaqzResult<ProfileStats, ProfileError> = stats()

    suspend fun ownAthleteProfile(): SaqzResult<AthleteProfile, ProfileError> = athleteProfile()
}
