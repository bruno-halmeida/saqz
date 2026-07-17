package br.com.saqz.access.application.invite.redeem

import br.com.saqz.access.application.invite.InviteTokenDigest
import br.com.saqz.access.domain.GroupRole
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

data class RedeemableInvite(val groupId: UUID)

data class RecordInvalidInviteAttempt(
    val userId: UUID,
    val windowStartedAt: Instant,
    val invalidCount: Int,
)

data class RedeemMembershipCommand(
    val groupId: UUID,
    val userId: UUID,
)

sealed interface RedeemInviteResult {
    data class Success(val groupId: UUID, val role: GroupRole) : RedeemInviteResult

    data class AttemptLimit(val retryAfterSeconds: Int) : RedeemInviteResult

    data object InvalidOrExpired : RedeemInviteResult
}

interface InviteRedemptionRepository {
    fun lockAttemptWindow(userId: UUID): InviteAttemptWindow?

    fun findInvite(digest: InviteTokenDigest): RedeemableInvite?

    fun recordInvalidAttempt(command: RecordInvalidInviteAttempt)

    fun redeemMembership(command: RedeemMembershipCommand): GroupRole
}
