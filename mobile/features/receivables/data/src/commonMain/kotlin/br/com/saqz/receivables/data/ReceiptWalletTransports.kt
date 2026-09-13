package br.com.saqz.receivables.data

import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable internal data class WalletBalanceTransport(val accountId: String, val availableBalanceCents: Long,
    val pendingReceivablesCents: Long, val refreshedAt: String)
@Serializable internal data class WalletEntryTransport(val id: String, val kind: String, val amountCents: Long,
    val balanceCents: Long, val occurredOn: String, val description: String)
@Serializable internal data class WalletStatementTransport(val items: List<WalletEntryTransport>, val nextCursor: String? = null)
@Serializable internal data class WalletBankTransport(val id: String, val bankCode: String, val accountType: String,
    val ownerName: String, val cpfCnpjSuffix: String, val agencySuffix: String, val accountSuffix: String, val verified: Boolean) {
    fun domain(): ReceiptBankDestination {
        require(id.isNotBlank() && bankCode.isNotBlank() && accountType in setOf("CHECKING", "SAVINGS"))
        require(cpfCnpjSuffix.length <= 4 && agencySuffix.length <= 4 && accountSuffix.length <= 4)
        return ReceiptBankDestination(id, bankCode, accountType, ownerName, cpfCnpjSuffix, agencySuffix, accountSuffix, verified)
    }
}
@Serializable internal data class WalletWithdrawalTransport(val id: String, val requestId: String, val destinationId: String,
    val amountCents: Long, val feeCents: Long, val status: String, val providerTransferId: String? = null) {
    fun domain(expectedRequest: String): ReceiptWithdrawal {
        require(id.isNotBlank() && requestId == expectedRequest && destinationId.isNotBlank() && amountCents > 0 && feeCents >= 0)
        require(status in setOf("REQUESTED", "UNKNOWN", "PROCESSING", "COMPLETED", "REJECTED", "CANCELLED"))
        return ReceiptWithdrawal(id, requestId, destinationId, amountCents, feeCents, status, providerTransferId)
    }
}
@Serializable internal data class WalletBankCommand(val requestId: String, val bankCode: String, val accountType: String,
    val ownerName: String, val cpfCnpj: String, val agency: String, val account: String, val accountDigit: String) {
    override fun toString() = "WalletBankCommand(redacted)"
}
@Serializable internal data class WalletWithdrawalCommand(val requestId: String, val destinationId: String,
    val amountCents: Long, val explicitlyAuthorized: Boolean)
