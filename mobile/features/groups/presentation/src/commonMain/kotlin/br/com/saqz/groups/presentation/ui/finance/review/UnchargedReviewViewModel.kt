package br.com.saqz.groups.presentation.ui.finance.review

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrlPlain
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.core.common.formatting.sanitizeBrlInput
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.CashboxGateway
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.ReviewDecision
import br.com.saqz.groups.domain.finance.UnchargedDecision
import br.com.saqz.groups.domain.finance.UnchargedKind
import br.com.saqz.groups.domain.finance.UnchargedReviewCommand
import br.com.saqz.groups.domain.finance.UnchargedReviewEntry
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.presentation.ui.finance.dayMonth
import br.com.saqz.groups.presentation.ui.finance.weekdayDayMonth
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Revisão de quem jogou sem cobrança (FIN-016, revisão B). Estado inicial pela regra: avulso e convidado em Cobrar;
 * quem faltou, saiu do grupo ou já ficou em Não cobrar, em Não cobrar. Um `requestId` por confirmação, repetido a
 * cada nova tentativa até dar certo; toque duplo em Confirmar não manda duas vezes.
 */
@OptIn(ExperimentalUuidApi::class)
class UnchargedReviewViewModel(
    private val groupId: String,
    private val gameId: String,
    private val cashbox: CashboxGateway,
    private val games: GameGateway,
) : MviViewModel<UnchargedReviewState, UnchargedReviewIntent, UnchargedReviewEffect>(UnchargedReviewState()) {
    // ponytail: o requestId vive na ViewModel; morte do processo no meio do envio gera outro (o servidor ainda não
    // duplica: uma diária por pessoa e jogo). Guardar no SavedStateHandle se aparecer caso real.
    private var requestId = Uuid.random().toString()
    private var loadGeneration = 0L

    init {
        load()
    }

    override fun handleIntent(intent: UnchargedReviewIntent) {
        when (intent) {
            UnchargedReviewIntent.Retry -> load()
            is UnchargedReviewIntent.AmountChanged -> update {
                it.copy(amountInput = sanitizeBrlInput(intent.value), amountInvalid = false, saveFailed = false)
            }
            is UnchargedReviewIntent.OpenMenu -> if (!state.value.saving && state.value.rows.any { it.key == intent.key }) {
                update { it.copy(menuKey = intent.key) }
            }
            UnchargedReviewIntent.DismissMenu -> update { it.copy(menuKey = null) }
            is UnchargedReviewIntent.Choose -> if (!state.value.saving) update { current ->
                current.copy(
                    menuKey = null,
                    rows = current.rows.map { if (it.key == intent.key) it.copy(decision = intent.decision) else it },
                )
            }
            is UnchargedReviewIntent.CycleMethod -> if (!state.value.saving) update { current ->
                current.copy(rows = current.rows.map { if (it.key == intent.key) it.copy(method = it.method.next()) else it })
            }
            UnchargedReviewIntent.Confirm -> confirm()
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val list = cashbox.uncharged(GroupId(groupId), gameId)
            if (generation != loadGeneration) return@launch
            val game = (games.read(GroupId(groupId), gameId) as? SaqzResult.Success)?.value?.game
            if (generation != loadGeneration) return@launch
            when (list) {
                is SaqzResult.Failure -> update { it.copy(isLoading = false, loadFailed = true) }
                is SaqzResult.Success -> update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        gameDay = game?.localDate?.let(::weekdayDayMonth).orEmpty(),
                        venueName = game?.venue?.name,
                        gameDate = game?.localDate?.let(::dayMonth).orEmpty(),
                        amountInput = list.value.suggestedAmountCents?.let(::formatBrlPlain).orEmpty(),
                        suggested = list.value.suggestedAmountCents != null,
                        rows = list.value.people.map { person ->
                            ReviewRowUi(
                                key = "${person.memberId}/${person.guestSeq}",
                                memberId = person.memberId,
                                guestSeq = person.guestSeq,
                                name = person.displayName,
                                hostName = person.hostDisplayName.takeIf { person.kind == UnchargedKind.Guest },
                                noShow = person.noShow,
                                leftGroup = person.leftGroup,
                                decision = initialDecision(
                                    noShow = person.noShow,
                                    leftGroup = person.leftGroup,
                                    skipped = person.decision == UnchargedDecision.Skip,
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    private fun confirm() {
        val current = state.value
        if (current.saving || current.isLoading || current.rows.isEmpty()) return
        val needsAmount = current.chargeCount + current.paidCount > 0
        val cents = parseBrlToCents(current.amountInput)
        if (needsAmount && (cents == null || cents !in 1L..MAX_CENTS)) {
            update { it.copy(amountInvalid = true) }
            return
        }
        val command = UnchargedReviewCommand(
            requestId = requestId,
            amountCents = cents.takeIf { needsAmount },
            people = current.rows.map {
                UnchargedReviewEntry(
                    memberId = it.memberId,
                    guestSeq = it.guestSeq,
                    decision = it.decision,
                    paidMethod = it.method.takeIf { _ -> it.decision == ReviewDecision.Paid },
                )
            },
        )
        update { it.copy(saving = true, saveFailed = false, menuKey = null) }
        viewModelScope.launch {
            when (cashbox.reviewUncharged(GroupId(groupId), gameId, command)) {
                is SaqzResult.Success -> {
                    requestId = Uuid.random().toString()
                    update { it.copy(saving = false) }
                    emit(UnchargedReviewEffect.Done)
                }
                // A próxima tentativa sai com o mesmo requestId: o servidor devolve o mesmo resultado sem efeito novo.
                is SaqzResult.Failure -> update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    private companion object {
        const val MAX_CENTS = 99_999_999L
    }
}

/** Regra da FIN-016: Faltou, saiu do grupo ou "Não cobrar" já decidido abrem em Não cobrar; o resto em Cobrar. */
internal fun initialDecision(noShow: Boolean, leftGroup: Boolean, skipped: Boolean): ReviewDecision =
    if (noShow || leftGroup || skipped) ReviewDecision.Skip else ReviewDecision.Charge

private fun PaidMethod.next() = when (this) {
    PaidMethod.Pix -> PaidMethod.Cash
    PaidMethod.Cash -> PaidMethod.Other
    PaidMethod.Other -> PaidMethod.Pix
}
