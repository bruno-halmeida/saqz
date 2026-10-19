package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** O texto que sai de fato no WhatsApp particular do dono (fim do teste e atraso) e o rótulo do log quando a entrega falha. */
class PrivateTrialEndingWhatsAppTest {
    private val owner = UUID.randomUUID()
    private val sent = mutableListOf<WhatsAppNotification>()
    private var delivery: WhatsAppDelivery = WhatsAppDelivery.Accepted
    private val sender = NotificationWhatsAppSender { message -> sent += message; delivery }
    private val logger = LoggerFactory.getLogger(PrivateTrialEndingWhatsApp::class.java) as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach fun capture() { appender.start(); logger.addAppender(appender) }

    @AfterEach fun release() { logger.detachAppender(appender); appender.stop() }

    private fun whatsApp(job: String = "trial_ending", phone: String? = "+5511987654321", enabled: Boolean = true) =
        PrivateTrialEndingWhatsApp({ phone }, { if (enabled) sender else null }, job)

    @Test
    fun `texto sem Saqz ganha o prefixo`() {
        whatsApp().send(owner, "Seu teste termina amanhã. Assine para a galera não ficar sem lista: https://saqz.app/assinar")

        assertEquals(
            listOf("Saqz: Seu teste termina amanhã. Assine para a galera não ficar sem lista: https://saqz.app/assinar"),
            sent.map { it.body },
        )
        assertEquals("+5511987654321", sent.single().phone)
    }

    @Test
    fun `texto que ja fala do Saqz sai como veio`() {
        val body = "Seu pagamento do Saqz está pendente. Seus grupos serão congelados em breve se não houver pagamento. Pagar agora: https://www.asaas.com/i/abc"
        whatsApp("subscription_past_due").send(owner, body)

        assertEquals(listOf(body), sent.map { it.body })
    }

    @Test
    fun `sem WhatsApp ligado ou sem telefone nada sai`() {
        whatsApp(enabled = false).send(owner, "Seu teste termina hoje.")
        whatsApp(phone = null).send(owner, "Seu teste termina hoje.")
        whatsApp(phone = "  ").send(owner, "Seu teste termina hoje.")

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a falha de entrega entra no log com o rotulo do proprio aviso`() {
        delivery = WhatsAppDelivery.Failed
        whatsApp("trial_ending").send(owner, "Seu teste termina hoje.")
        whatsApp("subscription_past_due").send(owner, "Seu pagamento do Saqz continua pendente.")

        assertEquals(
            listOf("trial_ending_whatsapp_failed delivery=Failed", "subscription_past_due_whatsapp_failed delivery=Failed"),
            appender.list.map { it.formattedMessage },
        )
    }
}
