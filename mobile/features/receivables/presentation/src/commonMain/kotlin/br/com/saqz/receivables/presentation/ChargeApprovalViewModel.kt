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
class ChargeApprovalViewModel(private val groupId: String, private val chargeId: String,
    private val gateway: ChargeApprovalGateway, private val conditions: GroupReceivablesGateway,
    private val session: ReceivablesSessionContext, private val identity: ReceivablesRecoveryIdentity,
    private val saved: SavedStateHandle) :
    MviViewModel<ChargeApprovalState, ChargeApprovalIntent, ChargeApprovalEffect>(ChargeApprovalState()) {
    private val key = session.currentKey()
    private val actor = identity.currentUserId()
    private var generation = 0
    init {
        val restored = saved.get<String>("approval.attempt")?.let { runCatching { Json.decodeFromString<ApprovalAttempt>(it) }.getOrNull() }
        if (restored != null && restored.actor == actor && restored.groupId == groupId && restored.chargeId == chargeId) {
            update { it.copy(attempt = restored, accountId = restored.accountId) }
        } else saved.remove<String>("approval.attempt")
        refresh()
    }
    override fun onIntent(intent: ChargeApprovalIntent) {
        if (!validSession()) return
        if (intent == ChargeApprovalIntent.Refresh) { requestRefresh(); return }
        val current = state.value
        if (current.loading) return
        when (intent) {
            ChargeApprovalIntent.Refresh -> Unit
            ChargeApprovalIntent.ChooseAccount -> resetAccount()
            is ChargeApprovalIntent.Account -> select(intent.id)
            is ChargeApprovalIntent.Accept -> if (current.canAccept) update { it.copy(accepted = intent.value) }
            ChargeApprovalIntent.Approve -> if (current.canApprove) begin(null)
            ChargeApprovalIntent.Replay -> current.attempt?.let(::write)
            ChargeApprovalIntent.RequestCancel -> if (current.canCancel) update { it.copy(confirmCancel = true) }
            ChargeApprovalIntent.DismissCancel -> update { it.copy(confirmCancel = false) }
            ChargeApprovalIntent.ConfirmCancel -> if (current.canCancel && current.confirmCancel) begin(current.detail!!.order.id)
        }
    }
    private fun requestRefresh() {
        if (!state.value.loading || state.value.attempt == null) refresh()
    }
    private fun select(id: String) {
        if (state.value.attempt != null || state.value.accounts.none { it.id == id }) return
        update { it.copy(accountId = id, review = null, detail = null, terms = emptyList(), accepted = false, confirmCancel = false) }
        refresh()
    }
    private fun resetAccount() {
        if (state.value.attempt != null) return
        update { ChargeApprovalState(loading = false) }; refresh()
    }
    private fun refresh() {
        if (!validSession()) return
        val expected = ++generation
        update { it.copy(loading = true, error = null, accepted = false, terms = emptyList(), review = null, confirmCancel = false) }
        viewModelScope.launch {
            val account = state.value.accountId
            if (account == null) loadAccounts(expected) else loadOrder(expected, ChargeApprovalTarget(groupId, chargeId, account))
        }
    }
    private suspend fun loadAccounts(expected: Int) {
        val result = conditions.accounts()
        if (!current(expected)) return
        when (result) {
            is SaqzResult.Success -> update { it.copy(loading = false, accounts = result.value) }
            is SaqzResult.Failure -> failed(result.error)
        }
    }
    private suspend fun loadOrder(expected: Int, target: ChargeApprovalTarget) {
        val result = gateway.lookup(target)
        if (!current(expected)) return
        when (result) {
            is SaqzResult.Failure -> failed(result.error)
            is SaqzResult.Success -> {
                val detail = result.value
                update { it.copy(detail = detail) }
                if (detail != null) {
                    val hadAttempt = state.value.attempt != null
                    resolveAttempt(detail)
                    update { it.copy(loading = false) }
                    if (hadAttempt && state.value.attempt == null) emit(ChargeApprovalEffect(generation))
                } else if (state.value.attempt != null) update { it.copy(loading = false) }
                else loadReview(expected, target)
            }
        }
    }
    private suspend fun loadReview(expected: Int, target: ChargeApprovalTarget) {
        val result = gateway.preview(target, Uuid.random().toString())
        if (!current(expected)) return
        when (result) {
            is SaqzResult.Failure -> failed(result.error)
            is SaqzResult.Success -> {
                val review = result.value
                update { it.copy(review = review) }
                val documents = mutableListOf<ReceiptTerms>()
                for (version in review.quotes.map { it.termsVersion }.distinct()) {
                    val terms = conditions.terms(version)
                    if (!current(expected)) return
                    if (terms !is SaqzResult.Success || terms.value.version != version || terms.value.content.isBlank()) {
                        failed(if (terms is SaqzResult.Failure) terms.error else ReceiptError.INVALID); return
                    }
                    documents += terms.value
                }
                update { it.copy(loading = false, terms = documents) }
            }
        }
    }
    private fun begin(orderId: String?) {
        val account = state.value.accountId ?: return
        val attempt = ApprovalAttempt(actor ?: return, groupId, chargeId, account, Uuid.random().toString(),
            state.value.review?.fingerprint.orEmpty(), orderId)
        saved["approval.attempt"] = Json.encodeToString(attempt)
        update { it.copy(attempt = attempt, confirmCancel = false) }
        write(attempt)
    }
    private fun write(attempt: ApprovalAttempt) {
        val expected = ++generation
        update { it.copy(loading = true, error = null, accepted = false) }
        viewModelScope.launch {
            val result = if (attempt.orderId == null) {
                when (val approved = gateway.approve(attempt.target, ChargeApprovalCommand(attempt.requestId, attempt.fingerprint, true))) {
                    is SaqzResult.Success -> SaqzResult.Success(MemberPaymentDetail(approved.value, emptyList()))
                    is SaqzResult.Failure -> approved
                }
            } else gateway.cancel(attempt.target, attempt.orderId, attempt.requestId)
            if (!current(expected)) return@launch
            when (result) {
                is SaqzResult.Failure -> {
                    if (result.error !in setOf(ReceiptError.UNCERTAIN, ReceiptError.NETWORK, ReceiptError.UNAVAILABLE)) {
                        clearAttempt(); update { it.copy(review = null, detail = null, terms = emptyList()) }
                    }
                    failed(result.error)
                }
                is SaqzResult.Success -> {
                    update { it.copy(loading = false, detail = result.value, review = null, terms = emptyList()) }
                    resolveAttempt(result.value)
                    emit(ChargeApprovalEffect(generation))
                }
            }
        }
    }
    private fun resolveAttempt(detail: MemberPaymentDetail) {
        val attempt = state.value.attempt ?: return
        if (attempt.orderId == null || detail.order.status in setOf("CANCELLED", "PAID", "REFUNDED", "CHARGEBACK")) {
            clearAttempt()
        }
    }
    private fun failed(error: ReceiptError) {
        if (error == ReceiptError.SIGNED_OUT) {
            generation++; saved.remove<String>("approval.attempt")
            update { ChargeApprovalState(loading = false, error = error) }
        } else update { it.copy(loading = false, error = error) }
    }
    private fun clearAttempt() { saved.remove<String>("approval.attempt"); update { it.copy(attempt = null) } }
    private fun current(expected: Int) = validSession() && generation == expected
    fun validEffect(effect: ChargeApprovalEffect) = current(effect.generation)
    fun validSession(): Boolean {
        if (key != null && key == session.currentKey() && actor != null && actor == identity.currentUserId()) return true
        generation++; saved.remove<String>("approval.attempt")
        update { ChargeApprovalState(loading = false, error = ReceiptError.SIGNED_OUT) }
        return false
    }
}
