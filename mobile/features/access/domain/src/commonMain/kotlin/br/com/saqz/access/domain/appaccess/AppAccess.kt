package br.com.saqz.access.domain.appaccess

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

data class AppAccessSession(
    val customToken: String,
    val ownerUserId: String,
    val displayName: String,
    val onboardingCompleted: Boolean,
) {
    override fun toString(): String =
        "AppAccessSession(customToken=<redacted>, ownerUserId=$ownerUserId, " +
            "displayName=$displayName, onboardingCompleted=$onboardingCompleted)"
}

sealed interface AppAccessError : SaqzError {
    data object CodeInvalid : AppAccessError
    data object ProviderUnavailable : AppAccessError
    data object IdentityMismatch : AppAccessError
    data class Data(val error: DataError) : AppAccessError
}

interface AppAccessGateway {
    /** Redeem is anonymous, single-use, and must never be retried by the client. */
    suspend fun redeem(code: String): SaqzResult<AppAccessSession, AppAccessError>
}

data class OnboardingStatus(val completed: Boolean)

sealed interface OnboardingError : SaqzError {
    data object AccountNotFound : OnboardingError
    data object AccountSuspended : OnboardingError
    data class Data(val error: DataError) : OnboardingError
}

interface OnboardingGateway {
    suspend fun status(): SaqzResult<OnboardingStatus, OnboardingError>

    suspend fun complete(): SaqzResult<OnboardingStatus, OnboardingError>
}
