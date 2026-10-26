package br.com.saqz.receivables.application

import br.com.saqz.sharedkernel.group.ChargeCollection
import br.com.saqz.sharedkernel.group.GroupChargeLedger
import br.com.saqz.sharedkernel.group.ReceivablesNotices
import br.com.saqz.sharedkernel.group.SettleTarget
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** [groupName] é aditivo (rodada 2 da F2): o app abre o pagamento sem saber o nome do grupo. */
data class SettleDestination(val groupId: UUID, val gameId: UUID?, val billingMonth: LocalDate?, val groupName: String? = null)

/** Links da hora do acerto: o mesmo código por (grupo, jogo) ou (grupo, mês); anunciar uma vez só. */
interface SettleLinkStore : SettleLinkUrls {
    /** URL do link se a hora do acerto ainda não saiu (e marca como saída); nula se já saiu. */
    fun announceGame(groupId: UUID, gameId: UUID): String?
    fun announceMonth(groupId: UUID, month: LocalDate): String?
    fun resolve(code: String): SettleDestination?
}

/**
 * Hora do acerto (FIN-013). Jogo: 30 minutos depois do fim (início + duração), se alguém deve diária dele. Mensalistas: no
 * dia de vencimento, a partir das 09:00, para quem não está no cartão. Uma vez por jogo ou mês; sai mesmo depois das 22h.
 * No grupo do WhatsApp nunca diz quem deve; o push vai para cada devedor com o valor dele.
 */
class SettleTime(private val ledger: GroupChargeLedger, private val codes: PaymentCodeStore, private val athletes: AthletePayments,
    private val subscriptions: SubscriptionStore, private val links: SettleLinkStore, private val notices: ReceivablesNotices,
    private val clock: Clock, private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")) {

    fun run() { games(); monthly() }

    fun games() {
        val until = clock.instant().minus(AFTER_END)
        // Janela curta: um deploy depois de dias parado não dispara acerto de jogo antigo.
        ledger.finishedGames(until.minus(Duration.ofHours(6)), until.plusMillis(1)).forEach { game ->
            codes.transaction {
                if (!active(game.groupId)) return@transaction
                val debts = ledger.gameDebts(game.groupId, game.gameId).filterValues { it > 0 }
                if (debts.isEmpty()) return@transaction
                val group = ledger.group(game.groupId) ?: return@transaction
                val url = links.announceGame(game.groupId, game.gameId) ?: return@transaction
                val day = game.localDate
                val place = game.venueName + (game.court?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "")
                notices.groupWhatsApp(game.groupId, "${group.name}\nJogo finalizado, hora do acerto\n" +
                    "${weekdayTitle(day)}, ${day.format(DAY)} · $place\nQuem jogou avulso paga pelo Saqz e a baixa sai na hora.",
                    "Pagar meu jogo", url, UUID.nameUUIDFromBytes("settle-game-group:${game.gameId}".toByteArray()))
                debts.forEach { (payer, cents) ->
                    notices.publish(game.groupId, setOf(payer), "Hora do acerto",
                        "${brl(cents)} do jogo de ${PaymentSettlement.weekday(day.dayOfWeek)} no ${group.name}. Toque para pagar.",
                        UUID.nameUUIDFromBytes("settle-game:${game.gameId}:$payer".toByteArray()), target = SettleTarget(gameId = game.gameId))
                }
            }
        }
    }

    fun monthly() {
        val now = clock.instant().atZone(zone)
        if (now.toLocalTime() < LocalTime.of(9, 0)) return
        val today = now.toLocalDate()
        ledger.monthlyDueOn(today).forEach { (groupId, dues) ->
            codes.transaction {
                if (!active(groupId)) return@transaction
                val onCard = subscriptions.withStatus("ACTIVE").filter { it.groupId == groupId }.map { it.memberId }.toSet()
                val debtors = dues.filterKeys { it !in onCard }.filterValues { it > 0 }
                if (debtors.isEmpty()) return@transaction
                val group = ledger.group(groupId) ?: return@transaction
                val month = today.withDayOfMonth(1)
                val url = links.announceMonth(groupId, month) ?: return@transaction
                val monthName = PaymentSettlement.month(month)
                notices.groupWhatsApp(groupId, "${group.name}\nMensalistas, hora do acerto\nMensalidade de $monthName · vence hoje\n" +
                    "Pague pelo Saqz ou deixe no automático no cartão.", "Pagar mensalidade", url,
                    UUID.nameUUIDFromBytes("settle-month-group:$groupId:$month".toByteArray()))
                debtors.forEach { (payer, cents) ->
                    notices.publish(groupId, setOf(payer), "Hora do acerto",
                        "${brl(cents)} da mensalidade de $monthName no ${group.name}. Toque para pagar.",
                        UUID.nameUUIDFromBytes("settle-month:$groupId:$month:$payer".toByteArray()), target = SettleTarget(billingMonth = month))
                }
            }
        }
    }

    /** Link aberto pelo app (logado): só para quem é membro ativo do grupo. */
    fun resolve(code: String, request: FinancialRequest): FinancialResult<SettleDestination> {
        val destination = codes.transaction {
            links.resolve(code)?.takeIf { ledger.isActiveMember(it.groupId, request.actorUserId) }
                ?.let { it.copy(groupName = ledger.group(it.groupId)?.name) }
        }
            ?: return FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId)
        return FinancialResult.Success(destination, request.requestId)
    }

    /** "Cobrar" com recebimentos ativos (FIN-013): botão "Pagar no app" para o link daquele jogo ou mês e o copia e cola. */
    fun collection(groupId: UUID, payerId: UUID, chargeIds: List<UUID>): ChargeCollection? {
        // Chamado de dentro da transação da fila do WhatsApp: tudo em transação nova e sem lançar para fora.
        val url = try {
            codes.independent {
                if (!active(groupId)) return@independent null
                val charge = ledger.lock(chargeIds).firstOrNull { it.groupId == groupId } ?: return@independent null
                charge.gameId?.let { links.gameUrl(groupId, it) } ?: links.monthUrl(groupId, (charge.billingMonth ?: charge.dueDate).withDayOfMonth(1))
            }
        } catch (_: Exception) { null } ?: return null
        return ChargeCollection(url, athletes.internalPix(groupId, payerId, chargeIds)?.payload)
    }

    private fun active(groupId: UUID) = codes.receivingAccount(groupId)?.let(athletes::available) == true

    companion object {
        private val AFTER_END: Duration = Duration.ofMinutes(30)
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
        fun weekdayTitle(date: LocalDate) = PaymentSettlement.weekday(date.dayOfWeek).replaceFirstChar { it.uppercase() }
    }
}
