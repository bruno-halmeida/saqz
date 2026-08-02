package br.com.saqz.groups.domain.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

data class InvitePreview(
    val groupName: String,
    val inviterName: String,
    val entryRequiresApproval: Boolean,
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
    data object GroupDeleted : InviteError
    data class RateLimited(val retryAfterSeconds: Int?) : InviteError
    data object PlanLimit : InviteError
    data class DataFailure(val error: DataError) : InviteError
}

interface InviteGateway {
    suspend fun preview(code: InviteCode): SaqzResult<InvitePreview, InviteError>

    suspend fun redeem(code: InviteCode): SaqzResult<InviteRedeem, InviteError>
}
