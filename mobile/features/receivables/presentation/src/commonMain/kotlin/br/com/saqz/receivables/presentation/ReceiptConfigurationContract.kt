package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable
data class ReceiptConfigurationRoute(val groupId: String) : NavKey

data class ReceiptConfigurationState(
    val loading: Boolean = true,
    val accounts: List<ReceiptAccount> = emptyList(),
    val accountId: String? = null,
    val status: ReceiptStatus? = null,
    val methods: Set<ReceiptMethod> = emptySet(),
    val review: ReceiptReview? = null,
    val terms: List<ReceiptTerms> = emptyList(),
    val accepted: Boolean = false,
    val discoveryAvailable: Boolean = false,
    val error: ReceiptError? = null,
    val completed: Boolean = false,
    val pendingMutation: Boolean = false,
    val confirmingDeactivation: Boolean = false,
) {
    val hasNoAccount get() = accounts.isEmpty() && status == null
    val canPreview get() = !loading && !pendingMutation && status != null && methods.isNotEmpty() && discoveryAvailable
    val canAccept get() = !loading && !pendingMutation && review != null && terms.isNotEmpty() &&
        review.schedules.map { it.termsVersion }.toSet() == terms.map { it.version }.toSet()
    val canActivate get() = canAccept && accepted && discoveryAvailable &&
        review?.permissions?.get("ACTIVATE_GROUP")?.allowed == true
    val canDeactivate get() = !loading && !pendingMutation && status?.state?.enabled == true &&
        status.permissions["CANCEL"]?.allowed == true
}
sealed interface ReceiptConfigurationIntent {
    data object RetryMutation : ReceiptConfigurationIntent
    data object Refresh : ReceiptConfigurationIntent
    data class SelectAccount(val id: String) : ReceiptConfigurationIntent
    data class ToggleMethod(val method: ReceiptMethod) : ReceiptConfigurationIntent
    data object Preview : ReceiptConfigurationIntent
    data class Accept(val accepted: Boolean) : ReceiptConfigurationIntent
    data object Activate : ReceiptConfigurationIntent
    data object RequestDeactivation : ReceiptConfigurationIntent
    data object DismissDeactivation : ReceiptConfigurationIntent
    data object Deactivate : ReceiptConfigurationIntent
}
