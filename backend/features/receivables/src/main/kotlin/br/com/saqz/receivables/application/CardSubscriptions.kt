package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeCalculator
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.group.GroupChargeLedger
import br.com.saqz.sharedkernel.group.GroupReceivables
import br.com.saqz.sharedkernel.group.PrivateWhatsApp
import br.com.saqz.sharedkernel.group.ReceivablesNotices
import br.com.saqz.sharedkernel.group.SettleTarget
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class HostedCheckout(val id: String, val url: String)
data class SubscriptionPayment(val id: String, val dueDate: LocalDate, val status: String, val valueCents: Long)

/** Assinatura no cartão no Asaas (FIN-012): checkout hospedado CREDIT_CARD + RECURRENT com a divisão da comissão. */
interface SubscriptionProvider {
    fun createCheckout(accountId: UUID, subscriptionId: UUID, customer: CardCustomer, amountCents: Long, commissionCents: Long,
                       nextDueDate: LocalDate, savedCustomer: (String) -> Unit): HostedCheckout
    fun subscriptionOfCheckout(accountId: UUID, checkoutId: String): String?
    fun cancelSubscription(accountId: UUID, subscriptionId: String): Boolean
    fun cancelCheckout(accountId: UUID, checkoutId: String): Boolean
    fun updateSubscriptionValue(accountId: UUID, subscriptionId: String, amountCents: Long, commissionCents: Long): Boolean
    fun subscriptionPayments(accountId: UUID, subscriptionId: String): List<SubscriptionPayment>
    /** Próxima cobrança da assinatura na data dada (o ciclo MONTHLY segue dali). */
    fun realignSubscription(accountId: UUID, subscriptionId: String, nextDueDate: LocalDate): Boolean
    fun cancelPayment(accountId: UUID, paymentId: String): Boolean
}

/** URLs do link da hora do acerto (`https://links.saqz.app/acerto/{codigo}`). */
interface SettleLinkUrls {
    fun monthUrl(groupId: UUID, month: LocalDate): String
    fun gameUrl(groupId: UUID, gameId: UUID): String
}

data class CardSubscription(val id: UUID, val accountId: UUID, val groupId: UUID, val memberId: UUID, val status: String,
    val amountCents: Long, val commissionCents: Long, val nextAmountCents: Long?, val nextAmountFrom: LocalDate?,
    val changeNotifiedAt: Instant?, val firstDueDate: LocalDate, val replacesId: UUID?, val checkoutId: String?,
    val checkoutUrl: String?, val providerSubscriptionId: String?, val refusedChargeId: UUID?, val createdAt: Instant,
    /** Dia de vencimento para o qual a assinatura no Asaas está alinhada; nulo = realinhar. */
    val alignedDueDay: Int? = null)

interface SubscriptionStore {
    fun <T> transaction(block: () -> T): T
    fun lockMember(groupId: UUID, memberId: UUID)
    fun live(groupId: UUID, memberId: UUID): List<CardSubscription>
    fun byRequest(memberId: UUID, requestId: UUID): CardSubscription?
    fun find(id: UUID): CardSubscription?
    fun byProvider(accountId: UUID, subscriptionId: String?, checkoutId: String?, reference: String?): CardSubscription?
    fun insert(subscription: CardSubscription, requestId: UUID)
    fun checkout(id: UUID, checkout: HostedCheckout)
    fun activate(id: UUID, providerSubscriptionId: String, at: Instant): Boolean
    /** ACTIVE/AUTHORIZING → [status] com o motivo; marca o cancelamento remoto pendente. */
    fun end(id: UUID, status: String, endReason: String, cancelReason: String?, at: Instant): Boolean
    fun remoteCancelled(id: UUID)
    fun pendingRemoteCancels(): List<CardSubscription>
    fun withStatus(vararg statuses: String): List<CardSubscription>
    fun scheduleChange(id: UUID, amountCents: Long?, from: LocalDate?)
    fun changeNotified(id: UUID, at: Instant)
    fun applyChange(id: UUID, amountCents: Long, commissionCents: Long)
    fun aligned(id: UUID, dueDay: Int)
    fun refused(id: UUID, chargeId: UUID?)
    fun clearRefused(chargeIds: Collection<UUID>)
    /** Guarda a cobrança gerada pela assinatura e devolve quando ela foi vista pela primeira vez. */
    fun recordPayment(accountId: UUID, paymentId: String, subscriptionId: UUID, dueDate: LocalDate, valueCents: Long, at: Instant): Instant
    fun refusedNotified(accountId: UUID, paymentId: String, at: Instant): Boolean
    fun exemptRemoved(accountId: UUID, paymentId: String, at: Instant): Boolean
    fun history(groupId: UUID, memberId: UUID): List<RecurrenceHistoryItem>
    fun managerStatuses(groupId: UUID): List<MemberRecurrence>
}

