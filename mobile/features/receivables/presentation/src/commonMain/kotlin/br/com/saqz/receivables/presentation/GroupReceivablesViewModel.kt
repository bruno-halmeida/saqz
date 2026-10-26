package br.com.saqz.receivables.presentation

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.launch

/**
 * O recebimento do grupo visto pelo gestor, para as peças que entram nas telas de grupo (Editar grupo, Caixa, Membros):
 * qual conta recebe, se está ativo, a política de taxa, a carteira, os Pix para revisar e quem está no automático.
 * Uma por grupo; quem não tem conta financeira (atleta, admin sem delegação) fica com tudo vazio e nada aparece.
 */
data class GroupReceivablesState(
    val loading: Boolean = true,
    val accountId: String? = null,
    val active: Boolean = false,
    val feePolicy: FeePolicy = FeePolicy.ABSORB,
    val walletLabel: String? = null,
    val reviews: List<PaymentReview> = emptyList(),
    /** `null`: rota da F3 ainda não existe (ou falhou); a linha some dos membros. */
    val autopay: Map<String, MemberAutopayStatus>? = null,
    val quotes: Map<Long, FeeQuote> = emptyMap(),
)

sealed interface GroupReceivablesIntent {
    data object Refresh : GroupReceivablesIntent
    data object LoadAutopay : GroupReceivablesIntent
    data class Quote(val baseCents: Long) : GroupReceivablesIntent
}

class GroupReceivablesViewModel(
    private val groupId: String,
    private val receivables: GroupReceivablesGateway,
    private val payments: GroupPaymentsGateway,
    private val wallet: ReceiptWalletGateway,
    private val autopay: AutopayGateway,
    private val session: ReceivablesSessionContext,
) : MviViewModel<GroupReceivablesState, GroupReceivablesIntent, Nothing>(GroupReceivablesState()) {
    private val sessionKey = session.currentKey()
    private var generation = 0L
    private val quoting = mutableSetOf<Long>()

    init { load() }

    override fun handleIntent(intent: GroupReceivablesIntent) {
        if (sessionKey == null || sessionKey != session.currentKey()) return
        when (intent) {
            GroupReceivablesIntent.Refresh -> load()
            GroupReceivablesIntent.LoadAutopay -> loadAutopay()
            is GroupReceivablesIntent.Quote -> quote(intent.baseCents)
        }
    }

    private fun load() {
        if (sessionKey == null || sessionKey != session.currentKey()) {
            update { it.copy(loading = false) }
            return
        }
        val expected = ++generation
        viewModelScope.launch {
            val accounts = (receivables.accounts() as? SaqzResult.Success)?.value.orEmpty()
            var chosen: ReceiptStatus? = null
            for (account in accounts) {
                val status = (receivables.status(groupId, account.id) as? SaqzResult.Success)?.value ?: continue
                if (status.state.groupId != groupId) continue
                if (chosen == null || status.state.enabled && !chosen.state.enabled) chosen = status
                if (status.state.enabled) break
            }
            if (expected != generation || sessionKey != session.currentKey()) return@launch
            val state = chosen?.state
            if (state == null) {
                update { GroupReceivablesState(loading = false) }
                return@launch
            }
            update { it.copy(loading = false, accountId = state.accountId, active = state.enabled, feePolicy = state.feePolicy) }
            val balance = wallet.balance(state.accountId)
            val reviews = if (state.enabled || state.openReviews > 0) payments.reviews(groupId, state.accountId) else null
            if (expected != generation || sessionKey != session.currentKey()) return@launch
            update {
                it.copy(
                    walletLabel = (balance as? SaqzResult.Success)?.value?.availableBalanceCents?.let(::formatBrl),
                    reviews = (reviews as? SaqzResult.Success)?.value.orEmpty(),
                )
            }
            if (state.enabled) loadAutopay()
        }
    }

    private fun loadAutopay() {
        val account = state.value.accountId ?: return
        if (!state.value.active) return
        val expected = generation
        viewModelScope.launch {
            val result = autopay.members(groupId, account)
            if (expected != generation || sessionKey != session.currentKey()) return@launch
            update { s -> s.copy(autopay = (result as? SaqzResult.Success)?.value?.associate { m -> m.memberUserId to m.status }) }
        }
    }

    private fun quote(baseCents: Long) {
        val account = state.value.accountId ?: return
        if (baseCents <= 0 || baseCents in state.value.quotes || !quoting.add(baseCents)) return
        viewModelScope.launch {
            val result = payments.quote(groupId, account, baseCents)
            quoting.remove(baseCents)
            if (sessionKey != session.currentKey()) return@launch
            if (result is SaqzResult.Success) update { s -> s.copy(quotes = s.quotes + (baseCents to result.value)) }
        }
    }
}

/** "Atleta paga R$ 20,00 · você recebe R$ 20,00 − taxas" (absorver) ou "Atleta paga R$ 21,28 · você recebe R$ 20,00". */
internal fun feePreviewText(policy: FeePolicy, baseCents: Long, quote: FeeQuote?): String {
    val pays = quote?.side(policy)?.athletePaysCents ?: baseCents
    return if (policy == FeePolicy.PASS) "Atleta paga ${formatBrl(pays)} · você recebe ${formatBrl(baseCents)}"
    else "Atleta paga ${formatBrl(pays)} · você recebe ${formatBrl(baseCents)} − taxas"
}
