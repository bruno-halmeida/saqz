package br.com.saqz.profile.domain

enum class AccountDeletionMethod { PASSWORD, GOOGLE, APPLE }
enum class AccountDeletionAuthorizationResult { AUTHORIZED, CANCELLED, REJECTED }

fun interface AccountDeletionCancellation { fun cancel() }

/** Confirms the same account, refreshes authentication and revokes Apple consent when linked. */
interface AccountDeletionAuthorization {
    val supportsApple: Boolean
    fun authorize(
        expectedUserId: String,
        method: AccountDeletionMethod,
        password: String,
        done: (AccountDeletionAuthorizationResult) -> Unit,
    ): AccountDeletionCancellation
}
