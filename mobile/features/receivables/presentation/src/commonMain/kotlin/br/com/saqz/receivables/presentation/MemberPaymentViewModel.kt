package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class MemberPaymentViewModel(private val orderId: String, private val gateway: MemberPaymentsGateway,
    private val renewal: PixRenewalGateway, private val conditions: GroupReceivablesGateway,
    private val receiptExport: ReceiptExportPort, private val saved: SavedStateHandle,
    private val runtime: MemberPaymentRuntime) :
    MviViewModel<MemberPaymentState, MemberPaymentIntent, MemberPaymentEffect>(MemberPaymentState()) {
    private val session get() = runtime.session
    private val identity get() = runtime.identity
    private val clock get() = runtime.clock
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
    override fun handleIntent(intent: MemberPaymentIntent) {
        if (!validSession()) return
        if (intent == MemberPaymentIntent.Refresh) return refresh()
        if (intent is MemberPaymentIntent.ReceiptExported) return finishReceiptExport(intent)
        val current = state.value
        if (current.loading || current.renewalPending || current.receiptSharing) return
        when (intent) {
            MemberPaymentIntent.Refresh -> Unit
            is MemberPaymentIntent.Method, is MemberPaymentIntent.Name, is MemberPaymentIntent.Document,
            is MemberPaymentIntent.Accept, is MemberPaymentIntent.RenewalDueDate -> edit(intent, current)
            is MemberPaymentIntent.ReceiptExported -> Unit
            else -> perform(intent, current)
        }
    }
    private fun edit(intent: MemberPaymentIntent, current: MemberPaymentState) = when (intent) {
        is MemberPaymentIntent.Method -> selectMethod(intent.method)
        is MemberPaymentIntent.Name -> if (current.canReview) update { it.copy(name = intent.value) } else Unit
        is MemberPaymentIntent.Document -> if (current.canReview) update {
            it.copy(document = intent.value.filter(Char::isDigit))
        } else Unit
        is MemberPaymentIntent.Accept -> if (current.canAccept) update { it.copy(accepted = intent.value) } else Unit
        is MemberPaymentIntent.RenewalDueDate -> if (current.pixExpired) update { it.copy(renewalDueDate =
            intent.value.filter { character -> character.isDigit() || character == '-' }.take(10), error = null) } else Unit
        else -> Unit
    }
    private fun perform(intent: MemberPaymentIntent, current: MemberPaymentState) = when (intent) {
        MemberPaymentIntent.Pay, MemberPaymentIntent.Replay -> submit(intent)
        MemberPaymentIntent.CopyPix -> useInstrument(copy = true)
        MemberPaymentIntent.Copied -> acknowledgeCopy()
        MemberPaymentIntent.OpenCard -> useInstrument(copy = false)
        MemberPaymentIntent.OpenFailed -> update { it.copy(openFailed = true) }
        MemberPaymentIntent.RenewPix -> if (current.canRenew) renewPix() else Unit
        MemberPaymentIntent.ExportReceipt -> if (current.canExportReceipt) exportReceipt() else Unit
        else -> Unit
    }
    private fun refresh() { if (!(state.value.loading && state.value.pending)) load(true) }
    private fun acknowledgeCopy() { if (state.value.canUseInstrument) update { it.copy(copied = true) } }
    private fun submit(intent: MemberPaymentIntent) {
        val canSubmit = if (intent == MemberPaymentIntent.Pay) state.value.canPay
            else state.value.pending && state.value.canReplay && command != null
        if (canSubmit) create()
    }
    private fun selectMethod(method: ReceiptMethod) {
        if (state.value.canReview && state.value.detail?.order?.quotes?.any { it.method == method } == true) choose(method)
    }
    private fun load(reconcile: Boolean) {
        if (!validSession()) return
        val expected = ++generation
        update { it.copy(loading = true, error = null, accepted = false, terms = null, copied = false, openFailed = false,
            receiptSharing = false, receiptShared = false, receiptShareFailed = false) }
        viewModelScope.launch {
            if (!current(expected)) return@launch
            val paymentRequest = saved.get<String>("payment.request")
            val result = if (reconcile) gateway.reconcile(orderId, paymentRequest ?: Uuid.random().toString())
                else gateway.detail(orderId)
            if (!current(expected)) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
                is SaqzResult.Success -> {
                    val detail = result.value
                    if (detail.order.id != orderId || detail.order.payerId != actor) {
                        update { MemberPaymentState(loading = false, error = ReceiptError.DENIED, pending = it.pending) }
                        return@launch
                    }
                    // Historical attempts cannot resolve an uncertain new POST.
                    val previous = saved.get<List<String>>("payment.previous")
                    val observed = detail.instruments.any { instrument ->
                        if (previous != null) instrument.id !in previous
                        else instrument.status !in setOf("CANCELLED", "EXPIRED")
                    }
                    if (observed || detail.order.status != "ISSUED") clearPending()
                    update { it.copy(loading = false, detail = detail, method = null) }
                    scheduleExpiry()
                    if (saved.get<String>(RENEWAL_REQUEST) != null) recoverRenewal(detail)
                }
            }
        }
    }
    private fun choose(method: ReceiptMethod) {
        val quote = state.value.detail?.order?.quotes?.singleOrNull { it.method == method } ?: return
        val expected = ++generation
        update { it.copy(loading = true, method = method, accepted = false, terms = null, error = null) }
        viewModelScope.launch {
            if (!current(expected)) return@launch
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
            order.fingerprint, true, MemberPaymentPayer(state.value.name.trim(), state.value.document)).also {
            command = it
            saved["payment.previous"] = state.value.detail!!.instruments.map { instrument -> instrument.id }
        }
        saved["payment.user"] = actor; saved["payment.order"] = orderId; saved["payment.request"] = next.requestId
        val expected = ++generation
        update { it.copy(loading = true, pending = true, canReplay = false, error = null) }
        viewModelScope.launch {
            if (!current(expected)) return@launch
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
            emit(MemberPaymentEffect.Copy(requireNotNull(i.pixPayload), generation))
        } else if (!copy && i.quote.method == ReceiptMethod.CARD) {
            val url = i.checkoutUrl
            if (url != null && hostedPaymentUrl(url)) emit(MemberPaymentEffect.Open(url, generation))
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
    private fun renewPix() {
        val detail = state.value.detail ?: return
        val instrument = detail.instrumentForRenewal() ?: return
        val command = PixRenewalCommand(Uuid.random().toString(), state.value.renewalDueDate)
        saveRenewal(command.requestId, detail, instrument)
        val expected = ++generation
        update { it.copy(loading = true, renewalPending = true, error = null, copied = false) }
        viewModelScope.launch {
            if (!current(expected)) return@launch
            val result = renewal.renew(detail.order, instrument, command)
            if (!current(expected)) return@launch
            handleRenewal(result, detail, instrument)
        }
    }
    private fun recoverRenewal(detail: MemberPaymentDetail) {
        val instrument = detail.instrumentForRenewal(saved.get(RENEWAL_INSTRUMENT)) ?: run { clearRenewal(); return }
        val request = saved.get<String>(RENEWAL_REQUEST) ?: return
        val valid = saved.get<String>(RENEWAL_USER) == actor && saved.get<String>(RENEWAL_ACCOUNT) == detail.order.accountId &&
            saved.get<String>(RENEWAL_GROUP) == detail.order.groupId && saved.get<String>(RENEWAL_ORDER) == detail.order.id &&
            saved.get<String>(RENEWAL_INSTRUMENT) == instrument.id
        if (!valid) { clearRenewal(); update { it.copy(error = ReceiptError.DENIED) }; return }
        val expected = ++generation
        update { it.copy(loading = true, renewalPending = true, error = null) }
        viewModelScope.launch {
            if (!current(expected)) return@launch
            val result = renewal.recover(detail.order, instrument, request)
            if (!current(expected)) return@launch
            handleRenewal(result, detail, instrument)
        }
    }
    private fun handleRenewal(result: SaqzResult<PixRenewal?, ReceiptError>, detail: MemberPaymentDetail,
        instrument: MemberPaymentInstrument) {
        when (result) {
            is SaqzResult.Failure -> {
                val uncertain = result.error in setOf(ReceiptError.UNCERTAIN, ReceiptError.NETWORK, ReceiptError.UNAVAILABLE)
                if (!uncertain) clearRenewal()
                update { it.copy(loading = false, renewalPending = uncertain, error = result.error) }
            }
            is SaqzResult.Success -> {
                val value = result.value
                if (value == null) update { it.copy(loading = false, renewalPending = true, error = ReceiptError.UNCERTAIN) }
                else if (!value.validFor(detail.order, instrument)) {
                    update { it.copy(loading = false, renewalPending = true, error = ReceiptError.UNCERTAIN) }
                } else {
                    val renewed = instrument.copy(status = value.status, pixPayload = value.pixPayload, pixImage = value.pixImage,
                        expiresAt = value.expiresAt)
                    val instruments = detail.instruments.map { if (it.id == renewed.id) renewed else it }
                    clearRenewal()
                    update { it.copy(loading = false, renewalPending = false, renewalDueDate = "", error = null,
                        pixExpired = false, detail = MemberPaymentDetail(detail.order.copy(dueDate = value.dueDate), instruments)) }
                    scheduleExpiry()
                }
            }
        }
    }
    private fun saveRenewal(requestId: String, detail: MemberPaymentDetail, instrument: MemberPaymentInstrument) {
        saved[RENEWAL_USER] = actor
        saved[RENEWAL_ACCOUNT] = detail.order.accountId
        saved[RENEWAL_GROUP] = detail.order.groupId
        saved[RENEWAL_ORDER] = detail.order.id
        saved[RENEWAL_INSTRUMENT] = instrument.id
        saved[RENEWAL_REQUEST] = requestId
    }
    private fun clearRenewal() = listOf(RENEWAL_USER, RENEWAL_ACCOUNT, RENEWAL_GROUP, RENEWAL_ORDER,
        RENEWAL_INSTRUMENT, RENEWAL_REQUEST).forEach { saved.remove<Any?>(it) }
    private fun exportReceipt() {
        val text = state.value.detail?.let(::receiptExportText) ?: return
        val expected = ++generation
        update { it.copy(receiptSharing = true, receiptShared = false, receiptShareFailed = false) }
        receiptExport.export(text) { result ->
            viewModelScope.launch {
                if (current(expected)) finishReceiptExport(MemberPaymentIntent.ReceiptExported(result, expected))
            }
        }
    }
    private fun finishReceiptExport(intent: MemberPaymentIntent.ReceiptExported) {
        if (intent.generation != generation || !validSession()) return
        update { it.copy(receiptSharing = false, receiptShared = intent.result == ReceiptExportResult.Shared,
            receiptShareFailed = intent.result == ReceiptExportResult.Failed) }
    }
    private fun clearPending() {
        clearMarker(); command = null
        update { it.copy(pending = false, canReplay = false) }
    }
    private fun clearMarker() { saved.keys().filter { it.startsWith("payment.") }.forEach { saved.remove<Any?>(it) } }
    private fun current(expected: Int) = validSession() && expected == generation
    fun validEffect(effect: MemberPaymentEffect): Boolean {
        if (!current(effect.generation)) return false
        val instrument = state.value.instrument ?: return false
        if (instrument.expired(clock.now())) update { it.copy(pixExpired = true) }
        return state.value.canUseInstrument
    }
    fun validSession(): Boolean {
        if (key != null && key == session.currentKey() && actor != null && actor == identity.currentUserId()) return true
        generation++; expiryJob?.cancel(); clearMarker(); clearRenewal(); command = null
        update { MemberPaymentState(loading = false, error = ReceiptError.SIGNED_OUT) }
        return false
    }
    override fun onCleared() {
        if (key != session.currentKey()) { clearMarker(); clearRenewal() }
        super.onCleared()
    }

    private companion object {
        const val RENEWAL_USER = "renewal.user"
        const val RENEWAL_ACCOUNT = "renewal.account"
        const val RENEWAL_GROUP = "renewal.group"
        const val RENEWAL_ORDER = "renewal.order"
        const val RENEWAL_INSTRUMENT = "renewal.instrument"
        const val RENEWAL_REQUEST = "renewal.request"
    }
}
