package br.com.saqz.receivables.presentation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.receivables.domain.*
import kotlinx.serialization.Serializable

@Serializable data object ReceiptWalletRoute : NavKey
@Serializable data class WalletAttempt(val actor: String, val accountId: String, val requestId: String,
    val kind: String, val amountCents: Long? = null, val destinationId: String? = null)
enum class WalletBankField { BANK, NAME, DOCUMENT, AGENCY, ACCOUNT, DIGIT }
data class WalletBankForm(val fields: Map<WalletBankField, String> = emptyMap(), val savings: Boolean = false) {
    operator fun get(field: WalletBankField) = fields[field].orEmpty()
    fun details() = ReceiptBankDetails(this[WalletBankField.BANK], if (savings) "SAVINGS" else "CHECKING",
        this[WalletBankField.NAME], this[WalletBankField.DOCUMENT], this[WalletBankField.AGENCY],
        this[WalletBankField.ACCOUNT], this[WalletBankField.DIGIT])
    override fun toString() = "WalletBankForm(redacted)"
}
data class ReceiptWalletState(val loading: Boolean = true, val accounts: List<ReceiptAccount> = emptyList(),
    val accountId: String? = null, val balance: ReceiptWalletBalance? = null,
    val entries: List<ReceiptWalletEntry> = emptyList(), val nextCursor: String? = null,
    val destinations: List<ReceiptBankDestination> = emptyList(), val destinationId: String? = null,
    val bankForm: WalletBankForm = WalletBankForm(), val editingBank: Boolean = false,
    val amount: String = "", val accepted: Boolean = false, val password: String = "", val authenticating: Boolean = false,
    val attempt: WalletAttempt? = null, val withdrawal: ReceiptWithdrawal? = null, val error: WalletError? = null) {
    val idle get() = !loading && !authenticating && error != WalletError.SIGNED_OUT
    val canEdit get() = idle && attempt == null && error != WalletError.DENIED
    val amountCents get() = incomeCents(amount)?.takeIf { it > 0 }
    val canWithdraw get() = canEdit && error == null && accepted && destinationId != null && amountCents != null &&
        balance != null && amountCents!! <= balance.availableBalanceCents
    override fun toString() = "ReceiptWalletState(redacted)"
}
sealed interface ReceiptWalletIntent {
    data object Refresh : ReceiptWalletIntent
    data class Account(val id: String) : ReceiptWalletIntent
    data object More : ReceiptWalletIntent
    data class Destination(val id: String) : ReceiptWalletIntent
    data class Amount(val value: String) : ReceiptWalletIntent
    data class Accept(val value: Boolean) : ReceiptWalletIntent
    data class EditBank(val value: Boolean) : ReceiptWalletIntent
    data class BankField(val field: WalletBankField, val value: String) : ReceiptWalletIntent
    data class Savings(val value: Boolean) : ReceiptWalletIntent
    data object SaveBank : ReceiptWalletIntent
    data object Withdraw : ReceiptWalletIntent
    data class Password(val value: String) : ReceiptWalletIntent
    data object AuthenticatePassword : ReceiptWalletIntent
    data object AuthenticateGoogle : ReceiptWalletIntent
}
