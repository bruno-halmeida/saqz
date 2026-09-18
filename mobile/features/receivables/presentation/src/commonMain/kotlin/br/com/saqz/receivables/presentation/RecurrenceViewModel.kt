package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class RecurrenceViewModel(
    private val accountId: String,
    private val groupId: String,
    private val gateway: RecurrenceGateway,
    private val conditions: GroupReceivablesGateway,
    private val session: ReceivablesSessionContext,
    private val identity: ReceivablesRecoveryIdentity,
    private val saved: SavedStateHandle,
) : MviViewModel<RecurrenceState, RecurrenceIntent, RecurrenceEffect>(RecurrenceState(accountId, groupId)) {
    private val sessionKey = session.currentKey()
    private val actor = identity.currentUserId()
    private var generation = 0

    init {
        val attempt = restoredAttempt()
        if (attempt == null) discover() else recover(attempt)
    }

    override fun handleIntent(intent: RecurrenceIntent) {
        if (!validSession()) return
        if (intent == RecurrenceIntent.Refresh) return refresh()
        val current = state.value
        if (current.loading || current.pending) return
        when (intent) {
            RecurrenceIntent.Refresh -> Unit
            is RecurrenceIntent.Method, is RecurrenceIntent.DueDate -> editReview(intent, current)
            is RecurrenceIntent.Name, is RecurrenceIntent.Document, is RecurrenceIntent.Accept -> editAcceptance(intent, current)
            else -> performAction(intent, current)
        }
    }

    private fun refresh() {
        val attempt = restoredAttempt()
        if (attempt == null) discover() else recover(attempt)
    }

    private fun editReview(intent: RecurrenceIntent, current: RecurrenceState) {
        if (!current.canEditReview) return
        when (intent) {
            is RecurrenceIntent.Method -> resetReview { it.copy(method = intent.value) }
            is RecurrenceIntent.DueDate -> resetReview { state -> state.copy(firstDueDate = intent.value
                .filter { character -> character.isDigit() || character == '-' }.take(10)) }
            else -> Unit
        }
    }

    private fun editAcceptance(intent: RecurrenceIntent, current: RecurrenceState) {
        if (!current.canAccept) return
        when (intent) {
            is RecurrenceIntent.Name -> update { it.copy(name = intent.value) }
            is RecurrenceIntent.Document -> update { it.copy(document = intent.value.filter(Char::isDigit)) }
            is RecurrenceIntent.Accept -> update { it.copy(accepted = intent.value) }
            else -> Unit
        }
    }

    private fun performAction(intent: RecurrenceIntent, current: RecurrenceState) = when (intent) {
        RecurrenceIntent.Preview -> if (current.canPreview) preview() else Unit
        RecurrenceIntent.Submit -> if (current.canSubmit) submit() else Unit
        RecurrenceIntent.Cancel -> if (current.canCancel) cancel() else Unit
        RecurrenceIntent.OpenCheckout -> openCheckout()
        RecurrenceIntent.OpenFailed -> update { it.copy(checkoutOpenFailed = true) }
        else -> Unit
    }

    private fun discover() = launchLatest { expected ->
        val result = gateway.discover(accountId, groupId)
        if (!current(expected)) return@launchLatest
        when (result) {
            is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
            is SaqzResult.Success -> {
                val recurrence = result.value
                if (recurrence != null && !recurrence.validFor(accountId, groupId, requireNotNull(actor))) {
                    update { RecurrenceState(accountId, groupId, loading = false, error = ReceiptError.DENIED) }
                } else update { it.copy(loading = false, recurrence = recurrence, firstDueDate = recurrence?.firstDueDate.orEmpty()) }
            }
        }
    }

    private fun preview() {
        val current = state.value
        val command = RecurrencePreviewCommand(Uuid.random().toString(), accountId, groupId, current.method, current.firstDueDate)
        update { it.copy(review = null, terms = null, accepted = false) }
        launchLatest { expected ->
            val result = gateway.preview(command)
            if (!current(expected)) return@launchLatest
            when (result) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
                is SaqzResult.Success -> {
                    val review = result.value
                    if (!review.validFor(accountId, groupId, requireNotNull(actor), command.method) ||
                        review.firstDueDate != command.firstDueDate) {
                        update { it.copy(loading = false, error = ReceiptError.INVALID) }
                        return@launchLatest
                    }
                    val terms = conditions.terms(review.termsVersion)
                    if (!current(expected)) return@launchLatest
                    when (terms) {
                        is SaqzResult.Failure -> update { it.copy(loading = false, error = terms.error) }
                        is SaqzResult.Success -> {
                            val document = terms.value.takeIf { it.version == review.termsVersion && it.content.isNotBlank() }
                            update { it.copy(loading = false, review = review.takeIf { document != null }, terms = document,
                                error = if (document == null) ReceiptError.INVALID else null, accepted = false) }
                        }
                    }
                }
            }
        }
    }

    private fun submit() {
        val current = state.value
        val review = current.review ?: return
        val command = RecurrenceAcceptanceCommand(Uuid.random().toString(), accountId, groupId, current.method,
            current.firstDueDate, review.fingerprint, true, MemberPaymentPayer(current.name.trim(), current.document))
        val old = current.recurrence?.takeIf { it.status == "STOPPED" }
        saveAttempt(command.requestId, old?.id, if (old == null) "AUTHORIZE" else "RESUME")
        launchMutation {
            if (old == null) gateway.authorize(review, command) else gateway.resume(old, review, command)
        }
    }

    private fun cancel() {
        val recurrence = state.value.recurrence ?: return
        val requestId = Uuid.random().toString()
        saveAttempt(requestId, recurrence.id, "CANCEL")
        launchMutation { gateway.cancel(recurrence, requestId) }
    }

    private fun launchMutation(call: suspend () -> SaqzResult<PaymentRecurrence, ReceiptError>) = launchLatest(pending = true) { expected ->
        val result = call()
        if (!current(expected)) return@launchLatest
        when (result) {
            is SaqzResult.Failure -> {
                val uncertain = result.error in setOf(ReceiptError.UNCERTAIN, ReceiptError.NETWORK, ReceiptError.UNAVAILABLE)
                if (!uncertain) clearAttempt()
                update { it.copy(loading = false, pending = uncertain, error = result.error, accepted = false,
                    name = if (uncertain) it.name else "", document = if (uncertain) it.document else "") }
            }
            is SaqzResult.Success -> finishMutation(result.value)
        }
    }

    private fun recover(attempt: RecurrenceAttempt) = launchLatest(pending = true) { expected ->
        val result = gateway.recover(attempt)
        if (!current(expected)) return@launchLatest
        when (result) {
            is SaqzResult.Failure -> update { it.copy(loading = false, pending = true, error = result.error) }
            is SaqzResult.Success -> result.value?.let(::finishMutation) ?: run {
                update { it.copy(loading = false, pending = true, error = ReceiptError.UNCERTAIN) }
            }
        }
    }

    private fun finishMutation(recurrence: PaymentRecurrence) {
        val cancellationUnconfirmed = saved.get<String>(OPERATION) == "CANCEL" &&
            recurrence.status !in setOf("STOP_PENDING", "STOPPED")
        if (!recurrence.validFor(accountId, groupId, requireNotNull(actor)) || cancellationUnconfirmed) {
            update { it.copy(loading = false, pending = true, error = ReceiptError.UNCERTAIN) }
            return
        }
        if (recurrence.status == "STOP_PENDING") {
            update { RecurrenceState(accountId, groupId, loading = false, recurrence = recurrence,
                firstDueDate = recurrence.firstDueDate, pending = true, error = ReceiptError.UNCERTAIN) }
        } else {
            clearAttempt()
            update { RecurrenceState(accountId, groupId, loading = false, recurrence = recurrence,
                firstDueDate = recurrence.firstDueDate) }
        }
    }

    private fun openCheckout() {
        val url = state.value.recurrence?.hostedCheckoutUrl
        if (state.value.canOpenCheckout && url != null) emit(RecurrenceEffect.Open(url, generation))
        else update { it.copy(checkoutOpenFailed = true) }
    }

    private fun resetReview(change: (RecurrenceState) -> RecurrenceState) {
        update { change(it).copy(review = null, terms = null, accepted = false, name = "", document = "", error = null) }
    }

    private fun launchLatest(pending: Boolean = false, block: suspend (Int) -> Unit) {
        if (!validSession()) return
        val expected = ++generation
        update { it.copy(loading = true, pending = pending, error = null, checkoutOpenFailed = false) }
        viewModelScope.launch { if (current(expected)) block(expected) }
    }

    private fun saveAttempt(requestId: String, recurrenceId: String?, operation: String) {
        saved[USER] = actor
        saved[ACCOUNT] = accountId
        saved[GROUP] = groupId
        saved[REQUEST] = requestId
        saved[RECURRENCE] = recurrenceId
        saved[OPERATION] = operation
    }

    private fun restoredAttempt(): RecurrenceAttempt? {
        val request = saved.get<String>(REQUEST) ?: return null
        val valid = actor != null && saved.get<String>(USER) == actor && saved.get<String>(ACCOUNT) == accountId &&
            saved.get<String>(GROUP) == groupId && saved.get<String>(OPERATION) in OPERATIONS
        if (!valid) {
            clearAttempt()
            return null
        }
        return RecurrenceAttempt(request, actor, accountId, groupId, saved.get(RECURRENCE), requireNotNull(saved.get(OPERATION)))
    }

    private fun clearAttempt() {
        listOf(USER, ACCOUNT, GROUP, REQUEST, RECURRENCE, OPERATION).forEach { saved.remove<Any?>(it) }
    }

    private fun current(expected: Int) = expected == generation && validSession()
    fun validEffect(effect: RecurrenceEffect) = current(effect.generation) && state.value.canOpenCheckout
    fun validSession(): Boolean {
        if (sessionKey != null && sessionKey == session.currentKey() && actor != null && actor == identity.currentUserId()) return true
        generation++
        clearAttempt()
        update { RecurrenceState(accountId, groupId, loading = false, error = ReceiptError.SIGNED_OUT) }
        return false
    }

    override fun onCleared() {
        if (sessionKey != session.currentKey()) clearAttempt()
        super.onCleared()
    }

    private companion object {
        const val USER = "recurrence.user"
        const val ACCOUNT = "recurrence.account"
        const val GROUP = "recurrence.group"
        const val REQUEST = "recurrence.request"
        const val RECURRENCE = "recurrence.id"
        const val OPERATION = "recurrence.operation"
        val OPERATIONS = setOf("AUTHORIZE", "RESUME", "CANCEL")
    }
}