/** [atAdhesion]: a cobrança feita na hora da adesão (o mês já em aberto), fora do dia de vencimento. */
data class RecurrenceHistoryItem(val month: LocalDate, val amountCents: Long, val paidAt: Instant, val atAdhesion: Boolean = false)
data class RecurrenceView(val status: String, val amountCents: Long, val nextChargeDate: LocalDate?, val refusedChargeId: UUID?,
    val history: List<RecurrenceHistoryItem>)
data class RecurrenceCheckout(val checkoutUrl: String)
data class MemberRecurrence(val memberUserId: UUID, val status: String)

enum class CancelReason { STOP_PLAYING, PREFER_PIX, CARD_PROBLEM, TOO_EXPENSIVE, OTHER, PREFER_NOT_SAY }

/** Mensalidade no cartão, "modo Netflix" (FIN-012, seção I do plano v1). */
class CardSubscriptions(private val store: SubscriptionStore, private val codes: PaymentCodeStore,
    private val athletes: AthletePayments, private val ledger: GroupChargeLedger, private val pricing: GroupReceivables,
    private val conditions: FinancialConditions, private val provider: SubscriptionProvider,
    private val notices: ReceivablesNotices, private val links: SettleLinkUrls, private val clock: Clock,
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")) {

    fun view(groupId: UUID, memberId: UUID): RecurrenceView? = store.transaction {
        val live = store.live(groupId, memberId).firstOrNull { it.status == "ACTIVE" } ?: store.live(groupId, memberId).firstOrNull()
            ?: return@transaction offer(groupId, memberId)
        val refused = live.refusedChargeId?.takeIf { id -> ledger.lock(listOf(id)).singleOrNull()?.status == "PENDING" }
        val status = if (live.status == "ACTIVE" && refused != null) "REFUSED" else live.status
        val dueDay = ledger.membership(groupId, memberId)?.dueDay
        RecurrenceView(status, live.amountCents, dueDay?.let { nextDue(it, today()) }, refused, store.history(groupId, memberId))
    }

    /**
     * Sem assinatura viva: `NONE` para o mensalista ativo num grupo em que ele consegue pagar pelo app (a oferta), com a
     * mensalidade que ele pagaria e o que já pagou no cartão; nulo para o avulso (e sem recebimento).
     */
    private fun offer(groupId: UUID, memberId: UUID): RecurrenceView? {
        val membership = ledger.membership(groupId, memberId)?.takeIf { it.active && it.mensalista } ?: return null
        val fee = membership.feeCents ?: return null
        val dueDay = membership.dueDay ?: return null
        val account = codes.receivingAccount(groupId) ?: return null
        if (!athletes.available(account)) return null
        val amount = pricing.price(groupId, fee)?.payableCents ?: fee
        return RecurrenceView("NONE", amount, nextDue(dueDay, today()), null, store.history(groupId, memberId))
    }

    fun get(groupId: UUID, request: FinancialRequest): FinancialResult<RecurrenceView?> = safely(request) {
        if (!store.transaction { ledger.isActiveMember(groupId, request.actorUserId) }) fail(FinancialError.NOT_FOUND)
        view(groupId, request.actorUserId)
    }

    /** Adere (FIN-012): só mensalista ativo, uma por pessoa por grupo; mensalidade do mês em aberto é cobrada na hora. */
    fun start(groupId: UUID, request: FinancialRequest): FinancialResult<RecurrenceCheckout> = safely(request) {
        open(groupId, request, replacing = false)
    }

    /** Trocar o cartão: um checkout novo; a assinatura atual é encerrada quando a nova ativar. */
    fun changeCard(groupId: UUID, request: FinancialRequest): FinancialResult<RecurrenceCheckout> = safely(request) {
        open(groupId, request, replacing = true)
    }

    private fun open(groupId: UUID, request: FinancialRequest, replacing: Boolean): RecurrenceCheckout {
        val member = request.actorUserId
        val subscription = store.transaction {
            store.byRequest(member, request.requestId)?.let { return@transaction it }
            val group = ledger.group(groupId) ?: fail(FinancialError.NOT_FOUND)
            val membership = ledger.membership(groupId, member)?.takeIf { it.active } ?: fail(FinancialError.NOT_FOUND)
            val fee = membership.feeCents
            val dueDay = membership.dueDay
            if (!membership.mensalista || fee == null || dueDay == null) fail(FinancialError.CONFLICT)
            val account = codes.receivingAccount(groupId) ?: fail(FinancialError.OPERATIONS_DISABLED)
            if (!athletes.available(account)) fail(FinancialError.OPERATIONS_DISABLED)
            if (!account.webhookReady) fail(FinancialError.CONFIGURATION_UNAVAILABLE)
            if (!codes.hasDocument(member)) fail(FinancialError.DOCUMENT_REQUIRED)
            store.lockMember(groupId, member)
            val live = store.live(groupId, member)
            val active = live.firstOrNull { it.status == "ACTIVE" }
            if (replacing && active == null) fail(FinancialError.NOT_FOUND)
            if (!replacing && active != null) fail(FinancialError.CONFLICT)
            live.firstOrNull { it.status == "AUTHORIZING" }?.let { pending ->
                if (pending.checkoutUrl != null && pending.createdAt.plusSeconds(50 * 60) > clock.instant()) return@transaction pending
                store.end(pending.id, "ABANDONED", "PAYER_CANCELLED", null, clock.instant())
            }
            val amount = active?.amountCents ?: (pricing.price(groupId, fee)?.payableCents ?: fee)
            val schedule = conditions.current(setOf(PaymentMethod.CARD), clock.instant(), account.accountId).singleOrNull()
                ?: fail(FinancialError.CONFIGURATION_UNAVAILABLE)
            val today = today()
            val month = today.withDayOfMonth(1)
            val current = ledger.monthlyCharge(groupId, member, month)
            val first = when {
                replacing -> nextDue(dueDay, today.plusDays(1))
                current?.status == "PENDING" -> today
                current == null && dueIn(month, dueDay) >= today -> dueIn(month, dueDay)
                else -> dueIn(month.plusMonths(1), dueDay)
            }
            // Cobrada na hora fora do dia de vencimento: a assinatura precisa ser realinhada depois de ativar.
            CardSubscription(UUID.randomUUID(), account.accountId, group.id, member, "AUTHORIZING", amount,
                FeeCalculator.commission(amount, schedule), null, null, null, first, active?.id, null, null, null, null, clock.instant(),
                dueDay.takeIf { first.dayOfMonth == dueIn(first.withDayOfMonth(1), it).dayOfMonth })
                .also { store.insert(it, request.requestId) }
        }
        subscription.checkoutUrl?.let { return RecurrenceCheckout(it) }
        val customer = store.transaction {
            codes.customer(subscription.accountId, member, ledger.displayName(member) ?: "Atleta Saqz",
                codes.document(member) ?: fail(FinancialError.DOCUMENT_REQUIRED))
        }
        val checkout = try {
            provider.createCheckout(subscription.accountId, subscription.id, customer, subscription.amountCents, subscription.commissionCents,
                subscription.firstDueDate) { id -> store.transaction { codes.saveCustomer(subscription.accountId, member, id) } }
        } catch (_: Exception) {
            store.transaction { store.end(subscription.id, "ABANDONED", "PAYER_CANCELLED", null, clock.instant()) }
            fail(FinancialError.PROVIDER_UNAVAILABLE)
        }
        store.transaction { store.checkout(subscription.id, checkout) }
        return RecurrenceCheckout(checkout.url)
    }

    /** Cancelar (FIN-012): motivo obrigatório; continua mensalista; o gestor é avisado. */
    fun cancel(groupId: UUID, reason: CancelReason, request: FinancialRequest): FinancialResult<RecurrenceView?> = safely(request) {
        val ended = store.transaction {
            val live = store.live(groupId, request.actorUserId)
            if (live.isEmpty()) return@transaction emptyList()
            live.filter { store.end(it.id, "CANCELLED", "PAYER_CANCELLED", reason.name, clock.instant()) }.also { cancelled ->
                if (cancelled.any { it.status == "ACTIVE" }) {
                    val name = ledger.displayName(request.actorUserId) ?: "Atleta"
                    notices.publish(groupId, ledger.managers(groupId) - request.actorUserId, "$name cancelou o automático",
                        "As próximas mensalidades chegam para pagar por Pix.",
                        UUID.nameUUIDFromBytes("subscription-cancelled:${cancelled.first().id}".toByteArray()))
                }
            }
        }
        ended.forEach { cancelRemote(it.id) }
        view(groupId, request.actorUserId)
    }

    /** Gestor (dono e admins): quem está no cartão e quem teve o cartão recusado. */
    fun members(groupId: UUID, accountId: UUID, request: FinancialRequest): FinancialResult<List<MemberRecurrence>> = safely(request) {
        store.transaction {
            if (codes.receivingAccount(groupId)?.accountId != accountId || request.actorUserId !in ledger.managers(groupId)) fail(FinancialError.NOT_FOUND)
            store.managerStatuses(groupId)
        }
    }

    /** Pagamento recusado no cartão (FIN-012): avisos uma vez por cobrança; a cobrança fica em aberto e a assinatura segue. */
    fun refused(subscription: CardSubscription, payment: ObservedPayment) {
        val month = (payment.dueDate ?: today()).withDayOfMonth(1)
        val first = store.transaction { store.refusedNotified(subscription.accountId, payment.id, clock.instant()) }
        if (!first) return
        val charge = store.transaction {
            ledger.monthlyCharge(subscription.groupId, subscription.memberId, month).also { store.refused(subscription.id, it?.id) }
        }
        val pix = charge?.takeIf { it.status == "PENDING" }?.let { athletes.internalPix(subscription.groupId, subscription.memberId, listOf(it.id)) }
        val monthName = PaymentSettlement.month(month)
        val amount = brl(charge?.payableCents ?: payment.valueCents)
        store.transaction {
            val name = ledger.displayName(subscription.memberId) ?: "Atleta"
            val link = links.monthUrl(subscription.groupId, month)
            notices.publish(subscription.groupId, setOf(subscription.memberId), "Seu cartão não passou",
                "A mensalidade de $monthName continua em aberto. Atualize o cartão ou pague por Pix.",
                UUID.nameUUIDFromBytes("refused-athlete:${payment.id}".toByteArray()),
                PrivateWhatsApp("Seu cartão não passou. A mensalidade de $monthName ($amount) continua em aberto.\n" +
                    "Atualize o cartão no app ou pague com o código da próxima mensagem.", "Atualizar cartão", link, pix?.payload),
                SettleTarget(billingMonth = month))
            notices.publish(subscription.groupId, ledger.managers(subscription.groupId) - subscription.memberId, "Cartão recusado",
                "A mensalidade de $monthName de $name não passou no cartão. A pessoa já foi avisada.",
                UUID.nameUUIDFromBytes("refused-manager:${payment.id}".toByteArray()))
        }
    }

    /** Ativa as assinaturas cujo checkout virou assinatura no Asaas e encerra a anterior na troca de cartão. */
    fun activatePending() {
        store.transaction { store.withStatus("AUTHORIZING") }.forEach { pending ->
            val checkout = pending.checkoutId ?: return@forEach
            val remote = try { provider.subscriptionOfCheckout(pending.accountId, checkout) } catch (_: Exception) { null }
            if (remote != null) activated(pending, remote)
            else if (pending.createdAt.plusSeconds(24 * 3600) < clock.instant()) {
                store.transaction { store.end(pending.id, "ABANDONED", "PAYER_CANCELLED", null, clock.instant()) }
                cancelRemote(pending.id)
            }
        }
    }

    fun activated(subscription: CardSubscription, providerSubscriptionId: String) {
        val replaced = store.transaction {
            if (!store.activate(subscription.id, providerSubscriptionId, clock.instant())) null
            else subscription.replacesId?.takeIf { store.end(it, "ENDED", "CARD_REPLACED", null, clock.instant()) }
        }
        replaced?.let(::cancelRemote)
    }

    /**
     * Corte automático (FIN-012): virou avulso, saiu do grupo, grupo desligado ou excluído, gestor sem direito. As vencidas
     * continuam em aberto; só a assinatura para no Asaas.
     */
    fun enforceCutoffs() {
        store.transaction { store.withStatus("ACTIVE", "AUTHORIZING") }.forEach { subscription ->
            val reason = store.transaction {
                val group = ledger.group(subscription.groupId) ?: return@transaction "GROUP_DELETED"
                val membership = ledger.membership(group.id, subscription.memberId)
                val account = codes.receivingAccount(group.id)
                when {
                    membership == null || !membership.active -> "MEMBER_LEFT"
                    !membership.mensalista -> "NOT_MENSALISTA"
                    account == null || account.accountId != subscription.accountId -> "GROUP_DISABLED"
                    !athletes.entitled(account) -> "INELIGIBLE"
                    else -> null
                }?.takeIf { store.end(subscription.id, "ENDED", it, null, clock.instant()) }
            }
            if (reason != null) cancelRemote(subscription.id)
        }
        store.transaction { store.pendingRemoteCancels() }.forEach { cancelRemote(it.id) }
    }

    /**
     * Reajuste (FIN-012): o valor novo vale a partir do primeiro vencimento que esteja a 7 dias ou mais; o aviso sai nessa
     * hora. A assinatura no Asaas troca de valor depois do último vencimento com o valor antigo, então nenhuma cobrança já
     * gerada muda e a primeira com o valor novo é a do vencimento avisado.
     */
    fun adjustValues() {
        val today = today()
        store.transaction { store.withStatus("ACTIVE") }.forEach { subscription ->
            val membership = store.transaction { ledger.membership(subscription.groupId, subscription.memberId) } ?: return@forEach
            val fee = membership.feeCents ?: return@forEach
            val dueDay = membership.dueDay ?: return@forEach
            val target = store.transaction { pricing.price(subscription.groupId, fee)?.payableCents } ?: fee
            if (target == subscription.amountCents) {
                if (subscription.nextAmountCents != null) store.transaction { store.scheduleChange(subscription.id, null, null) }
                return@forEach
            }
            var current = subscription
            if (current.nextAmountCents != target) {
                val from = nextDue(dueDay, today.plusDays(7))
                store.transaction { store.scheduleChange(current.id, target, from) }
                current = current.copy(nextAmountCents = target, nextAmountFrom = from, changeNotifiedAt = null)
            }
            if (current.changeNotifiedAt == null) notifyChange(current)
            val from = current.nextAmountFrom ?: return@forEach
            val previousDue = dueIn(from.minusMonths(1).withDayOfMonth(1), dueDay)
            if (today > previousDue && current.providerSubscriptionId != null) {
                val schedule = conditions.current(setOf(PaymentMethod.CARD), clock.instant(), current.accountId).singleOrNull() ?: return@forEach
                val commission = FeeCalculator.commission(target, schedule)
                val updated = try { provider.updateSubscriptionValue(current.accountId, current.providerSubscriptionId!!, target, commission) }
                    catch (_: Exception) { false }
                if (updated) store.transaction { store.applyChange(current.id, target, commission) }
            }
        }
    }

    /**
     * Realinha o ciclo da assinatura ao dia de vencimento do atleta (FIN-012, protótipo Netflix-Gerenciar: "Julho · cobrado
     * na adesão, 22/07", "Agosto · cobrado em 10/08"): depois da adesão que cobrou na hora e quando o dia do atleta muda.
     * A próxima cobrança vai para o primeiro vencimento de um mês ainda não coberto; cobrança futura já gerada na data errada
     * é apagada para o Asaas gerar de novo na data certa.
     */
    fun realignDueDates() {
        val today = today()
        store.transaction { store.withStatus("ACTIVE") }.forEach { subscription ->
            val remote = subscription.providerSubscriptionId ?: return@forEach
            val dueDay = store.transaction { ledger.membership(subscription.groupId, subscription.memberId)?.dueDay } ?: return@forEach
            if (subscription.alignedDueDay == dueDay) return@forEach
            var next = nextDue(dueDay, today.plusDays(1))
            val covered = store.transaction {
                val charge = ledger.monthlyCharge(subscription.groupId, subscription.memberId, next.withDayOfMonth(1))
                (charge != null && charge.status != "PENDING") || subscription.firstDueDate.withDayOfMonth(1) == next.withDayOfMonth(1)
            }
            if (covered) next = dueIn(next.withDayOfMonth(1).plusMonths(1), dueDay)
            val moved = try { provider.realignSubscription(subscription.accountId, remote, next) } catch (_: Exception) { false }
            if (!moved) return@forEach
            val stale = try { provider.subscriptionPayments(subscription.accountId, remote) } catch (_: Exception) { return@forEach }
                .filter { it.status == "PENDING" && it.dueDate > today && it.dueDate != next }
            if (stale.all { runCatching { provider.cancelPayment(subscription.accountId, it.id) }.getOrDefault(false) })
                store.transaction { store.aligned(subscription.id, dueDay) }
        }
    }

    private fun notifyChange(subscription: CardSubscription) {
        val from = subscription.nextAmountFrom ?: return
        val next = brl(subscription.nextAmountCents ?: return)
        val day = from.format(DateTimeFormatter.ofPattern("dd/MM"))
        store.transaction {
            notices.publish(subscription.groupId, setOf(subscription.memberId), "Mensalidade com valor novo",
                "A partir de $day, $next no automático do seu cartão.",
                UUID.nameUUIDFromBytes("subscription-change:${subscription.id}:$from:${subscription.nextAmountCents}".toByteArray()),
                PrivateWhatsApp("A mensalidade muda de ${brl(subscription.amountCents)} para $next a partir de $day.\n" +
                    "Ela continua no automático no seu cartão. Se quiser cancelar, é pelo app.", "Ver no app",
                    links.monthUrl(subscription.groupId, from.withDayOfMonth(1))),
                SettleTarget(billingMonth = from.withDayOfMonth(1)))
            store.changeNotified(subscription.id, clock.instant())
        }
    }

    /** Isenção (MEM-003): o mês isento não é cobrado; a cobrança daquele mês no Asaas sai antes do vencimento. */
    fun removeExemptMonths() {
        val today = today()
        store.transaction { store.withStatus("ACTIVE") }.forEach { subscription ->
            val exempt = store.transaction { ledger.membership(subscription.groupId, subscription.memberId)?.exempt } ?: return@forEach
            val remote = subscription.providerSubscriptionId ?: return@forEach
            if (!exempt) return@forEach
            val payments = try { provider.subscriptionPayments(subscription.accountId, remote) } catch (_: Exception) { return@forEach }
            payments.filter { it.status == "PENDING" && it.dueDate >= today }.forEach { payment ->
                val removed = try { provider.cancelPayment(subscription.accountId, payment.id) } catch (_: Exception) { false }
                if (removed) store.transaction {
                    store.recordPayment(subscription.accountId, payment.id, subscription.id, payment.dueDate, payment.valueCents, clock.instant())
                    store.exemptRemoved(subscription.accountId, payment.id, clock.instant())
                }
            }
        }
    }

    private fun cancelRemote(id: UUID) {
        val subscription = store.transaction { store.find(id) } ?: return
        val done = try {
            when {
                subscription.providerSubscriptionId != null -> provider.cancelSubscription(subscription.accountId, subscription.providerSubscriptionId)
                subscription.checkoutId != null -> provider.cancelCheckout(subscription.accountId, subscription.checkoutId)
                else -> true
            }
        } catch (_: Exception) { false }
        if (done) store.transaction { store.remoteCancelled(id) }
    }

    private fun today() = LocalDate.now(clock.withZone(zone))
    private fun dueIn(month: LocalDate, dueDay: Int) = month.withDayOfMonth(dueDay.coerceIn(1, month.lengthOfMonth()))
    /** Primeiro vencimento no dia [dueDay] a partir de [from] (inclusive). */
    private fun nextDue(dueDay: Int, from: LocalDate): LocalDate {
        val inMonth = dueIn(from.withDayOfMonth(1), dueDay)
        return if (inMonth >= from) inMonth else dueIn(from.withDayOfMonth(1).plusMonths(1), dueDay)
    }

    private class Failed(val code: FinancialError) : RuntimeException()
    private fun fail(error: FinancialError): Nothing = throw Failed(error)
    private fun <T> safely(request: FinancialRequest, block: () -> T): FinancialResult<T> = try {
        FinancialResult.Success(block(), request.requestId)
    } catch (e: Failed) { FinancialResult.Failure(e.code, request.requestId) }
    catch (_: FinancialRequestConflict) { FinancialResult.Failure(FinancialError.CONFLICT, request.requestId) }
    catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId) }
}
