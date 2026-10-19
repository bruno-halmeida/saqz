package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Quem cobra a assinatura atrasada: o texto e o "Pagar agora" mudam por origem. */
enum class PastDueProvider { ASAAS, APP_STORE, GOOGLE_PLAY }

/** Marcos do aviso, em dias da carência: o dia do vencimento, três dias depois e o último dia. */
internal const val PAST_DUE_DAY_ZERO = 0
internal const val PAST_DUE_DAY_THREE = 3
internal const val PAST_DUE_LAST_DAY = 7

/**
 * Assinatura em atraso que ainda dá acesso. [since] é a âncora do atraso (chave da trava). [dueDay] é o dia do vencimento, ou nulo
 * quando a origem não informa (Google Play); [lastDay] é o último dia com acesso, o "congelam hoje". [accessEndsAt] é o instante em
 * que o acesso acaba quando ele não cai na virada do dia (lojas: o horário acompanha a compra); nulo no site, onde é a meia-noite.
 */
data class PastDueSubscription(
    val ownerUserId: UUID,
    val provider: PastDueProvider,
    val since: Instant,
    val dueDay: LocalDate?,
    val lastDay: LocalDate,
    val asaasSubscriptionId: String? = null,
    val accessEndsAt: Instant? = null,
)

/** Cobrança vencida da mensalidade, lida do webhook PAYMENT_OVERDUE guardado: o id e o link da fatura do Asaas. */
data class OverdueInvoice(val paymentId: String, val invoiceUrl: String?)

interface SubscriptionPastDueRepository {
    /** Atrasos que ainda dão acesso e cujo dono não tem outro plano em dia. */
    fun pastDue(now: Instant): List<PastDueSubscription>

    /** A cobrança vencida da mensalidade (nunca a de upgrade): do mesmo `asaasSubscriptionId`, com vencimento a partir de [dueDay]. */
    fun overdueInvoice(subscription: PastDueSubscription): OverdueInvoice?

    /**
     * Reserva todos os marcos de [due] que ainda não existem. Devolve true quando o mais apertado (o maior, aqui os marcos crescem
     * com o atraso) foi reservado por esta chamada: só então o aviso sai.
     */
    fun claim(subscription: PastDueSubscription, due: List<Int>): Boolean

    /** Desfaz a reserva de [milestone] quando o aviso não saiu, para a próxima rodada tentar de novo. */
    fun release(subscription: PastDueSubscription, milestone: Int)
}

/**
 * Job diário: no dia do vencimento sem pagamento, três dias depois e no último dia da carência, o dono recebe, uma vez por marco, o
 * aviso de pagamento pendente na central, no push e no WhatsApp. Para sozinho quando ele paga, cancela ou os grupos congelam.
 * Nas lojas não há link: o texto manda atualizar a forma de pagamento. Onde a origem não informa o vencimento (Google Play), só o
 * último dia é avisado.
 */
class SubscriptionPastDueReminders(
    private val repository: SubscriptionPastDueRepository,
    private val notice: TrialEndingNotice,
    private val whatsApp: TrialEndingWhatsApp,
    gateway: () -> AsaasGateway?,
    private val clock: Clock,
    private val zone: ZoneId = TRIAL_ZONE,
) {
    private val invoices = OverdueInvoiceResolver(repository, gateway)

    /** Devolve quantos avisos saíram nesta rodada. */
    fun run(): Int {
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        var sent = 0
        repository.pastDue(now).forEach { subscription ->
            val due = dueMilestones(subscription, today, now)
            if (due.isEmpty() || !repository.claim(subscription, due)) return@forEach
            val milestone = due.max()
            val link = if (subscription.provider == PastDueProvider.ASAAS) {
                when (val invoice = invoices.resolve(subscription)) {
                    InvoiceLookup.Paid -> {
                        repository.release(subscription, milestone)
                        JobLog.item("subscription_past_due", "reminder_skipped_paid", "userId" to subscription.ownerUserId, "milestone" to milestone)
                        return@forEach
                    }
                    is InvoiceLookup.Link -> invoice.url
                }
            } else {
                null
            }
            val message = pastDueMessage(milestone, subscription.provider, link)
            val published = runCatching { notice.publish(subscription.ownerUserId, message.body, message.push) }.getOrDefault(false)
            if (!published) {
                repository.release(subscription, milestone)
                JobLog.itemFailed("subscription_past_due", "reminder_not_published", "NOT_PUBLISHED", "userId" to subscription.ownerUserId, "milestone" to milestone)
                return@forEach
            }
            runCatching { whatsApp.send(subscription.ownerUserId, message.body) }
            JobLog.item("subscription_past_due", "reminder_sent", "userId" to subscription.ownerUserId, "milestone" to milestone, "provider" to subscription.provider)
            sent++
        }
        return sent
    }
}

/**
 * Quanto antes do fim do acesso a última rodada pode mandar o último aviso: a rodada é diária, então um fim que cai antes da próxima
 * (com 30 minutos de folga para ela atrasar) é avisado nesta. Sem isso, o acesso de loja que acaba antes das 10h de São Paulo acaba sem aviso.
 */
