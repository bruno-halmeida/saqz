package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzResult

data class OwnedReceiptAccount(val id: String, val ownerId: String, val registration: AccountRegistration,
    val operationsEnabled: Boolean)
data class ReceiptDocument(val id: String, val type: String, val status: String, val onboardingUrl: String?,
    val description: String? = null) {
    val needsSubmission get() = status in setOf("PENDING", "REJECTED")
}

/** Legal data remains in memory; no generated toString, serialization or persisted draft. */
class ReceiptLegalAddress(val street: String, val number: String, val province: String, val postalCode: String) {
    fun valid() = street.isNotBlank() && number.isNotBlank() && province.isNotBlank() && postalCode.matches(Regex("[0-9]{8}"))
}
data class ReceiptLegalRegistration(val name: String, val email: String, val cpfCnpj: String, val mobilePhone: String,
    val incomeCents: Long, val address: ReceiptLegalAddress, val birthDate: String?, val companyType: String?) {
    override fun toString() = "ReceiptLegalRegistration(redacted)"
    fun valid() = name.isNotBlank() && email.contains('@') && cpfCnpj.matches(Regex("[0-9]{11}|[0-9]{14}")) &&
        mobilePhone.matches(Regex("[0-9]{10,13}")) && incomeCents >= 0 && address.valid() &&
        if (cpfCnpj.length == 11) birthDate?.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) == true && companyType == null
        else birthDate == null && companyType in setOf("MEI", "LIMITED", "INDIVIDUAL", "ASSOCIATION")
}
class ReceiptRegistrationCommand(val requestId: String, val termsVersion: String, val accepted: Boolean,
    val registration: ReceiptLegalRegistration)
class ReceiptDocumentFile(val bytes: ByteArray, val contentType: String) {
    fun valid() = bytes.isNotEmpty() && bytes.size <= MAX_DOCUMENT_BYTES && contentType in DOCUMENT_TYPES
    companion object {
        const val MAX_DOCUMENT_BYTES = 5 * 1024 * 1024
        val DOCUMENT_TYPES = setOf("application/pdf", "image/jpeg", "image/png")
    }
}
interface FinancialOnboardingGateway {
    suspend fun mine(ownerId: String): SaqzResult<OwnedReceiptAccount?, ReceiptError>
    suspend fun currentTerms(): SaqzResult<ReceiptTerms, ReceiptError>
    suspend fun create(ownerId: String, command: ReceiptRegistrationCommand): SaqzResult<OwnedReceiptAccount, ReceiptError>
    suspend fun recover(ownerId: String, requestId: String): SaqzResult<OwnedReceiptAccount, ReceiptError>
    suspend fun documents(): SaqzResult<List<ReceiptDocument>, ReceiptError>
    suspend fun upload(document: ReceiptDocument, requestId: String, file: ReceiptDocumentFile): SaqzResult<Unit, ReceiptError>
}
