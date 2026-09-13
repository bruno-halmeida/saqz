package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable data object FinancialManagementRoute : NavKey
@Serializable data class FinancialManagementAttempt(val actor: String, val accountId: String,
    val requestId: String, val kind: String, val targetId: String? = null, val termsVersion: String? = null)
enum class ManagementField { EMAIL, PHONE, MOBILE_PHONE, SITE, INCOME, POSTAL_CODE, ADDRESS, ADDRESS_NUMBER, COMPLEMENT, PROVINCE }
data class FinancialManagementForm(val values: Map<ManagementField, String> = emptyMap()) {
    operator fun get(field: ManagementField) = values[field].orEmpty()
    override fun toString() = "FinancialManagementForm(redacted)"
    fun correction(): ReceiptRegistrationCorrection? {
        val value = ReceiptRegistrationCorrection(get(ManagementField.EMAIL), get(ManagementField.PHONE).ifBlank { null },
            get(ManagementField.MOBILE_PHONE), get(ManagementField.SITE).ifBlank { null },
            incomeCents(get(ManagementField.INCOME)) ?: return null, get(ManagementField.POSTAL_CODE).filter(Char::isDigit),
            get(ManagementField.ADDRESS), get(ManagementField.ADDRESS_NUMBER),
            get(ManagementField.COMPLEMENT).ifBlank { null }, get(ManagementField.PROVINCE))
        return value.takeIf(ReceiptRegistrationCorrection::valid)
    }
}
data class FinancialManagementState(
    val loading: Boolean = true,
    val actorId: String? = null,
    val accounts: List<ManagedReceiptAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val role: ReceiptManagementRole? = null,
    val form: FinancialManagementForm = FinancialManagementForm(),
    val candidates: List<ReceiptAdministrator> = emptyList(),
    val delegations: List<ReceiptDelegation> = emptyList(),
    val terms: ReceiptTerms? = null,
    val delegateUserId: String = "",
    val acceptedDelegation: Boolean = false,
    val municipalityWarningAccepted: Boolean = false,
    val attempt: FinancialManagementAttempt? = null,
    val completedRequestId: String? = null,
    val error: ReceiptError? = null,
) {
    val pending get() = attempt != null
    val canCorrect get() = role != null && error == null && !loading && !pending && form.correction() != null && municipalityWarningAccepted
    val canGrant get() = role == ReceiptManagementRole.OWNER && !loading && error == null && !pending &&
        candidates.any { it.userId == delegateUserId } &&
        acceptedDelegation && terms?.version?.isNotBlank() == true
}
sealed interface FinancialManagementIntent {
    data object Refresh : FinancialManagementIntent
    data class SelectAccount(val accountId: String) : FinancialManagementIntent
    data class Edit(val field: ManagementField, val value: String) : FinancialManagementIntent
    data class AcceptMunicipalityWarning(val value: Boolean) : FinancialManagementIntent
    data object Correct : FinancialManagementIntent
    data object Recover : FinancialManagementIntent
    data class DelegateUser(val userId: String) : FinancialManagementIntent
    data class AcceptDelegation(val value: Boolean) : FinancialManagementIntent
    data object Grant : FinancialManagementIntent
    data class Revoke(val userId: String) : FinancialManagementIntent
}
sealed interface FinancialManagementEffect {
    val generation: Int
    data class Changed(val requestId: String, override val generation: Int) : FinancialManagementEffect
}