internal val LAST_NOTICE_LEAD: Duration = Duration.ofMinutes(24 * 60 + 30)

/**
 * Marcos devidos hoje. Fora da carência (depois do último dia) não sai nada: os grupos já congelaram. Rodada perdida reserva todos
 * os marcos já passados e avisa só o mais apertado. O último dia sai também na véspera quando o acesso acaba antes da próxima rodada.
 */
internal fun dueMilestones(subscription: PastDueSubscription, today: LocalDate, now: Instant): List<Int> {
    if (today.isAfter(subscription.lastDay)) return emptyList()
    val dueDay = subscription.dueDay
    return buildList {
        if (dueDay != null && !today.isBefore(dueDay)) add(PAST_DUE_DAY_ZERO)
        if (dueDay != null && !today.isBefore(dueDay.plusDays(PAST_DUE_DAY_THREE.toLong()))) add(PAST_DUE_DAY_THREE)
        val endsBeforeNextRound = subscription.accessEndsAt?.let { !it.isAfter(now.plus(LAST_NOTICE_LEAD)) } == true
        if (!today.isBefore(subscription.lastDay) || endsBeforeNextRound) add(PAST_DUE_LAST_DAY)
    }
}

/** Central e WhatsApp ([body]) e o push curto ([push]) de um marco. */
data class PastDueMessage(val body: String, val push: String)

/**
 * Textos aprovados em 18/10 (DEB-076). [link] é a fatura do Asaas; sem ele o aviso sai sem o "Pagar agora". Nas lojas o
 * "Pagar agora: <link>" vira "Atualize a forma de pagamento na App Store." (ou "no Google Play."). O push curto abre o app.
 */
fun pastDueMessage(milestone: Int, provider: PastDueProvider, link: String?): PastDueMessage {
    val pay = when (provider) {
        PastDueProvider.ASAAS -> link?.let { "Pagar agora: $it" }
        PastDueProvider.APP_STORE -> "Atualize a forma de pagamento na App Store."
        PastDueProvider.GOOGLE_PLAY -> "Atualize a forma de pagamento no Google Play."
    }
    fun withPay(text: String) = if (pay == null) text else "$text $pay"
    return when (milestone) {
        PAST_DUE_DAY_ZERO -> PastDueMessage(
            withPay("Seu pagamento do Saqz está pendente. Seus grupos serão congelados em breve se não houver pagamento."),
            "Seu pagamento do Saqz está pendente. Toque para regularizar.",
        )
        PAST_DUE_DAY_THREE -> PastDueMessage(
            withPay("Seu pagamento do Saqz continua pendente. Seus grupos serão congelados em breve se não houver pagamento."),
            "Seu pagamento do Saqz continua pendente. Toque para regularizar.",
        )
        else -> PastDueMessage(
            when (provider) {
                PastDueProvider.ASAAS ->
                    if (link == null) "Seus grupos congelam hoje. Pague agora para a galera não ficar sem lista."
                    else "Seus grupos congelam hoje. Pague agora para a galera não ficar sem lista: $link"
                else -> withPay("Seus grupos congelam hoje.")
            },
            "Seus grupos congelam hoje. Toque para regularizar.",
        )
    }
}

/** Resultado da conferência da fatura vencida da mensalidade. */
sealed interface InvoiceLookup {
    /** A cobrança já está paga (o webhook só atrasou): não há o que cobrar. */
    data object Paid : InvoiceLookup

    /** [url] é a fatura a pagar; nulo quando não há fatura vencida para mostrar. */
    data class Link(val url: String?) : InvoiceLookup
}

/**
 * Link da fatura vencida da mensalidade. Com o Asaas à mão, a cobrança é conferida: já paga (o webhook só atrasou) não cobra;
 * a que não está vencida não dá link; a vencida dá o link atual. Sem o Asaas (ou se ele falhar), vale o link do webhook.
 * Usado pelo aviso de atraso e pelo "Pagar agora" do `/subscriptions/me`, que mostram sempre a mesma fatura.
 */
class OverdueInvoiceResolver(
    private val repository: SubscriptionPastDueRepository,
    private val gateway: () -> AsaasGateway?,
) {
    fun resolve(subscription: PastDueSubscription): InvoiceLookup {
        val invoice = repository.overdueInvoice(subscription) ?: return InvoiceLookup.Link(null)
        val snapshot = runCatching { gateway()?.findPayment(invoice.paymentId) }.getOrNull()
            ?: return InvoiceLookup.Link(invoice.invoiceUrl)
        return when (snapshot.status) {
            in PAID_STATUSES -> InvoiceLookup.Paid
            "OVERDUE" -> InvoiceLookup.Link(snapshot.invoiceUrl ?: invoice.invoiceUrl)
            else -> InvoiceLookup.Link(null)
        }
    }

    private companion object {
        val PAID_STATUSES = setOf("RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH")
    }
}
