package br.com.saqz.groups.application.finance.uncharged

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.finance.charge.AttendanceBillingOutcome
import br.com.saqz.groups.application.finance.charge.ChargeManagementRepository
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.finance.charge.ChargeWithEvents
import br.com.saqz.groups.application.finance.charge.GameChargeInput
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.finance.charge.PaidMethod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** FIN-016: o estado de cada pessoa na revisão. */
enum class UnchargedDecision { CHARGE, PAID, SKIP }
enum class UnchargedKind { AVULSO, GUEST }

/** Quem está confirmado num jogo sem diária e não é mensalista na hora da leitura (convidado sempre, no anfitrião). */
data class UnchargedPerson(
    val memberId: UUID,
    val guestSeq: Int,
    val displayName: String,
    val hostDisplayName: String?,
    val kind: UnchargedKind,
    val noShow: Boolean,
    val leftGroup: Boolean,
    /** Já decidido "Não cobrar": continua na lista para poder ser reaberto. */
    val skipped: Boolean,
)

data class UnchargedGameSummary(
    val gameId: UUID,
    val title: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val venueName: String,
    val peopleCount: Int,
)

data class UnchargedGameView(val gameId: UUID, val suggestedAmountCents: Long?, val people: List<UnchargedPerson>)

data class UnchargedReviewItem(val memberId: UUID, val guestSeq: Int, val decision: UnchargedDecision, val paidMethod: PaidMethod? = null)
data class UnchargedReviewCommand(val requestId: UUID, val amountCents: Long?, val people: List<UnchargedReviewItem>)
data class UnchargedReviewOutcome(val charged: Int, val paid: Int, val skipped: Int, val ignored: Int, val chargeIds: List<UUID>)

/** Jogo travado para a revisão. [billable] falso = cancelado, rascunho ou Sem diária: ninguém entra. */
data class UnchargedReviewTarget(val startsAt: Instant, val zoneId: ZoneId, val billable: Boolean)

interface UnchargedRepository {
    fun role(actorId: UUID, groupId: UUID): GroupRole?
    /** Jogos com alguém a decidir (sem a marca), do mais recente ao mais antigo. */
    fun summary(groupId: UUID): List<UnchargedGameSummary>
    /** Nulo: o jogo não é do grupo. */
    fun game(groupId: UUID, gameId: UUID): UnchargedGameView?
    /** Trava o grupo (a mesma trava da criação de diária) e o jogo; nulo se o jogo não é do grupo. */
    fun lockForReview(groupId: UUID, gameId: UUID): UnchargedReviewTarget?
    fun replay(groupId: UUID, requestId: UUID): UnchargedReviewOutcome?
    fun skip(groupId: UUID, gameId: UUID, memberId: UUID, guestSeq: Int, actorId: UUID, at: Instant)
    fun clearSkip(gameId: UUID, memberId: UUID, guestSeq: Int)
    fun save(groupId: UUID, gameId: UUID, requestId: UUID, actorId: UUID, outcome: UnchargedReviewOutcome, at: Instant)
}

sealed interface UnchargedResult<out T> {
    data class Success<T>(val value: T) : UnchargedResult<T>
    data object Hidden : UnchargedResult<Nothing>
    data object Forbidden : UnchargedResult<Nothing>
    data class Invalid(val field: String) : UnchargedResult<Nothing>
}

data class UnchargedReviewResult(val outcome: UnchargedReviewOutcome, val charges: List<ChargeWithEvents>)

/**
 * Diária retroativa (DEB-147, FIN-016): só gestor. Cria pela porta das diárias ([ChargeTransactions]), então vale o
 * valor do atleta com recebimento ativo. Nada vai ao atleta: nem central, nem push, nem WhatsApp.
 */
class UnchargedReview(
    private val transaction: TransactionRunner,
    private val repository: UnchargedRepository,
    private val charges: ChargeTransactions,
    private val chargeLookup: ChargeManagementRepository,
    private val now: () -> Instant,
) {
    fun summary(actor: UUID, group: UUID): UnchargedResult<List<UnchargedGameSummary>> =
        authorized(actor, group) { UnchargedResult.Success(repository.summary(group)) }

    fun game(actor: UUID, group: UUID, game: UUID): UnchargedResult<UnchargedGameView> =
        authorized(actor, group) { repository.game(group, game)?.let { UnchargedResult.Success(it) } ?: UnchargedResult.Hidden }

    fun review(actor: UUID, group: UUID, game: UUID, command: UnchargedReviewCommand): UnchargedResult<UnchargedReviewResult> =
        transaction.inTransaction {
            authorized(actor, group) {
                val target = repository.lockForReview(group, game) ?: return@authorized UnchargedResult.Hidden
                // Mesmo requestId: o resultado gravado, sem segundo efeito (toque duplo, retentativa do app).
                repository.replay(group, command.requestId)?.let { return@authorized UnchargedResult.Success(result(group, it)) }
                val bills = command.people.any { it.decision != UnchargedDecision.SKIP }
                if (bills && (command.amountCents == null || command.amountCents !in 1..99_999_999)) {
                    return@authorized UnchargedResult.Invalid("amountCents")
                }
                // Lido depois da trava: quem já ganhou diária, saiu da lista ou virou mensalista conta em `ignored`.
                val open = if (target.billable) repository.game(group, game)?.people.orEmpty()
                    .associateBy { it.memberId to it.guestSeq }.toMutableMap() else mutableMapOf()
                var charged = 0; var paid = 0; var skipped = 0; var ignored = 0
                val ids = mutableListOf<UUID>()
                val at = now()
                val today = LocalDate.ofInstant(at, target.zoneId)
                command.people.forEach { item ->
                    if (open.remove(item.memberId to item.guestSeq) == null) { ignored++; return@forEach }
                    when (item.decision) {
                        UnchargedDecision.SKIP -> { repository.skip(group, game, item.memberId, item.guestSeq, actor, at); skipped++ }
                        UnchargedDecision.CHARGE, UnchargedDecision.PAID -> {
                            val input = GameChargeInput(
                                group, game, item.memberId, command.amountCents, today, AttendanceBillingOutcome.CONFIRMED, item.guestSeq,
                            )
                            val paidMethod = if (item.decision == UnchargedDecision.PAID) item.paidMethod ?: PaidMethod.PIX else null
                            // Já pagou: recebida na data do jogo, e entra no caixa nessa data.
                            ids += charges.retroactive(input, actor, paidMethod, target.startsAt).id
                            repository.clearSkip(game, item.memberId, item.guestSeq)
                            if (paidMethod == null) charged++ else paid++
                        }
                    }
                }
                val outcome = UnchargedReviewOutcome(charged, paid, skipped, ignored, ids)
                repository.save(group, game, command.requestId, actor, outcome, at)
                UnchargedResult.Success(result(group, outcome))
            }
        }

    private fun result(group: UUID, outcome: UnchargedReviewOutcome) =
        UnchargedReviewResult(outcome, outcome.chargeIds.mapNotNull { chargeLookup.find(group, it) })

    private fun <T> authorized(actor: UUID, group: UUID, block: () -> UnchargedResult<T>): UnchargedResult<T> =
        when (repository.role(actor, group)) {
            null -> UnchargedResult.Hidden
            GroupRole.ATHLETE -> UnchargedResult.Forbidden
            GroupRole.OWNER, GroupRole.ADMIN -> block()
        }
}
