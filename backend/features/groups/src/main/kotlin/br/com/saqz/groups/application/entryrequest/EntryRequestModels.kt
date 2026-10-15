package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.invite.redeem.GroupAthleteOccupancy
import br.com.saqz.groups.application.membership.AccessMembership
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
import java.time.Instant
import java.util.UUID

data class GroupEntryRequest(
    val userId: UUID,
    val displayName: AccessName,
    val requestedAt: Instant,
)

data class SaveEntryRequestAthleteProfileCommand(
    val groupId: UUID,
    val userId: UUID,
    val nickname: String?,
    val position: AthletePosition?,
    val secondaryPosition: AthletePosition?,
    val level: AthleteLevel?,
    val preferredSide: AthletePreferredSide?,
    val heightCm: Int?,
)

interface EntryRequestRepository {
    fun list(groupId: UUID): List<GroupEntryRequest>

    fun find(groupId: UUID, userId: UUID): GroupEntryRequest?

    /** Stores the athlete registration on the pending request; false when the user has no pending request. */
    fun saveAthleteProfile(command: SaveEntryRequestAthleteProfileCommand): Boolean

    /** Copies the registration stored on the request (if any) to the membership created on approval. */
    fun applyAthleteProfileToMembership(groupId: UUID, userId: UUID)

    fun delete(groupId: UUID, userId: UUID)

    fun loadAthleteOccupancy(groupId: UUID): GroupAthleteOccupancy?
}

sealed interface ListEntryRequestsResult {
    data class Success(val requests: List<GroupEntryRequest>) : ListEntryRequestsResult

    data object GroupNotFound : ListEntryRequestsResult

    data object AccessForbidden : ListEntryRequestsResult
}

sealed interface ApproveEntryRequestResult {
    data class Success(val membership: AccessMembership) : ApproveEntryRequestResult

    data object GroupNotFound : ApproveEntryRequestResult

    data object AccessForbidden : ApproveEntryRequestResult

    data object RequestNotFound : ApproveEntryRequestResult

    data object AthleteLimitExceeded : ApproveEntryRequestResult
}

sealed interface RejectEntryRequestResult {
    data object Success : RejectEntryRequestResult

    data object GroupNotFound : RejectEntryRequestResult

    data object AccessForbidden : RejectEntryRequestResult
}

sealed interface SaveEntryRequestAthleteProfileResult {
    data object Success : SaveEntryRequestAthleteProfileResult

    data object GroupNotFound : SaveEntryRequestAthleteProfileResult

    data object RequestNotFound : SaveEntryRequestAthleteProfileResult

    data class Invalid(val fieldErrors: Map<String, List<String>>) : SaveEntryRequestAthleteProfileResult
}
