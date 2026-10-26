package br.com.saqz.sharedkernel.group

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Cobrança do grupo vista pelo recebimento pelo app. `payableCents` já resolve o nulo para o valor da cobrança. */
data class LedgerCharge(val id: UUID, val groupId: UUID, val memberUserId: UUID, val kind: String, val gameId: UUID?,
    val gameDate: LocalDate?, val billingMonth: LocalDate?, val guestName: String?, val dueDate: LocalDate,
    val createdAt: Instant, val amountCents: Long, val payableCents: Long, val status: String, val settledBy: String?)

data class LedgerGroup(val id: UUID, val name: String, val ownerUserId: UUID)

/** Vínculo de mensalista (FIN-003/MEM-003): valor e dia efetivos (o do membro, senão o do grupo). */
data class MonthlyMembership(val active: Boolean, val mensalista: Boolean, val feeCents: Long?, val dueDay: Int?, val exempt: Boolean)

/** Jogo encerrado (início + duração) com diária em aberto: candidato à hora do acerto (FIN-013). */
data class FinishedGame(val groupId: UUID, val gameId: UUID, val localDate: LocalDate, val endsAt: Instant,
    val venueName: String, val court: String?)

/**
 * Cobranças e caixa do grupo para o recebimento pelo app (FIN-011, FIN-014). Implementada em grupos; todos os métodos
 * participam da transação de quem chama. Ordem de trava: cobranças (por id), depois as linhas de recebimentos.
 */
interface GroupChargeLedger {
    /** Grupo não excluído. */
    fun group(groupId: UUID): LedgerGroup?
    fun isActiveMember(groupId: UUID, userId: UUID): Boolean
    /** Quem resolve a revisão e recebe os avisos de pagamento (FIN-009): o dono e os admins ativos. */
    fun managers(groupId: UUID): Set<UUID>
    fun displayName(userId: UUID): String?
    /** Cobranças PENDING de que a pessoa é a pagadora (as dela e as dos convidados dela), mais antigas primeiro. */
    fun openCharges(groupId: UUID, payerId: UUID): List<LedgerCharge>
    /** Trava as cobranças (FOR UPDATE, em ordem de id) para a baixa não brigar com o "Recebi" do gestor. */
    fun lock(chargeIds: Collection<UUID>): List<LedgerCharge>
    /** PENDING → PAID com `settled_by=APP`; paid_method segue PIX no Pix e OTHER no cartão. Falso se já não estava pendente. */
    fun settleByApp(chargeId: UUID, method: String, actorId: UUID, at: Instant): Boolean
    /** Estorno ou contestação: marca a revisão, sem reabrir a dívida (como em setembro). */
    fun requireReview(chargeIds: Collection<UUID>, actorId: UUID, at: Instant)
    fun membership(groupId: UUID, userId: UUID): MonthlyMembership?
    /** A mensalidade do mês ([month] = primeiro dia) da pessoa, em qualquer estado. */
    fun monthlyCharge(groupId: UUID, memberId: UUID, month: LocalDate): LedgerCharge?
    /** Jogos publicados que terminaram entre [from] e [until] e têm diária PENDING. */
    fun finishedGames(from: Instant, until: Instant): List<FinishedGame>
    /** O que cada pagador deve de diárias daquele jogo (dele e dos convidados), pelo valor que ele paga. */
    fun gameDebts(groupId: UUID, gameId: UUID): Map<UUID, Long>
    /** Grupos com mensalidade PENDING que vence em [date]; valor por pagador. */
    fun monthlyDueOn(date: LocalDate): Map<UUID, Map<UUID, Long>>
    /** Lançamento automático no caixa (`origin`), idempotente pelo [id]. */
    fun record(id: UUID, groupId: UUID, direction: String, origin: String, description: String, amountCents: Long,
               date: LocalDate, actorId: UUID, at: Instant)
}
