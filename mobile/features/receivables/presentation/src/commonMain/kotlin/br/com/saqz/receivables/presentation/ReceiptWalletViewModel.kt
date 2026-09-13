package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.domain.port.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ReceiptWalletViewModel(private val gateway: ReceiptWalletGateway, private val directory: ReceiptAccountDirectory,
    private val session: ReceivablesSessionContext, private val identity: ReceivablesRecoveryIdentity,
    private val auth: ReceiptReauthenticationPort, private val saved: SavedStateHandle) :
    MviViewModel<ReceiptWalletState, ReceiptWalletIntent, Unit>(ReceiptWalletState()) {
    private val actor = identity.currentUserId()
    private val key = session.currentKey()
    private var generation = 0
    init {
        val pending = saved.get<String>(ATTEMPT)?.let { runCatching { Json.decodeFromString<WalletAttempt>(it) }.getOrNull() }
        if (pending != null && pending.actor == actor && pending.kind in setOf("BANK", "WITHDRAW")) {
            update { it.copy(attempt = pending, accountId = pending.accountId) }
        } else saved.remove<String>(ATTEMPT)
        refresh()
    }
    override fun onIntent(intent: ReceiptWalletIntent) {
        if (!validSession() || !state.value.idle) return
        when (intent) {
            ReceiptWalletIntent.Refresh -> refresh()
            ReceiptWalletIntent.More -> loadMore()
            ReceiptWalletIntent.SaveBank -> saveBank()
            ReceiptWalletIntent.Withdraw -> withdraw()
            ReceiptWalletIntent.AuthenticatePassword -> authenticate(false)
            ReceiptWalletIntent.AuthenticateGoogle -> authenticate(true)
            else -> edit(intent)
        }
    }
    private fun edit(intent: ReceiptWalletIntent) {
        if (!state.value.canEdit) return
        update { s -> when (intent) {
            is ReceiptWalletIntent.Account -> if (s.accounts.any { it.id == intent.id })
                ReceiptWalletState(loading = false, accounts = s.accounts, accountId = intent.id) else s
            is ReceiptWalletIntent.Destination -> if (s.destinations.any { it.id == intent.id })
                s.copy(destinationId = intent.id, accepted = false) else s
            is ReceiptWalletIntent.Amount -> s.copy(amount = intent.value.take(18), accepted = false)
            is ReceiptWalletIntent.Accept -> s.copy(accepted = intent.value)
            is ReceiptWalletIntent.EditBank -> s.copy(editingBank = intent.value, bankForm = WalletBankForm(), accepted = false)
            is ReceiptWalletIntent.BankField -> s.copy(bankForm = s.bankForm.copy(
                fields = s.bankForm.fields + (intent.field to intent.value.take(200))))
            is ReceiptWalletIntent.Savings -> s.copy(bankForm = s.bankForm.copy(savings = intent.value))
            is ReceiptWalletIntent.Password -> s.copy(password = intent.value.take(512))
            else -> s
        } }
        if (intent is ReceiptWalletIntent.Account) refresh()
    }
    private fun refresh() {
        if (!validSession()) return
        val expected = ++generation
        update { it.copy(loading = true, error = null, accepted = false, password = "") }
        viewModelScope.launch {
            val accounts = directory.accounts()
            if (!current(expected)) return@launch
            if (accounts is SaqzResult.Failure) {
                failed(if (accounts.error == ReceiptError.SIGNED_OUT) WalletError.SIGNED_OUT else WalletError.UNAVAILABLE)
                return@launch
            }
            val list = (accounts as SaqzResult.Success).value
            val account = state.value.accountId?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
            if (state.value.attempt != null && account != state.value.attempt?.accountId) {
                failed(WalletError.DENIED); return@launch
            }
            update { it.copy(accounts = list, accountId = account) }
            if (account == null) { update { ReceiptWalletState(loading = false) }; return@launch }
            load(account, expected)
        }
    }
    private suspend fun load(account: String, expected: Int) {
        val balance = accepted(gateway.balance(account), expected) ?: return
        val banks = accepted(gateway.destinations(account), expected) ?: return
        val page = accepted(gateway.statement(account, null), expected) ?: return
        update { it.copy(balance = balance, entries = page.items, nextCursor = page.nextCursor,
            destinations = banks, destinationId = it.destinationId?.takeIf { id -> banks.any { b -> b.id == id } }, loading = false) }
        recover(expected)
    }
    private fun <T> accepted(result: SaqzResult<T, WalletError>, expected: Int): T? {
        if (!current(expected)) return null
        return when (result) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> { failed(result.error); null }
        }
    }
    private fun loadMore() {
        val s = state.value; val cursor = s.nextCursor ?: return; val account = s.accountId ?: return
        val expected = ++generation
        update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = gateway.statement(account, cursor)
            if (!current(expected)) return@launch
            when (result) {
                is SaqzResult.Failure -> failed(result.error)
                is SaqzResult.Success -> update { it.copy(loading = false,
                    entries = (it.entries + result.value.items).distinctBy { e -> e.id }, nextCursor = result.value.nextCursor) }
            }
        }
    }
    private fun saveBank() {
        val s = state.value; val account = s.accountId ?: return
        if (!s.canEdit || !s.editingBank || !s.bankForm.details().valid()) return
        val details = s.bankForm.details()
        val marker = WalletAttempt(actor!!, account, Uuid.random().toString(), "BANK")
        save(marker); val expected = ++generation
        update { it.copy(loading = true, error = null, password = "") }
        viewModelScope.launch {
            val result = gateway.saveDestination(account, marker.requestId, details)
            if (!current(expected)) return@launch
            when (result) {
                is SaqzResult.Failure -> writeFailed(result.error)
                is SaqzResult.Success -> {
                    clearAttempt()
                    update { it.copy(loading = false, editingBank = false, bankForm = WalletBankForm(),
                        destinations = (it.destinations + result.value).distinctBy { d -> d.id }, destinationId = result.value.id) }
                }
            }
        }
    }
    private fun withdraw() {
        val s = state.value; val account = s.accountId ?: return
        if (!s.canWithdraw) return
        val marker = WalletAttempt(actor!!, account, Uuid.random().toString(), "WITHDRAW", s.amountCents, s.destinationId)
        save(marker); val expected = ++generation
        update { it.copy(loading = true, accepted = false, password = "", error = null) }
        viewModelScope.launch {
            val result = gateway.withdraw(account, marker.requestId, marker.destinationId!!, marker.amountCents!!)
            if (!current(expected)) return@launch
            when (result) {
                is SaqzResult.Failure -> writeFailed(result.error)
                is SaqzResult.Success -> acceptWithdrawal(result.value)
            }
        }
    }
    private suspend fun recover(expected: Int) {
        val attempt = state.value.attempt ?: return
        update { it.copy(loading = true) }
        if (attempt.kind == "WITHDRAW") {
            val result = gateway.recoverWithdrawal(attempt.accountId, attempt.requestId)
            if (!current(expected)) return
            when (result) {
                is SaqzResult.Failure -> failed(result.error)
                is SaqzResult.Success -> if (result.value.amountCents == attempt.amountCents &&
                    result.value.destinationId == attempt.destinationId) acceptWithdrawal(result.value) else failed(WalletError.INVALID)
            }
        } else {
            val result = gateway.recoverDestination(attempt.accountId, attempt.requestId)
            if (!current(expected)) return
            when (result) {
                is SaqzResult.Failure -> failed(result.error)
                is SaqzResult.Success -> {
                    clearAttempt(); update { it.copy(loading = false, editingBank = false, bankForm = WalletBankForm(),
                        destinations = (it.destinations + result.value).distinctBy { d -> d.id }, destinationId = result.value.id) }
                }
            }
        }
    }
    private fun authenticate(google: Boolean) {
        if (!state.value.canEdit || !google && state.value.password.isBlank()) return
        val password = state.value.password; val expected = ++generation
        update { it.copy(authenticating = true, password = "", error = null, accepted = false) }
        val callback = ReceiptReauthenticationCallback { result -> viewModelScope.launch {
            if (current(expected)) update { it.copy(authenticating = false, error = when (result) {
                ReceiptReauthenticationResult.AUTHENTICATED, ReceiptReauthenticationResult.CANCELLED -> null
                ReceiptReauthenticationResult.INVALID_CREDENTIALS -> WalletError.RECENT_AUTHENTICATION
                ReceiptReauthenticationResult.UNAVAILABLE -> WalletError.UNAVAILABLE
            }) }
        } }
        if (google) auth.google(callback) else auth.password(password, callback)
    }
    private fun acceptWithdrawal(value: ReceiptWithdrawal) {
        if (!value.pending) clearAttempt()
        update { it.copy(loading = false, withdrawal = value, amount = "", accepted = false) }
    }
    private fun save(value: WalletAttempt) { saved[ATTEMPT] = Json.encodeToString(value); update { it.copy(attempt = value) } }
    private fun clearAttempt() { saved.remove<String>(ATTEMPT); update { it.copy(attempt = null) } }
    private fun writeFailed(error: WalletError) {
        if (error !in setOf(WalletError.UNCERTAIN, WalletError.NETWORK, WalletError.UNAVAILABLE)) clearAttempt()
        failed(error)
    }
    private fun failed(error: WalletError) {
        if (error == WalletError.SIGNED_OUT) clearSession()
        else if (error == WalletError.DENIED) update { it.copy(loading = false, authenticating = false, error = error,
            balance = null, destinations = emptyList(), entries = emptyList(), nextCursor = null,
            bankForm = WalletBankForm(), password = "", amount = "", accepted = false, editingBank = false) }
        else update { it.copy(loading = false, authenticating = false, error = error) }
    }
    private fun current(expected: Int) = validSession() && generation == expected
    fun validSession(): Boolean {
        if (actor != null && key != null && actor == identity.currentUserId() && key == session.currentKey()) return true
        clearSession(); return false
    }
    private fun clearSession() {
        generation++
        clearAttempt()
        update { ReceiptWalletState(loading = false, error = WalletError.SIGNED_OUT) }
    }
    private companion object { const val ATTEMPT = "wallet.attempt" }
}
