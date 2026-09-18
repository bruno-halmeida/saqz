package br.com.saqz.receivables.presentation

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ReceiptConfigurationViewModel(
    private val groupId: String,
    private val gateway: GroupReceivablesGateway,
    private val availability: ReceivablesAvailabilityGateway,
    private val session: ReceivablesSessionContext,
    private val saved: SavedStateHandle,
    private val identity: ReceivablesRecoveryIdentity,
) : MviViewModel<ReceiptConfigurationState, ReceiptConfigurationIntent, Nothing>(ReceiptConfigurationState()) {
    private val sessionKey = session.currentKey()
    private val actorId = identity.currentUserId()
    private var generation = 0L
    private var activation: ReceiptCommand? = null
    private var deactivation: ReceiptCommand? = null

    init {
        if (actorId != null && saved.get<String>("receipt.user") == actorId && saved.get<String>("receipt.request") != null) {
            val command = ReceiptCommand(saved.get<String>("receipt.request")!!, saved.get<String>("receipt.account")!!,
                saved.get<List<String>>("receipt.methods").orEmpty().map { ReceiptMethod.valueOf(it) }.toSet(),
                saved["receipt.fingerprint"], saved.get<Boolean>("receipt.activate") == true)
            if (command.accepted) activation = command else deactivation = command
            update { ReceiptConfigurationState(loading = false, accountId = command.accountId, pendingMutation = true) }
        } else { clearPending(); load() }
    }

    override fun handleIntent(intent: ReceiptConfigurationIntent) {
        if (!validSession()) return
        val current = state.value
        if (current.loading) return
        if (handlePending(intent)) return
        when (intent) {
            ReceiptConfigurationIntent.RetryMutation -> Unit
            ReceiptConfigurationIntent.Refresh -> load()
            is ReceiptConfigurationIntent.SelectAccount -> selectAccount(intent.id)
            is ReceiptConfigurationIntent.ToggleMethod -> {
                activation = null
                update { it.copy(methods = toggled(it.methods, intent.method),
                    review = null, terms = emptyList(), accepted = false, error = null, completed = false) }
            }
            ReceiptConfigurationIntent.Preview -> if (current.canPreview) preview()
            is ReceiptConfigurationIntent.Accept -> if (current.canAccept) update { it.copy(accepted = intent.accepted) }
            ReceiptConfigurationIntent.Activate -> if (current.canActivate) mutate(activate = true)
            ReceiptConfigurationIntent.RequestDeactivation -> if (current.canDeactivate) update { it.copy(confirmingDeactivation = true) }
            ReceiptConfigurationIntent.DismissDeactivation -> update { it.copy(confirmingDeactivation = false) }
            ReceiptConfigurationIntent.Deactivate -> if (canConfirmDeactivation()) mutate(activate = false)
        }
    }

    private fun canConfirmDeactivation() = state.value.canDeactivate && state.value.confirmingDeactivation

    private fun selectAccount(id: String) {
        if (!canSelectAccount(id)) return
        activation = null
        deactivation = null
        update { it.copy(accountId = id, status = null, methods = emptySet(), review = null,
            terms = emptyList(), accepted = false, discoveryAvailable = false, completed = false, confirmingDeactivation = false) }
        loadStatus(id)
    }

    private fun handlePending(intent: ReceiptConfigurationIntent): Boolean {
        if (!state.value.pendingMutation) return false
        if (intent == ReceiptConfigurationIntent.RetryMutation) mutate(activate = activation != null)
        return true
    }

    private fun toggled(methods: Set<ReceiptMethod>, method: ReceiptMethod) =
        if (method in methods) methods - method else methods + method

    private fun canSelectAccount(id: String) = state.value.accountId != id && state.value.accounts.any { it.id == id }

    private fun load() = launchRequest {
        activation = null
        deactivation = null
        update { ReceiptConfigurationState(loading = true) }
        when (val result = gateway.accounts()) {
            is SaqzResult.Failure -> fail(result.error)
            is SaqzResult.Success -> if (validSession()) update { it.copy(accounts = result.value) }
        }
    }

    private fun loadStatus(accountId: String) = launchRequest {
        when (val result = gateway.status(groupId, accountId)) {
            is SaqzResult.Failure -> fail(result.error)
            is SaqzResult.Success -> if (validSession()) {
                if (result.value.state.accountId != accountId || result.value.state.groupId != groupId) fail(ReceiptError.INVALID)
                else update { it.copy(status = result.value) }
            }
        }
        refreshAvailability(accountId, generation)
    }

    private fun refreshAvailability(accountId: String, expected: Long) {
        viewModelScope.launch {
            if (!validSession()) return@launch
            val result = availability.get()
            if (validSession() && generation == expected && state.value.accountId == accountId) {
                update { it.copy(discoveryAvailable = (result as? SaqzResult.Success)?.value?.newJourneysAvailable == true) }
            }
        }
    }

    private fun preview() = launchRequest {
        activation = null
        update { it.copy(review = null, terms = emptyList(), accepted = false) }
        val current = state.value
        val account = current.accountId ?: return@launchRequest
        when (val result = gateway.preview(groupId, ReceiptCommand(Uuid.random().toString(), account, current.methods))) {
            is SaqzResult.Failure -> fail(result.error)
            is SaqzResult.Success -> {
                val review = result.value
                if (!validSession()) return@launchRequest
                if (review.state.accountId != account || review.state.groupId != groupId ||
                    review.schedules.map { it.method }.toSet() != current.methods ||
                    !review.fingerprint.matches(Regex("[a-f0-9]{64}"))) {
                    fail(ReceiptError.INVALID)
                    return@launchRequest
                }
                update { it.copy(review = review) }
                val documents = mutableListOf<ReceiptTerms>()
                for (version in review.schedules.map { it.termsVersion }.distinct()) {
                    when (val terms = gateway.terms(version)) {
                        is SaqzResult.Failure -> { fail(terms.error); return@launchRequest }
                        is SaqzResult.Success -> {
                            if (!validSession()) return@launchRequest
                            if (terms.value.version != version || terms.value.content.isBlank()) {
                                fail(ReceiptError.INVALID)
                                return@launchRequest
                            }
                            documents.add(terms.value)
                        }
                    }
                }
                update { it.copy(terms = documents) }
            }
        }
    }

    private fun mutate(activate: Boolean) = launchRequest {
        val current = state.value
        val account = current.accountId ?: return@launchRequest
        val command = if (activate) {
            activation ?: ReceiptCommand(Uuid.random().toString(), account, current.methods,
                current.review?.fingerprint, true).also { activation = it }
        } else deactivation ?: ReceiptCommand(Uuid.random().toString(), account).also { deactivation = it }
        if (activate && !current.pendingMutation) {
            val available = availability.get()
            if (!validSession()) return@launchRequest
            if ((available as? SaqzResult.Success)?.value?.newJourneysAvailable != true) {
                update { it.copy(discoveryAvailable = false) }
                activation = null
                fail(ReceiptError.DENIED)
                return@launchRequest
            }
        }
        persistPending(command, activate)
        update { it.copy(pendingMutation = true) }
        val result = if (activate) gateway.activate(groupId, command) else gateway.deactivate(groupId, command)
        if (!validSession()) return@launchRequest
        when (result) {
            is SaqzResult.Success -> {
                if (result.value.accountId != account || result.value.groupId != groupId) {
                    update { it.copy(pendingMutation = true) }
                    fail(ReceiptError.UNCERTAIN)
                    return@launchRequest
                }
                activation = null
                deactivation = null
                clearPending()
                update { it.copy(status = it.status?.copy(state = result.value) ?: ReceiptStatus(result.value, emptyMap()),
                    review = null,
                    terms = emptyList(), accepted = false, completed = true, pendingMutation = false, confirmingDeactivation = false) }
                if (current.accounts.isEmpty()) reloadContext(account)
            }
            is SaqzResult.Failure -> mutationFailed(result.error)
        }
    }

    private suspend fun reloadContext(account: String) {
        when (val result = gateway.accounts()) {
            is SaqzResult.Success -> if (validSession()) update { it.copy(accounts = result.value) }
            is SaqzResult.Failure -> fail(result.error)
        }
        if (!validSession()) return
        when (val result = gateway.status(groupId, account)) {
            is SaqzResult.Success -> if (validSession()) update { it.copy(status = result.value) }
            is SaqzResult.Failure -> fail(result.error)
        }
        refreshAvailability(account, generation)
    }

    override fun onCleared() {
        if (session.currentKey() != sessionKey) clearPending()
        super.onCleared()
    }

    private fun mutationFailed(error: ReceiptError) {
        val uncertain = error in setOf(ReceiptError.NETWORK, ReceiptError.UNAVAILABLE, ReceiptError.UNCERTAIN)
        update { it.copy(pendingMutation = uncertain) }
        if (!uncertain) { activation = null; deactivation = null; clearPending() }
        if (error == ReceiptError.STALE) update { it.copy(review = null, terms = emptyList(), accepted = false) }
        fail(error)
    }

    private fun launchRequest(block: suspend () -> Unit) {
        if (!validSession()) return
        val expected = ++generation
        update { it.copy(loading = true, error = null, completed = false) }
        viewModelScope.launch {
            block()
            if (validSession() && expected == generation) update { it.copy(loading = false) }
        }
    }

    private fun validSession(): Boolean {
        if (sessionKey != null && sessionKey == session.currentKey()) return true
        generation++
        clearPending()
        activation = null
        deactivation = null
        update { ReceiptConfigurationState(loading = false, error = ReceiptError.SIGNED_OUT) }
        return false
    }
    private fun persistPending(command: ReceiptCommand, activate: Boolean) {
        saved["receipt.user"] = actorId
        saved["receipt.request"] = command.requestId
        saved["receipt.account"] = command.accountId
        saved["receipt.methods"] = command.methods.map { it.name }
        saved["receipt.fingerprint"] = command.fingerprint
        saved["receipt.activate"] = activate
    }
    private fun clearPending() {
        saved.keys().filter { it.startsWith("receipt.") }.forEach { saved.remove<Any?>(it) }
    }
    private fun fail(error: ReceiptError) { if (validSession()) update { it.copy(error = error) } }
}
