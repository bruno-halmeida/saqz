package br.com.saqz.groups.application.invite.redeem

import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.plan.ClosedAthleteOccupancy
import java.time.Instant
import java.util.UUID

data class InviteAttemptWindow(
    val windowStartedAt: Instant,
    val invalidCount: Int,
) {
    init {
        require(invalidCount in 0..10) { "Invalid invite count must be between zero and ten" }
    }
}

data class RedeemableInvite(
    val groupId: UUID,
    val groupDeleted: Boolean = false,
    val entryRequiresApproval: Boolean = false,
)

data class RecordInvalidInviteAttempt(
    val userId: UUID,
    val windowStartedAt: Instant,
    val invalidCount: Int,
)

data class RedeemMembershipCommand(
    val groupId: UUID,
    val userId: UUID,
)

data class CreateEntryRequestCommand(
    val groupId: UUID,
    val userId: UUID,
    val requestedAt: Instant,
)

data class GroupAthleteOccupancy(
    val ownerUserId: UUID,
    val openMemberIds: Set<UUID>,
    val openWaitlistIds: Set<UUID>,
    val closedOccupancies: List<ClosedAthleteOccupancy>,
)

sealed interface RedeemInviteResult {
    data class Success(val groupId: UUID, val role: GroupRole) : RedeemInviteResult

    data class Pending(val groupId: UUID) : RedeemInviteResult

    data class AttemptLimit(val retryAfterSeconds: Int) : RedeemInviteResult

    data object InvalidOrExpired : RedeemInviteResult

    data object GroupDeleted : RedeemInviteResult

    data object AthleteLimitExceeded : RedeemInviteResult
}

interface InviteRedemptionRepository {
    fun lockAttemptWindow(userId: UUID, initializedAt: Instant): InviteAttemptWindow

    fun findInvite(digest: InviteTokenDigest): RedeemableInvite?

    fun recordInvalidAttempt(command: RecordInvalidInviteAttempt)

    fun loadAthleteOccupancy(groupId: UUID): GroupAthleteOccupancy?

    fun findMembershipRole(groupId: UUID, userId: UUID): GroupRole? = null

    fun createEntryRequest(command: CreateEntryRequestCommand) = Unit

    fun redeemMembership(command: RedeemMembershipCommand): GroupRole
}
