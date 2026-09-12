package br.com.saqz.subscriptions.domain.trial

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class TrialStatus {
    Available,
    Active,
    Expired,
    Ineligible,
    Subscribed,
}

/** Server-authoritative trial access, either for the authenticated owner or a group member. */
data class TrialAccess(
    val status: TrialStatus,
    val startedAt: String?,
    val endsAt: String?,
    val serverTime: String,
    val readOnly: Boolean,
    val canCreateGroup: Boolean,
    val maxGroups: Int,
    val maxAthletes: Int,
    val isOwner: Boolean,
    val appUrl: String?,
)

sealed interface TrialError : SaqzError {
    data object NotFound : TrialError
    data class Data(val error: DataError) : TrialError
}

interface TrialGateway {
    suspend fun ownerTrial(): SaqzResult<TrialAccess, TrialError>

    suspend fun groupTrial(groupId: GroupId): SaqzResult<TrialAccess, TrialError>
}
