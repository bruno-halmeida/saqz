package br.com.saqz.receivables.presentation

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Gestor-Revisao e Gestor-Atribuir: um Pix que caiu fora de um código do Saqz (FIN-014). */
@Serializable
data class PaymentReviewRoute(val groupId: String, val accountId: String, val reviewId: String) : NavKey

data class PaymentReviewArgs(val groupId: String, val accountId: String, val reviewId: String)

data class DebtorUi(val memberUserId: String, val name: String, val support: String?, val openCount: Int)

data class SuggestedChargeUi(val chargeId: String, val label: String, val support: String, val overdue: Boolean,
    val amountLabel: String, val cents: Long, val checked: Boolean)

data class PaymentReviewState(
    val loading: Boolean = true,
    val error: ReceiptError? = null,
    val payerName: String = "",
    val amountCents: Long = 0,
    val amountLabel: String = "",
    val receivedLabel: String = "",
    val assigning: Boolean = false,
    val query: String = "",
    val debtors: List<DebtorUi> = emptyList(),
    val member: DebtorUi? = null,
    val charges: List<SuggestedChargeUi> = emptyList(),
    val busy: Boolean = false,
) {
    val markedCents get() = charges.filter { it.checked }.sumOf { it.cents }
    val visibleDebtors get() = if (query.isBlank()) debtors else debtors.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val canAssign get() = member != null && !busy && charges.any { it.checked } && markedCents <= amountCents
}

sealed interface PaymentReviewIntent {
    data object Retry : PaymentReviewIntent
    data object StartAssign : PaymentReviewIntent
    data object BackToDetail : PaymentReviewIntent
    data class Search(val query: String) : PaymentReviewIntent
    data class SelectMember(val memberUserId: String) : PaymentReviewIntent
    data class ToggleCharge(val chargeId: String) : PaymentReviewIntent
    data object ConfirmAssign : PaymentReviewIntent
    data object NotCharge : PaymentReviewIntent
}

sealed interface PaymentReviewEffect { data object Resolved : PaymentReviewEffect }

@OptIn(ExperimentalUuidApi::class)
class PaymentReviewViewModel(
    private val args: PaymentReviewArgs,
    private val payments: GroupPaymentsGateway,
    private val debtors: GroupDebtorsGateway,
    private val session: ReceivablesSessionContext,
) : MviViewModel<PaymentReviewState, PaymentReviewIntent, PaymentReviewEffect>(PaymentReviewState()) {
    private val sessionKey = session.currentKey()
    private var suggestionGeneration = 0L

    init { load() }

    override fun handleIntent(intent: PaymentReviewIntent) {
        val current = state.value
        if (sessionKey == null || sessionKey != session.currentKey() || current.busy) return
        when (intent) {
            PaymentReviewIntent.Retry -> load()
            PaymentReviewIntent.StartAssign -> if (!current.loading && current.error == null) startAssign()
            PaymentReviewIntent.BackToDetail -> update { it.copy(assigning = false, member = null, charges = emptyList()) }
            is PaymentReviewIntent.Search -> update { it.copy(query = intent.query) }
            is PaymentReviewIntent.SelectMember -> current.debtors.firstOrNull { it.memberUserId == intent.memberUserId }
                ?.let(::selectMember)
            is PaymentReviewIntent.ToggleCharge -> update {
                it.copy(charges = it.charges.map { c -> if (c.chargeId == intent.chargeId) c.copy(checked = !c.checked) else c })
            }
            PaymentReviewIntent.ConfirmAssign -> if (current.canAssign) resolve {
                payments.assign(args.reviewId, it, current.member!!.memberUserId, current.charges.filter { c -> c.checked }.map { c -> c.chargeId })
            }
            PaymentReviewIntent.NotCharge -> if (!current.loading && !current.assigning) resolve { payments.notCharge(args.reviewId, it) }
        }
    }

    private fun load() {
        update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = payments.reviews(args.groupId, args.accountId)
            if (sessionKey != session.currentKey()) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = result.error) }
                is SaqzResult.Success -> {
                    val review = result.value.firstOrNull { it.id == args.reviewId }
                    if (review == null) {
                        // Resolvida em outro aparelho ou por outro admin: some daqui também.
                        update { it.copy(loading = false) }
                        emit(PaymentReviewEffect.Resolved)
                    } else update {
                        it.copy(loading = false, payerName = review.payerName, amountCents = review.amountCents,
                            amountLabel = formatBrl(review.amountCents), receivedLabel = receivedLabel(review.receivedAt))
                    }
                }
            }
        }
    }

    private fun startAssign() {
        update { it.copy(assigning = true, error = null) }
        viewModelScope.launch {
            val result = debtors.debtors(args.groupId)
            if (sessionKey != session.currentKey()) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(error = result.error) }
                is SaqzResult.Success -> update {
                    it.copy(debtors = result.value.map { d ->
                        DebtorUi(d.memberUserId, d.name,
                            if (d.openCount > 0) "${d.openCount} em aberto · ${formatBrl(d.openCents)}" else null, d.openCount)
                    })
                }
            }
        }
    }

    private fun selectMember(member: DebtorUi) {
        val expected = ++suggestionGeneration
        update { it.copy(member = member, charges = emptyList(), error = null) }
        viewModelScope.launch {
            val result = payments.suggestion(args.reviewId, member.memberUserId)
            if (expected != suggestionGeneration || sessionKey != session.currentKey()) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(error = result.error) }
                // A sugestão já vem marcada: as mais antigas que o valor cobre. O gestor pode desmarcar.
                is SaqzResult.Success -> update {
                    it.copy(charges = result.value.charges.map { c ->
                        SuggestedChargeUi(c.chargeId, c.label, if (c.overdue) "Vencida" else "Vence em ${PaymentLabels.dayMonth(c.dueDate)}",
                            c.overdue, formatBrl(c.payableCents), c.payableCents, checked = true)
                    })
                }
            }
        }
    }

    private fun <T> resolve(call: suspend (String) -> SaqzResult<T, ReceiptError>) {
        update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = call(Uuid.random().toString())
            if (sessionKey != session.currentKey()) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(busy = false, error = result.error) }
                is SaqzResult.Success -> {
                    update { it.copy(busy = false) }
                    emit(PaymentReviewEffect.Resolved)
                }
            }
        }
    }
}

/** `2026-07-28T23:04:00-03:00` → `28/07, 23:04` no fuso do aparelho. */
internal fun receivedLabel(iso: String): String = runCatching {
    val local = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
    "${local.day.toString().padStart(2, '0')}/${local.monthNumber.toString().padStart(2, '0')}, " +
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}.getOrDefault(iso)
