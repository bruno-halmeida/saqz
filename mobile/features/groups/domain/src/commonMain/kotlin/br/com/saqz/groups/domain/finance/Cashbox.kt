package br.com.saqz.groups.domain.finance

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

/**
 * FIN-006: o dinheiro que o grupo já tinha antes do Saqz. `amountCents == null` = ninguém respondeu ainda
 * (é o que mostra a pergunta do caixa zerado); `0` = começou do zero.
 */
data class OpeningBalance(
    val amountCents: Long?,
    val version: FinanceVersionToken,
)

/** FIN-016: o resumo do caixa — quantos jogaram sem cobrança e em quais jogos (do mais recente ao mais antigo). */
data class UnchargedSummary(
    val peopleCount: Int,
    val games: List<UnchargedGame>,
)

data class UnchargedGame(
    val gameId: String,
    val title: String,
    val localDate: String,
    val localTime: String,
    val venueName: String?,
    val peopleCount: Int,
)

enum class UnchargedKind {
    Avulso,
    Guest,
}

/** A decisão já guardada no servidor: só "Não cobrar" fica na lista (para poder ser reaberta). */
enum class UnchargedDecision {
    Skip,
}

data class UnchargedPerson(
    val memberId: String,
    val guestSeq: Int,
    val displayName: String,
    val hostDisplayName: String?,
    val kind: UnchargedKind,
    val noShow: Boolean,
    val leftGroup: Boolean,
    val decision: UnchargedDecision?,
)

data class UnchargedList(
    val gameId: String,
    /** O valor do jogo; sem ele, o padrão do grupo; sem os dois, `null` (o gestor digita). */
    val suggestedAmountCents: Long?,
    val people: List<UnchargedPerson>,
)

/** O que o gestor escolheu para cada pessoa na revisão: Cobrar, Já pagou ou Não cobrar. */
enum class ReviewDecision {
    Charge,
    Paid,
    Skip,
}

data class UnchargedReviewEntry(
    val memberId: String,
    val guestSeq: Int,
    val decision: ReviewDecision,
    val paidMethod: PaidMethod? = null,
)

data class UnchargedReviewCommand(
    val requestId: String,
    val amountCents: Long?,
    val people: List<UnchargedReviewEntry>,
)

data class UnchargedReviewResult(
    val charged: Int,
    val paid: Int,
    val skipped: Int,
    val ignored: Int,
    val charges: List<Charge>,
)

/** Saldo inicial (FIN-006) e diária retroativa (FIN-016): só o gestor. */
interface CashboxGateway {
    suspend fun openingBalance(groupId: GroupId): SaqzResult<OpeningBalance, FinanceError>

    /** `If-Match` com a versão lida; 409 vira [FinanceError.Conflict]. */
    suspend fun saveOpeningBalance(
        groupId: GroupId,
        version: FinanceVersionToken,
        amountCents: Long,
    ): SaqzResult<OpeningBalance, FinanceError>

    suspend fun unchargedSummary(groupId: GroupId): SaqzResult<UnchargedSummary, FinanceError>

    suspend fun uncharged(groupId: GroupId, gameId: String): SaqzResult<UnchargedList, FinanceError>

    suspend fun reviewUncharged(
        groupId: GroupId,
        gameId: String,
        command: UnchargedReviewCommand,
    ): SaqzResult<UnchargedReviewResult, FinanceError>
}
