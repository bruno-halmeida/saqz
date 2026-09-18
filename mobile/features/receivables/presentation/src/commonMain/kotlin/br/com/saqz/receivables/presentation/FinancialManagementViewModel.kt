package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class FinancialManagementViewModel(
    private val gateway: FinancialManagementGateway,
    private val session: ReceivablesSessionContext,
    private val identity: ReceivablesRecoveryIdentity,
    private val saved: SavedStateHandle,
) : MviViewModel<FinancialManagementState, FinancialManagementIntent, FinancialManagementEffect>(FinancialManagementState()) {
    private val actor = identity.currentUserId()
    private val sessionKey = session.currentKey()
    private var generation = 0
    private var correction: ReceiptCorrectionCommand? = null

    init {
        val marker = saved.get<String>(ATTEMPT)?.let { runCatching { Json.decodeFromString<FinancialManagementAttempt>(it) }.getOrNull() }
        if (marker != null && marker.actor == actor && marker.kind in setOf("CORRECT", "GRANT", "REVOKE")) {
            update { it.copy(actorId = actor, attempt = marker, selectedAccountId = marker.accountId) }
        } else saved.remove<String>(ATTEMPT)
        refresh()
    }

    override fun handleIntent(intent: FinancialManagementIntent) {
        if (!validSession()) return
        if (intent == FinancialManagementIntent.Refresh) { refresh(); return }
        if (state.value.loading) return
        when (intent) {
            FinancialManagementIntent.Refresh -> Unit
            is FinancialManagementIntent.SelectAccount -> select(intent.accountId)
            is FinancialManagementIntent.Edit -> if (!state.value.pending) update { it.copy(form = it.form.copy(
                values = it.form.values + (intent.field to intent.value.take(200))), municipalityWarningAccepted = false,
                completedRequestId = null, error = null) }
            is FinancialManagementIntent.AcceptMunicipalityWarning -> if (!state.value.pending)
                update { it.copy(municipalityWarningAccepted = intent.value) }
            FinancialManagementIntent.Correct -> correct()
            FinancialManagementIntent.Recover -> recover()
            is FinancialManagementIntent.DelegateUser -> selectDelegate(intent.userId)
            is FinancialManagementIntent.AcceptDelegation -> if (!state.value.pending && state.value.role == ReceiptManagementRole.OWNER)
                update { it.copy(acceptedDelegation = intent.value) }
            FinancialManagementIntent.Grant -> grant()
            is FinancialManagementIntent.Revoke -> revoke(intent.userId)
        }
    }

    private fun selectDelegate(userId: String) {
        if (state.value.pending || state.value.candidates.none { it.userId == userId }) return
        update { it.copy(delegateUserId = userId, acceptedDelegation = false) }
    }

    private fun refresh() {
        if (!validSession()) return
        val expected = ++generation
        update { it.copy(loading = true, actorId = actor, error = null) }
        viewModelScope.launch {
            when (val result = gateway.accounts()) {
                is SaqzResult.Failure -> if (current(expected)) fail(result.error)
                is SaqzResult.Success -> if (current(expected)) {
                    val markerAccount = state.value.attempt?.accountId
                    if (markerAccount != null && result.value.none { it.id == markerAccount }) {
                        fail(ReceiptError.DENIED); return@launch
                    }
                    val selected = markerAccount?.takeIf { id -> result.value.any { it.id == id } }
                        ?: state.value.selectedAccountId?.takeIf { id -> result.value.any { it.id == id } }
                        ?: result.value.firstOrNull()?.id
                    update { it.copy(accounts = result.value, selectedAccountId = selected) }
                    if (selected == null) {
                        update { FinancialManagementState(loading = false, actorId = actor) }
                    } else load(selected, expected)
                }
            }
        }
    }

    private suspend fun load(accountId: String, expected: Int) {
        when (val view = gateway.management(accountId)) {
            is SaqzResult.Failure -> if (current(expected)) fail(view.error)
            is SaqzResult.Success -> if (current(expected)) {
                val c = view.value.correction
                update { it.copy(role = view.value.role, form = c.form(), municipalityWarningAccepted = false) }
                val delegations = accepted(gateway.delegations(accountId), expected) ?: return
                val owner = view.value.role == ReceiptManagementRole.OWNER
                val candidates = if (owner) accepted(gateway.candidates(accountId), expected) ?: return else emptyList()
                val terms = if (owner) accepted(gateway.terms(), expected) ?: return else null
                update { it.copy(loading = false, delegations = delegations, terms = terms,
                    candidates = candidates, delegateUserId = "", acceptedDelegation = false) }
            }
        }
    }

    private fun <T> accepted(result: SaqzResult<T, ReceiptError>, expected: Int): T? {
        if (!current(expected)) return null
        return when (result) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> { fail(result.error); null }
        }
    }

    private fun select(accountId: String) {
        if (state.value.pending || state.value.accounts.none { it.id == accountId }) return
        update { it.copy(selectedAccountId = accountId, completedRequestId = null) }
        refresh()
    }
    private fun correct() {
        val account = state.value.selectedAccountId ?: return
        val value = state.value.form.correction() ?: return
        if (!state.value.canCorrect) return
        val requestId = Uuid.random().toString()
        correction = ReceiptCorrectionCommand(requestId, value)
        save(FinancialManagementAttempt(actor!!, account, requestId, "CORRECT"))
        writeCorrection(account, correction!!)
    }
    private fun writeCorrection(account: String, command: ReceiptCorrectionCommand) = write(command.requestId) {
        gateway.correct(account, command)
    }
    private fun recover() {
        val attempt = state.value.attempt ?: return
        when (attempt.kind) {
            "CORRECT" -> write(attempt.requestId) { gateway.recover(attempt.accountId, attempt.requestId) }
            "GRANT" -> write(attempt.requestId) { gateway.grant(attempt.accountId, attempt.targetId!!,
                attempt.termsVersion!!, attempt.requestId) }
            "REVOKE" -> write(attempt.requestId) { gateway.revoke(attempt.accountId, attempt.targetId!!, attempt.requestId) }
        }
    }
    private fun grant() {
        val s = state.value
        if (!s.canGrant) return
        val requestId = Uuid.random().toString()
        val attempt = FinancialManagementAttempt(actor!!, s.selectedAccountId!!, requestId, "GRANT",
            s.delegateUserId, s.terms!!.version)
        save(attempt); write(requestId) { gateway.grant(attempt.accountId, attempt.targetId!!, attempt.termsVersion!!, requestId) }
    }
    private fun revoke(userId: String) {
        val s = state.value
        if (s.role != ReceiptManagementRole.OWNER || s.pending || s.delegations.none { it.userId == userId && !it.revoked }) return
        val requestId = Uuid.random().toString()
        val attempt = FinancialManagementAttempt(actor!!, s.selectedAccountId!!, requestId, "REVOKE", userId)
        save(attempt); write(requestId) { gateway.revoke(attempt.accountId, userId, requestId) }
    }
    private fun write(requestId: String, call: suspend () -> SaqzResult<Unit, ReceiptError>) {
        val expected = ++generation
        update { it.copy(loading = true, error = null) }
        viewModelScope.launch { when (val result = call()) {
            is SaqzResult.Failure -> if (current(expected)) {
                if (result.error !in setOf(ReceiptError.UNCERTAIN, ReceiptError.NETWORK, ReceiptError.UNAVAILABLE)) clearAttempt()
                fail(result.error)
            }
            is SaqzResult.Success -> if (current(expected)) {
                clearAttempt(); correction = null; update { it.copy(completedRequestId = requestId) }
                refresh(); emit(FinancialManagementEffect.Changed(requestId, generation))
            }
        } }
    }
    private fun save(attempt: FinancialManagementAttempt) {
        saved[ATTEMPT] = Json.encodeToString(attempt); update { it.copy(attempt = attempt) }
    }
    private fun clearAttempt() { saved.remove<String>(ATTEMPT); update { it.copy(attempt = null) } }
    private fun fail(error: ReceiptError) {
        when (error) {
            ReceiptError.SIGNED_OUT -> clearSession()
            ReceiptError.DENIED -> {
                correction = null
                update { FinancialManagementState(loading = false, actorId = actor, error = error,
                    attempt = it.attempt, selectedAccountId = it.attempt?.accountId) }
            }
            else -> update { it.copy(loading = false, error = error) }
        }
    }
    private fun current(expected: Int) = validSession() && expected == generation
    fun validEffect(effect: FinancialManagementEffect) = current(effect.generation) && effect is FinancialManagementEffect.Changed &&
        state.value.completedRequestId == effect.requestId
    fun validSession(): Boolean {
        if (actor != null && sessionKey != null && actor == identity.currentUserId() && sessionKey == session.currentKey()) return true
        clearSession(); return false
    }
    private fun clearSession() {
        generation++
        correction = null
        clearAttempt()
        update { FinancialManagementState(loading = false, error = ReceiptError.SIGNED_OUT) }
    }
    override fun onCleared() { correction = null; super.onCleared() }
    private fun ReceiptRegistrationCorrection.form() = FinancialManagementForm(mapOf(
        ManagementField.EMAIL to email, ManagementField.PHONE to phone.orEmpty(), ManagementField.MOBILE_PHONE to mobilePhone,
        ManagementField.SITE to site.orEmpty(), ManagementField.INCOME to brlInput(incomeCents), ManagementField.POSTAL_CODE to postalCode,
        ManagementField.ADDRESS to address, ManagementField.ADDRESS_NUMBER to addressNumber,
        ManagementField.COMPLEMENT to complement.orEmpty(), ManagementField.PROVINCE to province))
    private fun brlInput(cents: Long) = "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
    private companion object { const val ATTEMPT = "financial-management.attempt" }
}
