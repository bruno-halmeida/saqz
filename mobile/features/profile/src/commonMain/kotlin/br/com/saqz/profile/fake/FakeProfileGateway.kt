package br.com.saqz.profile.fake

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.AthleteMembership
import br.com.saqz.profile.domain.AthleteProfile
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.domain.Profile
import br.com.saqz.profile.domain.ProfileError
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.ProfileMembership
import br.com.saqz.profile.domain.ProfileStats
import br.com.saqz.profile.domain.ProfileUser
import br.com.saqz.profile.domain.UpdateField
import br.com.saqz.profile.domain.UpdateSessionProfileRequest

/** Fake determinístico para previews e testes das quatro telas do perfil. */
class FakeProfileGateway(
    initialProfile: Profile = previewProfile,
    initialStats: ProfileStats = previewStats,
    initialAthleteProfile: AthleteProfile = previewAthleteProfile,
) : ProfileGateway {
    var profile: Profile = initialProfile
    var stats: ProfileStats = initialStats
    var athleteProfile: AthleteProfile = initialAthleteProfile

    var profileError: ProfileError? = null
    var statsError: ProfileError? = null
    var athleteProfileError: ProfileError? = null
    var deleteSessionError: ProfileError? = null
    var uploadPhotoError: ProfileError? = null
    var deletePhotoError: ProfileError? = null

    val updateRequests = mutableListOf<UpdateSessionProfileRequest>()
    val uploadedPhotos = mutableListOf<UploadedPhoto>()
    var deleteSessionCalls: Int = 0
        private set
    var deletePhotoCalls: Int = 0
        private set
    var accountDeleted: Boolean = false
        private set

    override suspend fun bootstrap(): SaqzResult<Profile, ProfileError> =
        profileError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(profile)

    override suspend fun updateProfile(
        request: UpdateSessionProfileRequest,
    ): SaqzResult<Profile, ProfileError> {
        updateRequests += request
        profileError?.let { return SaqzResult.Failure(it) }
        profile = profile.apply(request)
        return SaqzResult.Success(profile)
    }

    override suspend fun stats(): SaqzResult<ProfileStats, ProfileError> =
        statsError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(stats)

    override suspend fun athleteProfile(): SaqzResult<AthleteProfile, ProfileError> =
        athleteProfileError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(athleteProfile)

    override suspend fun deleteSession(): SaqzResult<Unit, ProfileError> {
        deleteSessionCalls += 1
        deleteSessionError?.let { return SaqzResult.Failure(it) }
        accountDeleted = true
        return SaqzResult.Success(Unit)
    }

    override suspend fun uploadPhoto(bytes: ByteArray, mediaType: String): SaqzResult<Unit, ProfileError> {
        uploadPhotoError?.let { return SaqzResult.Failure(it) }
        uploadedPhotos += UploadedPhoto(bytes.copyOf(), mediaType)
        profile = profile.copy(
            user = profile.user.copy(photoUrl = "/api/session/photo?v=upload-${uploadedPhotos.size}"),
        )
        return SaqzResult.Success(Unit)
    }

    override suspend fun deletePhoto(): SaqzResult<Unit, ProfileError> {
        deletePhotoCalls += 1
        deletePhotoError?.let { return SaqzResult.Failure(it) }
        profile = profile.copy(user = profile.user.copy(photoUrl = null))
        return SaqzResult.Success(Unit)
    }

    data class UploadedPhoto(
        val bytes: ByteArray,
        val mediaType: String,
    ) {
        override fun equals(other: Any?): Boolean = other is UploadedPhoto &&
            bytes.contentEquals(other.bytes) && mediaType == other.mediaType

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
    }

    private fun Profile.apply(request: UpdateSessionProfileRequest): Profile = copy(
        user = user.copy(
            displayName = request.displayName.valueOr(user.displayName),
            nickname = request.nickname.valueOr(user.nickname),
            phone = request.phone.valueOr(user.phone),
            city = request.city.valueOr(user.city),
            phoneVisibility = request.phoneVisibility.valueOr(user.phoneVisibility),
        ),
    )

    private fun <T> UpdateField<T>.valueOr(current: T): T = when (this) {
        UpdateField.Unchanged -> current
        is UpdateField.Set -> value
    }

    private companion object {
        val previewProfile = Profile(
            user = ProfileUser(
                id = "user-preview",
                email = "rafael@email.com",
                displayName = "Rafael Costa",
                nickname = "Rafa",
                phone = "+5511988765432",
                phoneRequired = false,
                phoneVisibility = PhoneVisibility.ADMINS,
                city = "São Paulo, SP",
                emailVerified = true,
                photoUrl = "/api/session/photo?v=preview",
            ),
            memberships = listOf(
                ProfileMembership(GroupId("group-preview"), "Vôlei de Quinta", "ADMIN"),
            ),
        )

        val previewStats = ProfileStats(games = 42, attendanceRate = 89, groups = 3)

        val previewAthleteProfile = AthleteProfile(
            userId = "user-preview",
            displayName = "Rafael Costa",
            phone = "+5511988765432",
            memberships = listOf(
                AthleteMembership(
                    groupId = GroupId("group-preview"),
                    groupName = "Vôlei de Quinta",
                    role = "ADMIN",
                    position = "PONTA",
                    membershipType = "MENSALISTA",
                    active = true,
                ),
            ),
        )
    }
}
