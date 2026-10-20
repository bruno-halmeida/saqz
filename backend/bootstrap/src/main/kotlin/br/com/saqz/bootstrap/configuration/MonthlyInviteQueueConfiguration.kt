package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.monthlyinvite.JdbcMonthlyInviteQueueStore
import br.com.saqz.groups.adapter.output.link.PublicInviteLinkFactory
import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteQueue
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration
import javax.sql.DataSource

/**
 * Fila do convite de mensalistas (CONV-012). Só existe com o WhatsApp ligado; o backend roda com uma réplica, então
 * um trabalhador (uma thread) garante "um envio por vez" em todo o backend.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.whatsapp", name = ["enabled"], havingValue = "true")
class MonthlyInviteQueueConfiguration {
    @Bean
    fun monthlyInviteQueue(
        dataSource: DataSource,
        sender: NotificationWhatsAppSender,
        numberCheck: WhatsAppNumberCheck,
        linkFactory: PublicInviteLinkFactory,
        clock: Clock,
    ) = MonthlyInviteQueue(JdbcMonthlyInviteQueueStore(dataSource), sender, numberCheck, linkFactory, clock)

    @Bean
    fun monthlyInviteQueueWorker(queue: MonthlyInviteQueue) = MonthlyInviteQueueWorker(queue)
}

class MonthlyInviteQueueWorker(private val queue: MonthlyInviteQueue) : SmartLifecycle {
    @Volatile private var thread: Thread? = null

    override fun start() {
        thread = Thread({ loop() }, "monthly-invite-queue").apply {
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        thread?.interrupt()
        thread = null
    }

    override fun isRunning(): Boolean = thread?.isAlive == true

    private fun loop() {
        runCatching { queue.restore() }.onFailure { log.warn("monthly_invite_queue restore failed", it) }
        while (!Thread.currentThread().isInterrupted) {
            try {
                queue.awaitWork(IDLE_WAIT)
                while (queue.processNext()) Unit
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: RuntimeException) {
                log.warn("monthly_invite_queue send failed", error)
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MonthlyInviteQueueWorker::class.java)
        val IDLE_WAIT: Duration = Duration.ofMinutes(1)
    }
}
