package br.com.saqz.profile.presentation.deletion

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.AccountDeletionAuthorization
import br.com.saqz.profile.domain.AccountDeletionAuthorizationResult
import br.com.saqz.profile.domain.AccountDeletionMethod
import br.com.saqz.profile.domain.ProfileGateway
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AccountDeletionViewModel(
    private val gateway: ProfileGateway,
    private val authorization: AccountDeletionAuthorization,
) : MviViewModel<AccountDeletionState, AccountDeletionIntent, AccountDeletionEffect>(
    AccountDeletionState(supportsApple = authorization.supportsApple),
) {
    private var generation = 0
    init { load() }

    override fun handleIntent(intent: AccountDeletionIntent) {
        if (state.value.isBusy || state.value.deleted) return
        when (intent) {
            AccountDeletionIntent.Retry -> load()
            is AccountDeletionIntent.Confirm -> update { it.copy(confirmed = intent.value, error = null) }
            is AccountDeletionIntent.Password -> update { it.copy(password = intent.value, error = null) }
            is AccountDeletionIntent.Delete -> delete(intent.method)
        }
    }

    private fun load() {
        val request = ++generation
        update { it.copy(isLoading = true, loadFailed = false, userId = null, confirmed = false) }
        viewModelScope.launch {
            val result = gateway.bootstrap()
            if (request != generation) return@launch
            when (result) {
                is SaqzResult.Success -> update {
                    it.copy(isLoading = false, userId = result.value.user.id, email = result.value.user.email)
                }
                is SaqzResult.Failure -> update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    private fun delete(method: AccountDeletionMethod) {
        val snapshot = state.value
        val id = snapshot.userId ?: return
        if (!snapshot.canDelete) return
        if (method == AccountDeletionMethod.PASSWORD && snapshot.password.isEmpty()) return
        if (method == AccountDeletionMethod.APPLE && !snapshot.supportsApple) return
        ++generation
        update { it.copy(isBusy = true, password = "", error = null) }
        viewModelScope.launch {
            val result = suspendCancellableCoroutine { continuation ->
                val cancellation = authorization.authorize(id, method, snapshot.password) {
                    if (continuation.isActive) continuation.resume(it)
                }
                continuation.invokeOnCancellation { cancellation.cancel() }
            }
            when (result) {
                AccountDeletionAuthorizationResult.AUTHORIZED -> when (gateway.deleteSession(id)) {
                    is SaqzResult.Success -> {
                        update { it.copy(isBusy = false, deleted = true) }
                        emit(AccountDeletionEffect.DELETED)
                    }
                    is SaqzResult.Failure -> update { it.copy(isBusy = false, error = AccountDeletionError.REQUEST) }
                }
                AccountDeletionAuthorizationResult.CANCELLED -> update { it.copy(isBusy = false) }
                AccountDeletionAuthorizationResult.REJECTED -> update {
                    it.copy(isBusy = false, error = AccountDeletionError.AUTHENTICATION)
                }
            }
        }
    }
}
