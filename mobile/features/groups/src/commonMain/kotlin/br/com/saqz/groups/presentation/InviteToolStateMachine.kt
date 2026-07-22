package br.com.saqz.groups.presentation

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.GroupMembershipError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InviteToolState(
    val inviteUrl: String? = null,
    val isLoading: Boolean = false,
    val error: InviteUiError? = null,
    val retryAfterSeconds: Int? = null,
)

class InviteToolStateMachine(
    private val roles: GroupMembershipGateway,
    private val groupId: () -> String?,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(InviteToolState())
    val state: StateFlow<InviteToolState> = mutableState.asStateFlow()

    fun rotate() {
        val currentGroupId = begin() ?: return
        scope.launch {
            mutableState.value = when (val result = roles.rotateInvite(GroupId(currentGroupId))) {
                is SaqzResult.Success -> InviteToolState(inviteUrl = result.value.value)
                is SaqzResult.Failure -> failed(result.error)
            }
        }
    }

    fun expire() {
        val currentGroupId = begin() ?: return
        scope.launch {
            mutableState.value = when (val result = roles.expireInvite(GroupId(currentGroupId))) {
                is SaqzResult.Success -> InviteToolState()
                is SaqzResult.Failure -> failed(result.error)
            }
        }
    }

    fun shareFinished(successful: Boolean) {
        if (!successful) mutableState.value = mutableState.value.copy(error = InviteUiError.UNAVAILABLE)
    }

    private fun begin(): String? {
        val currentGroupId = groupId() ?: return null
        if (mutableState.value.isLoading) return null
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        return currentGroupId
    }

    private fun failed(error: GroupMembershipError) = mutableState.value.copy(
        isLoading = false,
        error = when (error) {
            GroupMembershipError.InvalidOrExpired -> InviteUiError.INVALID_OR_EXPIRED
            is GroupMembershipError.AttemptLimit -> InviteUiError.ATTEMPT_LIMIT
            is GroupMembershipError.Validation,
            is GroupMembershipError.DataFailure,
            -> InviteUiError.UNAVAILABLE
        },
        retryAfterSeconds = (error as? GroupMembershipError.AttemptLimit)?.retryAfterSeconds,
    )
}
