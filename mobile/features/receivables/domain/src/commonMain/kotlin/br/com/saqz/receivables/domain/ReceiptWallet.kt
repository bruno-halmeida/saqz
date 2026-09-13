package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class WalletError : SaqzError { INVALID, DENIED, RECENT_AUTHENTICATION, INSUFFICIENT_BALANCE, CONFLICT,
    UNCERTAIN, NETWORK, UNAVAILABLE, SIGNED_OUT }
data class ReceiptWalletBalance(val accountId: String, val availableBalanceCents: Long,
    val pendingReceivablesCents: Long, val refreshedAt: String)
data class ReceiptWalletEntry(val id: String, val kind: String, val amountCents: Long, val balanceCents: Long,
    val occurredOn: String, val description: String)
data class ReceiptWalletStatement(val items: List<ReceiptWalletEntry>, val nextCursor: String?)
data class ReceiptBankDestination(val id: String, val bankCode: String, val accountType: String, val ownerName: String,
    val cpfCnpjSuffix: String, val agencySuffix: String, val accountSuffix: String, val verified: Boolean)
/** Transient bank data: never put this object in navigation, saved state, logs or local storage. */
class ReceiptBankDetails(val bankCode: String, val accountType: String, val ownerName: String, val cpfCnpj: String,
    val agency: String, val account: String, val accountDigit: String) {
    fun valid() = bankCode.matches(Regex("[0-9]{3}")) && accountType in setOf("CHECKING", "SAVINGS") &&
        ownerName.isNotBlank() && cpfCnpj.matches(Regex("[0-9]{11}|[0-9]{14}")) &&
        agency.matches(Regex("[0-9]{1,6}")) && account.matches(Regex("[0-9]{1,20}")) &&
        accountDigit.matches(Regex("[0-9A-Za-z]{1,2}"))
    override fun toString() = "ReceiptBankDetails(redacted)"
}
data class ReceiptWithdrawal(val id: String, val requestId: String, val destinationId: String, val amountCents: Long,
    val feeCents: Long, val status: String, val providerTransferId: String?) {
    val pending get() = status in setOf("REQUESTED", "UNKNOWN", "PROCESSING")
}
interface ReceiptWalletGateway {
    suspend fun balance(accountId: String): SaqzResult<ReceiptWalletBalance, WalletError>
    suspend fun statement(accountId: String, cursor: String?): SaqzResult<ReceiptWalletStatement, WalletError>
    suspend fun destinations(accountId: String): SaqzResult<List<ReceiptBankDestination>, WalletError>
    suspend fun saveDestination(accountId: String, requestId: String, details: ReceiptBankDetails):
        SaqzResult<ReceiptBankDestination, WalletError>
    suspend fun recoverDestination(accountId: String, requestId: String): SaqzResult<ReceiptBankDestination, WalletError>
    suspend fun withdraw(accountId: String, requestId: String, destinationId: String, amountCents: Long):
        SaqzResult<ReceiptWithdrawal, WalletError>
    suspend fun recoverWithdrawal(accountId: String, requestId: String): SaqzResult<ReceiptWithdrawal, WalletError>
}
