package br.com.saqz.composeapp.subscriptiongate

import androidx.compose.runtime.Immutable

@Immutable
data class SubscriptionGateState(
    val status: SubscriptionGateStatus = SubscriptionGateStatus.Initial,
    val maskedEmail: String? = null,
    val failure: SubscriptionGateFailure? = null,
)

enum class SubscriptionGateStatus {
    Initial,
    Sending,
    Sent,
    Failed,
    Verifying,
    NotAuthorized,
    Authorized,
}

sealed interface SubscriptionGateFailure {
    data object Authorization : SubscriptionGateFailure

    data object PurchaseInformation : SubscriptionGateFailure
}

sealed interface SubscriptionGateIntent {
    data object Opened : SubscriptionGateIntent

    data object Closed : SubscriptionGateIntent

    data class ForegroundChanged(val isForeground: Boolean) : SubscriptionGateIntent

    data object RequestPurchaseInformation : SubscriptionGateIntent

    data object RefreshAuthorization : SubscriptionGateIntent
}

sealed interface SubscriptionGateEffect {
    data object AuthorizationGranted : SubscriptionGateEffect
}
