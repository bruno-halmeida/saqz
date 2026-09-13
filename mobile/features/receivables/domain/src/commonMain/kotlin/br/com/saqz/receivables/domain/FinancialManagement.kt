package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzResult

enum class ReceiptManagementRole { OWNER, DELEGATE }
data class ManagedReceiptAccount(val id: String, val ownerId: String, val registration: AccountRegistration,
    val operationsEnabled: Boolean)
data class ReceiptAdministrator(val userId: String, val displayName: String, val groupNames: List<String>)
data class ReceiptDelegation(val accountId: String, val userId: String, val revoked: Boolean)

/** The legal name/document/type and financial ownership are intentionally not representable here. */
data class ReceiptRegistrationCorrection(
    val email: String, val phone: String?, val mobilePhone: String, val site: String?, val incomeCents: Long,
    val postalCode: String, val address: String, val addressNumber: String, val complement: String?, val province: String,
) {
    fun valid() = email.length in 3..200 && email.contains('@') && phone?.matches(Regex("[0-9]{10,13}")) != false &&
        mobilePhone.matches(Regex("[0-9]{10,13}")) &&
        site?.let { it.length <= 200 && (it.startsWith("https://") || it.startsWith("http://")) } != false &&

        incomeCents >= 0 && postalCode.matches(Regex("[0-9]{8}")) && address.isNotBlank() && address.length <= 200 &&
        addressNumber.isNotBlank() && addressNumber.length <= 30 && complement?.length?.let { it <= 100 } != false &&
        province.isNotBlank() && province.length <= 100
    override fun toString() = "ReceiptRegistrationCorrection(redacted)"
}
data class ReceiptManagementView(val accountId: String, val role: ReceiptManagementRole,
    val correction: ReceiptRegistrationCorrection)
class ReceiptCorrectionCommand(val requestId: String, val correction: ReceiptRegistrationCorrection)

interface FinancialManagementGateway {
    suspend fun candidates(accountId: String): SaqzResult<List<ReceiptAdministrator>, ReceiptError>
    suspend fun accounts(): SaqzResult<List<ManagedReceiptAccount>, ReceiptError>
    suspend fun management(accountId: String): SaqzResult<ReceiptManagementView, ReceiptError>
    suspend fun correct(accountId: String, command: ReceiptCorrectionCommand): SaqzResult<Unit, ReceiptError>
    suspend fun recover(accountId: String, requestId: String): SaqzResult<Unit, ReceiptError>
    suspend fun delegations(accountId: String): SaqzResult<List<ReceiptDelegation>, ReceiptError>
    suspend fun terms(): SaqzResult<ReceiptTerms, ReceiptError>
    suspend fun grant(accountId: String, userId: String, termsVersion: String, requestId: String): SaqzResult<Unit, ReceiptError>
    suspend fun revoke(accountId: String, userId: String, requestId: String): SaqzResult<Unit, ReceiptError>
}
