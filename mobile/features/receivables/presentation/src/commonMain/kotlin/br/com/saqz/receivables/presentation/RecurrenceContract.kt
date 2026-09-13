package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable data class RecurrenceRoute(val accountId: String, val groupId: String) : NavKey

data class RecurrenceState(
    val accountId: String,
    val groupId: String,
    val loading: Boolean = true,
    val recurrence: PaymentRecurrence? = null,
    val review: RecurrenceReview? = null,
    val terms: ReceiptTerms? = null,
    val method: ReceiptMethod = ReceiptMethod.PIX,
    val firstDueDate: String = "",
    val name: String = "",
    val document: String = "",
    val accepted: Boolean = false,
    val pending: Boolean = false,
    val error: ReceiptError? = null,
    val checkoutOpenFailed: Boolean = false,
) {
    val canEditReview get() = !loading && !pending && recurrence?.status !in setOf("ACTIVE", "AUTHORIZING", "STOP_PENDING")
    val canPreview get() = canEditReview && firstDueDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    val canAccept get(): Boolean {
        val currentReview = review ?: return false
        val currentTerms = terms ?: return false
        return canEditReview && currentReview.method == method && currentReview.firstDueDate == firstDueDate &&
            currentTerms.version == currentReview.termsVersion && currentTerms.content.isNotBlank()
    }
    val canSubmit get() = error == null && canAccept && accepted && name.trim().length in 2..120 && name.none(Char::isISOControl) &&
        document.matches(Regex("[0-9]{11}|[0-9]{14}"))
    val canCancel get() = !loading && !pending && recurrence?.status in setOf("ACTIVE", "AUTHORIZING")
    val canResume get() = recurrence?.status == "STOPPED"
    val canOpenCheckout get(): Boolean {
        val current = recurrence ?: return false
        return !loading && !pending && current.status == "AUTHORIZING" && current.method == ReceiptMethod.CARD &&
            current.hostedCheckoutUrl?.let(::hostedPaymentUrl) == true
    }
}

sealed interface RecurrenceIntent {
    data object Refresh : RecurrenceIntent
    data class Method(val value: ReceiptMethod) : RecurrenceIntent
    data class DueDate(val value: String) : RecurrenceIntent
    data object Preview : RecurrenceIntent
    data class Name(val value: String) : RecurrenceIntent
    data class Document(val value: String) : RecurrenceIntent
    data class Accept(val value: Boolean) : RecurrenceIntent
    data object Submit : RecurrenceIntent
    data object Cancel : RecurrenceIntent
    data object OpenCheckout : RecurrenceIntent
    data object OpenFailed : RecurrenceIntent
}

sealed interface RecurrenceEffect {
    val generation: Int
    data class Open(val url: String, override val generation: Int) : RecurrenceEffect
}
