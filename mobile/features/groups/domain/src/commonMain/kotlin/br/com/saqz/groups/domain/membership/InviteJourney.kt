package br.com.saqz.groups.domain.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

data class InvitePreview(
    val groupName: String,
    val inviterName: String,
    val entryRequiresApproval: Boolean,
    val city: String? = null,
    val composition: String? = null,
    val level: String? = null,
    val memberCount: Int = 0,
    val regularSlots: List<InviteRegularSlot> = emptyList(),
    val expiresAt: String? = null,
    val nextGame: InviteNextGame? = null,
)

data class InviteRegularSlot(
    val weekday: String,
    val startTime: String,
)

data class InviteNextGame(
    val startsAt: String,
    val venueName: String,
    val court: String?,
)

enum class InviteRedeemStatus {
    JOINED,
    PENDING,
}

data class InviteRedeem(
    val status: InviteRedeemStatus,
    val groupId: GroupId,
    val role: String?,
)

sealed interface InviteError : SaqzError {
    data object InvalidOrExpired : InviteError
    data class Expired(val expiredAt: String) : InviteError
    data object GroupDeleted : InviteError
    data class RateLimited(val retryAfterSeconds: Int?) : InviteError
    data object PlanLimit : InviteError
    data class DataFailure(val error: DataError) : InviteError
}

interface InviteGateway {
    suspend fun preview(code: InviteCode): SaqzResult<InvitePreview, InviteError>

    suspend fun redeem(code: InviteCode): SaqzResult<InviteRedeem, InviteError>
}
