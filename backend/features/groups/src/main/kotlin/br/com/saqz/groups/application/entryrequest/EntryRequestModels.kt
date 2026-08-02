package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.invite.redeem.GroupAthleteOccupancy
import br.com.saqz.groups.application.membership.AccessMembership
import br.com.saqz.groups.domain.AccessName
import java.time.Instant
import java.util.UUID

data class GroupEntryRequest(
    val userId: UUID,
    val displayName: AccessName,
    val requestedAt: Instant,
)

interface EntryRequestRepository {
    fun list(groupId: UUID): List<GroupEntryRequest>

    fun find(groupId: UUID, userId: UUID): GroupEntryRequest?

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
