package br.com.saqz.groups.presentation

import br.com.saqz.groups.data.RolesInvitesGateway
import br.com.saqz.network.NetworkResult
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
    private val roles: RolesInvitesGateway,
    private val groupId: () -> String?,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(InviteToolState())
    val state: StateFlow<InviteToolState> = mutableState.asStateFlow()

    fun rotate() {
        val currentGroupId = begin() ?: return
        scope.launch {
            mutableState.value = when (val result = roles.rotateInvite(currentGroupId)) {
                is NetworkResult.Success -> InviteToolState(inviteUrl = result.value.inviteUrl)
                is NetworkResult.Failure -> failed()
            }
        }
    }

    fun expire() {
        val currentGroupId = begin() ?: return
        scope.launch {
            mutableState.value = when (val result = roles.expireInvite(currentGroupId)) {
                is NetworkResult.Success -> InviteToolState()
                is NetworkResult.Failure -> failed()
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

    private fun failed() = mutableState.value.copy(
        isLoading = false,
        error = InviteUiError.UNAVAILABLE,
    )
}
