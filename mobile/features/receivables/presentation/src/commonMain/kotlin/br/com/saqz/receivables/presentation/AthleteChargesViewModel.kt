package br.com.saqz.receivables.presentation

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class AthleteChargesViewModel(
    private val args: GroupArgs,
    private val payments: AthletePaymentsGateway,
    private val history: OwnChargeHistoryGateway,
    private val session: ReceivablesSessionContext,
) : MviViewModel<AthleteChargesState, AthleteChargesIntent, AthleteChargesEffect>(AthleteChargesState(args.groupName)) {
    private val sessionKey = session.currentKey()
    private var generation = 0L
    private var openIds: List<String> = emptyList()

    init { load() }

    override fun handleIntent(intent: AthleteChargesIntent) {
        val current = state.value
        when (intent) {
            AthleteChargesIntent.Refresh -> load()
            AthleteChargesIntent.Appeared -> if (!isFirstAppearance() && !current.loading) load()
            AthleteChargesIntent.PayAll -> if (!current.loading && openIds.isNotEmpty()) {
                emit(AthleteChargesEffect.OpenPayment(openIds))
            }
            is AthleteChargesIntent.Pay -> if (current.open.any { it.chargeId == intent.chargeId }) {
                emit(AthleteChargesEffect.OpenPayment(listOf(intent.chargeId)))
            }
            AthleteChargesIntent.OpenAutopay -> if (current.autopay != null) emit(AthleteChargesEffect.OpenAutopay)
        }
    }

    private fun load() {
        if (sessionKey == null || sessionKey != session.currentKey()) {
            update { it.copy(loading = false, error = PaymentError.SIGNED_OUT) }
            return
        }
        val expected = ++generation
        update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val paid = async { history.paid(args.groupId) }
            val me = payments.me(args.groupId)
            val paidResult = paid.await()
            if (expected != generation || sessionKey != session.currentKey()) return@launch
            when (me) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = me.error) }
                is SaqzResult.Success -> {
                    openIds = me.value.openCharges.map { it.chargeId }
                    update {
                        athleteChargesState(args.groupName, me.value, (paidResult as? SaqzResult.Success)?.value)
                    }
                }
            }
        }
    }
}

/** Regra de exibição de Minhas cobranças, fora da ViewModel para o teste ler direto. */
internal fun athleteChargesState(groupName: String, me: AthleteReceivables, paid: List<PaidCharge>?): AthleteChargesState {
    val recurrence = me.recurrence
    val refusedId = recurrence?.refusedChargeId?.takeIf { recurrence.status == RecurrenceStatus.REFUSED }
    val open = me.openCharges.sortedWith(compareBy<OpenCharge> { it.dueDate }.thenBy { it.guestName != null })
    val monthlyOnly = open.size == 1 && open.single().kind == OpenChargeKind.MONTHLY
    val overdue = open.count { it.overdue }
    val refused = refusedId?.let { id -> open.firstOrNull { it.chargeId == id } }
    val next = recurrence?.nextChargeDate?.let(PaymentLabels::dayMonth)
    return AthleteChargesState(
        groupName = groupName,
        loading = false,
        active = me.active,
        totalLabel = formatBrl(me.totalCents),
        summaryLabel = when {
            monthlyOnly -> PaymentLabels.support(open.single())
            open.size == 1 -> "1 cobrança"
            else -> "${open.size} cobranças"
        },
        overdueLabel = if (!monthlyOnly && overdue > 0) "$overdue ${if (overdue == 1) "vencida" else "vencidas"}" else null,
        monthlyKicker = if (monthlyOnly) "mensalidade de ${PaymentLabels.monthName(open.single().billingMonth)}" else null,
        open = open.map {
            OpenChargeUi(it.chargeId, PaymentLabels.title(it), PaymentLabels.support(it, refused = it.chargeId == refusedId),
                formatBrl(it.payableCents), it.overdue)
        },
        history = paid.orEmpty().map { PaidChargeUi(it.chargeId, PaymentLabels.history(it), PaymentLabels.historySupport(it)) },
        historyFailed = paid == null,
        autopay = when {
            recurrence == null || !me.active -> null
            recurrence.status == RecurrenceStatus.REFUSED && refused != null -> AutopayCardUi.Refused(refused.chargeId,
                PaymentLabels.monthName(refused.billingMonth) ?: PaymentLabels.dayMonth(refused.dueDate),
                formatBrl(refused.payableCents), next)
            recurrence.status == RecurrenceStatus.NONE -> AutopayCardUi.Offer(
                open.firstOrNull { it.kind == OpenChargeKind.MONTHLY }?.dueDate?.split('-')?.getOrNull(2)?.trimStart('0'))
            else -> AutopayCardUi.Active(next)
        },
    )
}
