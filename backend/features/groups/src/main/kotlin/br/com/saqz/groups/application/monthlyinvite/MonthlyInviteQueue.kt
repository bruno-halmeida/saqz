package br.com.saqz.groups.application.monthlyinvite

import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/** O que a fila precisa de uma linha na hora de mandar. */
data class MonthlyInviteSendContext(
    val inviteeId: UUID,
    val name: String,
    val phone: String,
    val status: MonthlyInviteeStatus,
    val groupName: String,
    /** Quem cadastrou (ou o dono, se a conta de quem cadastrou sumiu). */
    val senderName: String,
    val link: MonthlyInviteLink?,
)

/** Estado de cada convidado no banco; a fila em si fica em memória (CONV-012). */
interface MonthlyInviteQueueStore {
    fun context(inviteeId: UUID): MonthlyInviteSendContext?

    fun markSent(inviteeId: UUID, attempts: Int, sentAt: Instant)

    fun markFailed(inviteeId: UUID, attempts: Int)

    fun markNoWhatsApp(inviteeId: UUID)

    /** Troca o estado das linhas pendentes (Na fila ↔ Pausado), sem mexer nas que já saíram. */
    fun markPending(inviteeIds: List<UUID>, status: MonthlyInviteeStatus)

    /** Linhas que estavam esperando o envio quando o backend caiu, na ordem de chegada. */
    fun pendingAtStartup(): List<UUID>
}

/**
 * Fila global do convite de mensalistas (CONV-012): um envio por vez em todo o backend, em ordem de chegada, com
 * espera sorteada de 5 a 15 s entre mensagens. Antes de mandar, confere se o número tem WhatsApp (`false` claro
 * vira "Sem WhatsApp"; sem resposta, segue). Cada mensagem tem 3 tentativas com intervalo crescente; esgotadas, a
 * linha fica "Falhou". Três falhas seguidas pausam a fila para todos por 10 minutos; depois ela tenta um envio:
 * passou, segue; falhou, pausa de novo. Só [processNext] (o trabalhador, uma thread) anda a fila.
 */
class MonthlyInviteQueue(
    private val store: MonthlyInviteQueueStore,
    private val sender: NotificationWhatsAppSender,
    private val numberCheck: WhatsAppNumberCheck,
    private val linkFactory: InviteLinkFactory,
    private val clock: Clock,
    private val nextDelay: () -> Duration = ::randomDelay,
    private val sleep: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
) : MonthlyInviteDispatcher {
    private val lock = Object()
    private val pending = ArrayDeque<UUID>()
    private var pausedUntil: Instant? = null
    private var consecutiveFailures = 0

    override fun pausedUntil(): Instant? = synchronized(lock) { pausedUntil }

    override fun enqueue(ids: List<UUID>) = synchronized(lock) {
        // Reenviar quem já espera leva a pessoa para o fim da fila, sem duplicar.
        ids.forEach {
            pending.remove(it)
            pending.addLast(it)
        }
        lock.notifyAll()
    }

    /** Ao subir: quem estava esperando volta à fila; quem já recebeu ("Aguardando") fica como está. */
    fun restore() {
        val ids = store.pendingAtStartup()
        store.markPending(ids, MonthlyInviteeStatus.QUEUED)
        enqueue(ids)
    }

    /** Espera até haver o que mandar (ou o tempo acabar). */
    fun awaitWork(timeout: Duration) = synchronized(lock) {
        if (pending.isEmpty()) lock.wait(timeout.toMillis().coerceAtLeast(1))
    }

    fun pendingIds(): List<UUID> = synchronized(lock) { pending.toList() }

    /** Manda a próxima mensagem da fila; false quando a fila está vazia. */
    fun processNext(): Boolean {
        if (synchronized(lock) { pending.isEmpty() }) return false
        pausedUntil()?.let { until ->
            val now = clock.instant()
            if (now.isBefore(until)) sleep(Duration.between(now, until))
        }
        val id = synchronized(lock) { pending.removeFirstOrNull() } ?: return false
        val outcome = deliver(id)
        when (outcome) {
            Outcome.SENT -> onSent()
            Outcome.FAILED -> onFailed()
            Outcome.SKIPPED -> Unit
        }
        if (outcome != Outcome.SKIPPED) sleep(nextDelay())
        return true
    }

    private fun onSent() {
        consecutiveFailures = 0
        val resumed = synchronized(lock) {
            if (pausedUntil == null) return
            pausedUntil = null
            pending.toList()
        }
        store.markPending(resumed, MonthlyInviteeStatus.QUEUED)
    }

    private fun onFailed() {
        consecutiveFailures += 1
        val paused = synchronized(lock) {
            if (pausedUntil == null && consecutiveFailures < FAILURES_TO_PAUSE) return
            pausedUntil = clock.instant().plus(PAUSE)
            pending.toList()
        }
        store.markPending(paused, MonthlyInviteeStatus.PAUSED)
    }

    private fun deliver(id: UUID): Outcome {
        val context = store.context(id) ?: return Outcome.SKIPPED
        if (context.status != MonthlyInviteeStatus.QUEUED && context.status != MonthlyInviteeStatus.PAUSED) return Outcome.SKIPPED
        val code = context.link?.takeIf { it.state(clock.instant()) == MonthlyInviteLinkState.ACTIVE }?.shortCode
        if (code == null) {
            // Link invalidado (ou vencido) enquanto a linha esperava: não manda link morto; "Reenviar" gera um novo.
            store.markFailed(id, 0)
            return Outcome.SKIPPED
        }
        if (numberCheck.hasWhatsApp(context.phone) == false) {
            store.markNoWhatsApp(id)
            return Outcome.SKIPPED
        }
        val message = WhatsAppNotification(
            notificationId = 0,
            phone = context.phone,
            body = body(context, linkFactory.create(InviteCode.fromShort(code)).toString()),
            trackId = "monthly-invite-$id",
        )
        for (attempt in 1..MAX_ATTEMPTS) {
            if (sender.send(message) == WhatsAppDelivery.Accepted) {
                store.markSent(id, attempt, clock.instant())
                return Outcome.SENT
            }
            if (attempt < MAX_ATTEMPTS) sleep(RETRY_STEP.multipliedBy(attempt.toLong()))
        }
        store.markFailed(id, MAX_ATTEMPTS)
        return Outcome.FAILED
    }

    private enum class Outcome { SENT, FAILED, SKIPPED }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val FAILURES_TO_PAUSE = 3
        val PAUSE: Duration = Duration.ofMinutes(10)
        val RETRY_STEP: Duration = Duration.ofSeconds(10)
        val MIN_DELAY: Duration = Duration.ofSeconds(5)
        val MAX_DELAY: Duration = Duration.ofSeconds(15)

        /** Texto fixo (CONV-012); só os nomes e o link mudam. */
        fun body(context: MonthlyInviteSendContext, link: String): String =
            "E aí, ${context.name}! 🏐 O ${context.senderName} te colocou na lista de mensalistas do ${context.groupName} " +
                "aqui no Saqz. Entra por aqui e confirma presença nos jogos direto pelo app: $link\n" +
                "Se não era pra você, é só ignorar."

        fun randomDelay(): Duration =
            Duration.ofMillis(ThreadLocalRandom.current().nextLong(MIN_DELAY.toMillis(), MAX_DELAY.toMillis() + 1))
    }
}
