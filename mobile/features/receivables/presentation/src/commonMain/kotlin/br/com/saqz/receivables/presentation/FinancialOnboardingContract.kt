package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable data object FinancialOnboardingRoute : NavKey
@Serializable data class OnboardingAttempt(val actor: String, val requestId: String, val kind: String,
    val documentId: String? = null, val documentStatus: String? = null)
enum class OnboardingField { NAME, EMAIL, DOCUMENT, PHONE, INCOME, STREET, NUMBER, PROVINCE, POSTAL_CODE, BIRTH_DATE, COMPANY_TYPE }
data class OnboardingForm(val values: Map<OnboardingField, String> = emptyMap(), val company: Boolean = false) {
    operator fun get(field: OnboardingField) = values[field].orEmpty()
    override fun toString() = "OnboardingForm(redacted)"
}
data class FinancialOnboardingState(val loading: Boolean = true, val account: OwnedReceiptAccount? = null,
    val discovered: Boolean = false, val available: Boolean = false, val terms: ReceiptTerms? = null,
    val form: OnboardingForm = OnboardingForm(), val accepted: Boolean = false,
    val documents: List<ReceiptDocument> = emptyList(), val attempt: OnboardingAttempt? = null,
    val selectedDocument: String? = null, val selectedFile: ReceiptDocumentFile? = null,
    val picking: Boolean = false, val uploaded: Boolean = false, val error: ReceiptError? = null) {
    val pending get() = attempt != null
    val canEdit get() = !loading && !pending && discovered && account == null && available && error == null
    val canAccept get() = canEdit && terms?.version?.isNotBlank() == true && terms.content.isNotBlank()
    val canCreate get() = canAccept && accepted && form.registration() != null
    val canUseDocuments get() = !loading && !pending && account != null && error == null
}
sealed interface FinancialOnboardingIntent {
    data object Refresh : FinancialOnboardingIntent
    data class Edit(val field: OnboardingField, val value: String) : FinancialOnboardingIntent
    data class Company(val value: Boolean) : FinancialOnboardingIntent
    data class Accept(val value: Boolean) : FinancialOnboardingIntent
    data object Create : FinancialOnboardingIntent
    data object Recover : FinancialOnboardingIntent
    data class Open(val documentId: String) : FinancialOnboardingIntent
    data class ChooseFile(val documentId: String) : FinancialOnboardingIntent
    data object DiscardFile : FinancialOnboardingIntent
    data object Upload : FinancialOnboardingIntent
    data object OpenFailed : FinancialOnboardingIntent
}
sealed interface FinancialOnboardingEffect {
    val generation: Int
    data class Open(val documentId: String, val url: String, override val generation: Int) : FinancialOnboardingEffect
    data class Changed(override val generation: Int) : FinancialOnboardingEffect
}
