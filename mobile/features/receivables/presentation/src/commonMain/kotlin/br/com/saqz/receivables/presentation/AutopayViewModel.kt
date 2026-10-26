package br.com.saqz.receivables.presentation

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Só o próprio atleta adere e cancela (FIN-012); o cartão é cadastrado na página do Asaas. */
@OptIn(ExperimentalUuidApi::class)
class AutopayViewModel(
    private val args: GroupArgs,
    private val payments: AthletePaymentsGateway,
    private val autopay: AutopayGateway,
    private val session: ReceivablesSessionContext,
) : MviViewModel<AutopayState, AutopayIntent, AutopayEffect>(AutopayState(args.groupName)) {
    private val sessionKey = session.currentKey()
    private var generation = 0L

    init { load() }

    override fun handleIntent(intent: AutopayIntent) {
        val current = state.value
        if (sessionKey == null || sessionKey != session.currentKey()) {
            update { AutopayState(args.groupName, loading = false, error = PaymentError.SIGNED_OUT) }
            return
        }
        when (intent) {
            AutopayIntent.Retry -> load()
            AutopayIntent.Resumed -> if (!current.loading && !current.busy && !current.cancelling) load(silent = true)
            AutopayIntent.Start -> if (current.status == RecurrenceStatus.NONE && !current.busy) checkout { autopay.start(args.groupId, it) }
            AutopayIntent.ChangeCard -> if (current.status != RecurrenceStatus.NONE && current.status != null && !current.busy) {
                checkout { autopay.changeCard(args.groupId, it) }
            }
            AutopayIntent.RequestCancel -> if (current.status != RecurrenceStatus.NONE && current.status != null) {
                update { it.copy(cancelling = true, reason = null, error = null) }
            }
            is AutopayIntent.SelectReason -> if (current.cancelling && !current.busy) update { it.copy(reason = intent.reason) }
            // Motivo obrigatório: sem ele, nada sai (FIN-012).
            AutopayIntent.ConfirmCancel -> if (current.canConfirmCancel) cancel(current.reason!!)
            AutopayIntent.KeepAutopay -> if (!current.busy) update { it.copy(cancelling = false, reason = null) }
        }
    }

    private fun checkout(call: suspend (String) -> SaqzResult<String, PaymentError>) {
        val expected = generation
        update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = call(Uuid.random().toString())
            if (expected != generation || sessionKey != session.currentKey()) return@launch
            update { it.copy(busy = false, error = (result as? SaqzResult.Failure)?.error) }
            if (result is SaqzResult.Success) emit(AutopayEffect.OpenUrl(result.value))
        }
    }

    private fun cancel(reason: AutopayCancelReason) {
        val expected = generation
        update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = autopay.cancel(args.groupId, Uuid.random().toString(), reason)
            if (expected != generation || sessionKey != session.currentKey()) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(busy = false, error = result.error) }
                is SaqzResult.Success -> {
                    update { it.copy(busy = false, cancelling = false, reason = null, cancelled = true) }
                    load(silent = true)
                }
            }
        }
    }

    private fun load(silent: Boolean = false) {
        val expected = ++generation
        if (!silent) update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = payments.me(args.groupId)
            if (expected != generation || sessionKey != session.currentKey()) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
                is SaqzResult.Success -> update { autopayState(it, result.value) }
            }
        }
    }
}

internal fun autopayState(current: AutopayState, me: AthleteReceivables): AutopayState {
    val recurrence = me.recurrence?.takeIf { me.active }
    val openMonthly = me.openCharges.firstOrNull { it.kind == OpenChargeKind.MONTHLY }
    val day = (recurrence?.nextChargeDate ?: openMonthly?.dueDate)?.split('-')?.getOrNull(2)?.trimStart('0')
    return current.copy(
        loading = false,
        error = null,
        status = recurrence?.status,
        amountLabel = formatBrl(recurrence?.amountCents?.takeIf { it > 0 } ?: openMonthly?.payableCents ?: 0),
        dueDay = day,
        openMonth = openMonthly?.let { PaymentLabels.monthName(it.billingMonth)?.replaceFirstChar(Char::uppercase) },
        nextCharge = recurrence?.nextChargeDate?.let { "${formatBrl(recurrence.amountCents)} em ${PaymentLabels.dayMonth(it)}" },
        history = recurrence?.history.orEmpty().map {
            AutopayHistoryUi(
                PaymentLabels.monthName(it.billingMonth)?.replaceFirstChar(Char::uppercase) ?: "Mensalidade",
                formatBrl(it.amountCents) + (it.chargedOn?.let { date ->
                    if (it.atAdhesion) " · cobrado na adesão, ${PaymentLabels.dayMonth(date)}"
                    else " · cobrado em ${PaymentLabels.dayMonth(date)}"
                } ?: ""),
            )
        },
    )
}
