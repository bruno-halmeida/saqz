package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class MemberPaymentViewModel(private val orderId: String, private val gateway: MemberPaymentsGateway,
    private val conditions: GroupReceivablesGateway, private val session: ReceivablesSessionContext,
    private val saved: SavedStateHandle, private val identity: ReceivablesRecoveryIdentity, private val clock: Clock) :
    MviViewModel<MemberPaymentState, MemberPaymentIntent, MemberPaymentEffect>(MemberPaymentState()) {
    private val key = session.currentKey()
    private val actor = identity.currentUserId()
    private var generation = 0
    private var command: MemberPaymentCommand? = null
    private var expiryJob: Job? = null
    init {
        if (actor != null && saved.get<String>("payment.user") == actor && saved.get<String>("payment.order") == orderId &&
            saved.get<String>("payment.request") != null) update { it.copy(pending = true) }
        else clearMarker()
        load(reconcile = state.value.pending)
    }
    override fun onIntent(intent: MemberPaymentIntent) {
        if (!validSession()) return
        if (intent == MemberPaymentIntent.Refresh) {
            refresh()
            return
        }
        val current = state.value
        if (current.loading) return
        when (intent) {
            MemberPaymentIntent.Refresh -> Unit
            is MemberPaymentIntent.Method -> selectMethod(intent.method)
            is MemberPaymentIntent.Name -> if (current.canReview) update { it.copy(name = intent.value) }
            is MemberPaymentIntent.Document -> if (current.canReview) update { it.copy(document = intent.value.filter(Char::isDigit)) }
            is MemberPaymentIntent.Accept -> if (current.canAccept) update { it.copy(accepted = intent.value) }
            MemberPaymentIntent.Pay -> if (current.canPay) create()
            MemberPaymentIntent.Replay -> if (canReplay()) create()
            MemberPaymentIntent.CopyPix -> useInstrument(copy = true)
            MemberPaymentIntent.OpenCard -> useInstrument(copy = false)
            MemberPaymentIntent.OpenFailed -> update { it.copy(openFailed = true) }
        }
    }
    private fun refresh() { if (!(state.value.loading && state.value.pending)) load(true) }
    private fun canReplay() = state.value.pending && state.value.canReplay && command != null
    private fun selectMethod(method: ReceiptMethod) {
        if (state.value.canReview && state.value.detail?.order?.quotes?.any { it.method == method } == true) choose(method)
    }
    private fun load(reconcile: Boolean) {
        if (!validSession()) return
        val expected = ++generation
        update { it.copy(loading = true, error = null, accepted = false, terms = null, copied = false, openFailed = false) }
        viewModelScope.launch {
            val result = if (reconcile) gateway.reconcile(orderId, Uuid.random().toString()) else gateway.detail(orderId)
            if (!current(expected)) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
                is SaqzResult.Success -> {
                    val detail = result.value
                    if (detail.order.id != orderId || detail.order.payerId != actor) {
                        update { MemberPaymentState(loading = false, error = ReceiptError.DENIED, pending = it.pending) }
                        return@launch
                    }
                    // Absence after an uncertain POST is not evidence that the POST failed.
                    if (detail.instruments.isNotEmpty() || detail.order.status != "ISSUED") clearPending()
                    update { it.copy(loading = false, detail = detail, method = null) }
                    scheduleExpiry()
                }
            }
        }
    }
    private fun choose(method: ReceiptMethod) {
        val quote = state.value.detail?.order?.quotes?.singleOrNull { it.method == method } ?: return
        val expected = ++generation
        update { it.copy(loading = true, method = method, accepted = false, terms = null, error = null) }
        viewModelScope.launch {
            val result = conditions.terms(quote.termsVersion)
            if (!current(expected)) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
                is SaqzResult.Success -> {
                    val valid = result.value.version == quote.termsVersion && result.value.content.isNotBlank()
                    update { it.copy(loading = false, terms = if (valid) result.value else null,
                        error = if (valid) null else ReceiptError.INVALID) }
                }
            }
        }
    }
    private fun create() {
        val order = state.value.detail?.order ?: return
        val next = command ?: MemberPaymentCommand(Uuid.random().toString(), state.value.method ?: return,
            order.fingerprint, true, MemberPaymentPayer(state.value.name.trim(), state.value.document)).also { command = it }
        saved["payment.user"] = actor; saved["payment.order"] = orderId; saved["payment.request"] = next.requestId
        val expected = ++generation
        update { it.copy(loading = true, pending = true, canReplay = false, error = null) }
        viewModelScope.launch {
            val result = gateway.instrument(order, next)
            if (!current(expected)) return@launch
            when (result) {
                is SaqzResult.Failure -> {
                    val uncertain = result.error in setOf(ReceiptError.UNCERTAIN, ReceiptError.NETWORK, ReceiptError.UNAVAILABLE)
                    if (!uncertain) clearPending()
                    update { it.copy(loading = false, error = result.error, canReplay = uncertain, accepted = false,
                        detail = if (uncertain) it.detail else null, terms = null) }
                }
                is SaqzResult.Success -> {
                    val instruments = state.value.detail!!.instruments.filter { it.id != result.value.id } + result.value
                    clearPending()
                    update { it.copy(loading = false, name = "", document = "", method = null, accepted = false, terms = null,
                        detail = MemberPaymentDetail(order, instruments)) }
                    scheduleExpiry()
                }
            }
        }
    }
    private fun useInstrument(copy: Boolean) {
        val i = state.value.instrument ?: return
        if (i.expired(clock.now())) { update { it.copy(pixExpired = true) }; return }
        if (!state.value.canUseInstrument) return
        if (copy && i.quote.method == ReceiptMethod.PIX && !i.pixPayload.isNullOrBlank()) {
            emit(MemberPaymentEffect.Copy(requireNotNull(i.pixPayload)))
            update { it.copy(copied = true) }
        } else if (!copy && i.quote.method == ReceiptMethod.CARD) {
            val url = i.checkoutUrl
            if (url != null && hostedPaymentUrl(url)) emit(MemberPaymentEffect.Open(url))
            else update { it.copy(openFailed = true) }
        }
    }
    private fun scheduleExpiry() {
        expiryJob?.cancel()
        val i = state.value.instrument
        update { it.copy(pixExpired = i?.expired(clock.now()) == true) }
        if (i == null || i.quote.method != ReceiptMethod.PIX || state.value.pixExpired) return
        val expires = i.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return
        val expected = generation
        expiryJob = viewModelScope.launch {
            delay((expires - clock.now()).inWholeMilliseconds.coerceAtLeast(0))
            if (current(expected)) update { it.copy(pixExpired = true) }
        }
    }
    private fun clearPending() {
        clearMarker(); command = null
        update { it.copy(pending = false, canReplay = false) }
    }
    private fun clearMarker() { saved.keys().filter { it.startsWith("payment.") }.forEach { saved.remove<Any?>(it) } }
    private fun current(expected: Int) = validSession() && expected == generation
    fun validSession(): Boolean {
        if (key != null && key == session.currentKey() && actor != null && actor == identity.currentUserId()) return true
        generation++; expiryJob?.cancel(); clearMarker(); command = null
        update { MemberPaymentState(loading = false, error = ReceiptError.SIGNED_OUT) }
        return false
    }
    override fun onCleared() {
        if (key != session.currentKey()) clearMarker()
        super.onCleared()
    }
}
