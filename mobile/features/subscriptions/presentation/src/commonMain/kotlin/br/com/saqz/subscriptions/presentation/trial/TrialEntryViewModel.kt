package br.com.saqz.subscriptions.presentation.trial

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import kotlinx.coroutines.launch

enum class TrialEntryFailure { Load, Coupon, Offer, Apply }
data class TrialEntryState(
    val loading: Boolean = true,
    val access: TrialAccess? = null,
    val code: String = "",
    val failure: TrialEntryFailure? = null,
    val ready: Boolean = false,
) {
    val canContinue: Boolean get() = !loading && failure == null && access?.canCreateGroup == true
}
sealed interface TrialEntryIntent {
    data object Refresh : TrialEntryIntent
    data class EditCode(val value: String) : TrialEntryIntent
    data object Apply : TrialEntryIntent
    data object Continue : TrialEntryIntent
}

class TrialEntryViewModel(private val gateway: TrialGateway) :
    MviViewModel<TrialEntryState, TrialEntryIntent, Nothing>(TrialEntryState()) {
    private var generation = 0
    init { load() }
    override fun onIntent(intent: TrialEntryIntent) {
        when (intent) {
            TrialEntryIntent.Refresh -> load()
            is TrialEntryIntent.EditCode -> if (!state.value.loading) update { it.copy(code = intent.value.take(32)) }
            TrialEntryIntent.Apply -> apply()
            TrialEntryIntent.Continue -> if (state.value.canContinue) load(proceed = true)
        }
    }
    private fun load(proceed: Boolean = false) {
        val request = ++generation
        update { it.copy(loading = true, failure = null, ready = false) }
        viewModelScope.launch {
            val result = gateway.ownerTrial()
            if (request != generation) return@launch
            when (result) {
                is SaqzResult.Success -> update { it.copy(loading = false, access = result.value,
                    ready = result.value.canCreateGroup && (proceed || result.value.status == TrialStatus.Active ||
                        result.value.status == TrialStatus.Subscribed)) }
                is SaqzResult.Failure -> update { it.copy(loading = false, access = null, failure = TrialEntryFailure.Load) }
            }
        }
    }
    private fun apply() {
        if (state.value.loading || state.value.access?.canRedeemCoupon != true) return
        val code = state.value.code.trim().uppercase()
        if (!code.matches(Regex("[A-Z0-9]{1,32}"))) {
            update { it.copy(failure = TrialEntryFailure.Coupon) }
            return
        }
        val request = ++generation
        update { it.copy(loading = true, failure = null) }
        viewModelScope.launch {
            val result = gateway.applyCoupon(code)
            if (request != generation) return@launch
            when (result) {
                is SaqzResult.Success -> update { it.copy(loading = false, access = result.value, code = code) }
                is SaqzResult.Failure -> update { it.copy(loading = false, failure = when (result.error) {
                    TrialError.CouponUnavailable -> TrialEntryFailure.Coupon
                    TrialError.OfferUnavailable -> TrialEntryFailure.Offer
                    else -> TrialEntryFailure.Apply
                }) }
            }
        }
    }
}
