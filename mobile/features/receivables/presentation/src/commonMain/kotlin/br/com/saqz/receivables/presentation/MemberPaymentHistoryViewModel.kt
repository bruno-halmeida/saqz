package br.com.saqz.receivables.presentation

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.launch

class MemberPaymentHistoryViewModel(private val gateway: MemberPaymentsGateway, private val session: ReceivablesSessionContext) :
    MviViewModel<MemberPaymentHistoryState, MemberPaymentHistoryIntent, MemberPaymentHistoryEffect>(MemberPaymentHistoryState()) {
    private val key = session.currentKey()
    private var generation = 0
    init { load(false) }
    override fun handleIntent(intent: MemberPaymentHistoryIntent) {
        if (!valid()) return
        when (intent) {
            MemberPaymentHistoryIntent.Refresh -> load(false)
            MemberPaymentHistoryIntent.More -> if (!state.value.loading && state.value.nextCursor != null) load(true)
            is MemberPaymentHistoryIntent.Open -> if (state.value.orders.any { it.id == intent.id }) {
                emit(MemberPaymentHistoryEffect(intent.id, generation))
            }
        }
    }
    private fun load(more: Boolean) {
        if (!valid()) return
        val expected = ++generation
        val cursor = if (more) state.value.nextCursor else null
        update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = gateway.orders(cursor)
            if (!valid() || generation != expected) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
                is SaqzResult.Success -> update { it.copy(loading = false,
                    orders = ((if (more) it.orders else emptyList()) + result.value.orders).distinctBy { order -> order.id },
                    nextCursor = result.value.nextCursor, error = null) }
            }
        }
    }
    fun valid(): Boolean {
        if (key != null && key == session.currentKey()) return true
        generation++
        update { MemberPaymentHistoryState(loading = false, error = ReceiptError.SIGNED_OUT) }
        return false
    }
    fun validEffect(effect: MemberPaymentHistoryEffect) = valid() && effect.generation == generation &&
        state.value.orders.any { it.id == effect.orderId }
}
