package br.com.saqz.profile.presentation.deletion

import br.com.saqz.profile.domain.AccountDeletionMethod

data class AccountDeletionState(
    val userId: String? = null,
    val email: String? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val confirmed: Boolean = false,
    val password: String = "",
    val supportsApple: Boolean = false,
    val isBusy: Boolean = false,
    val deleted: Boolean = false,
    val error: AccountDeletionError? = null,
) {
    val canDelete get() = userId != null && confirmed && !isBusy && !deleted
    override fun toString() = "AccountDeletionState(loading=$isLoading, busy=$isBusy, deleted=$deleted)"
}

enum class AccountDeletionError { AUTHENTICATION, REQUEST }
sealed interface AccountDeletionIntent {
    data object Retry : AccountDeletionIntent
    data class Confirm(val value: Boolean) : AccountDeletionIntent
    class Password(val value: String) : AccountDeletionIntent {
        override fun toString() = "Password(redacted)"
    }
    data class Delete(val method: AccountDeletionMethod) : AccountDeletionIntent
}
enum class AccountDeletionEffect { DELETED }
