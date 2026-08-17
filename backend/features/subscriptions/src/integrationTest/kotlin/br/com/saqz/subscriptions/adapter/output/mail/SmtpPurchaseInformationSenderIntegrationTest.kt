package br.com.saqz.subscriptions.adapter.output.mail

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import java.net.ServerSocket
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSenderImpl
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmtpPurchaseInformationSenderIntegrationTest {
    private val smtp = GreenMail(ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP))
    private val recipient = "owner@example.test"
    private val from = "nao-responda@saqz.local"
    private val purchaseUrl = "https://checkout.test/assinar/"
    private lateinit var sender: SmtpPurchaseInformationSender

    @BeforeAll
    fun startSmtp() {
        smtp.start()
        sender = SmtpPurchaseInformationSender(
            greenMailSender(),
            from,
            purchaseUrl,
        )
    }

    @BeforeEach
    fun purgeMessages() {
        smtp.purgeEmailFromAllMailboxes()
    }

    @AfterAll
    fun stopSmtp() {
        smtp.stop()
    }

    @Test
    fun `sends recipient from subject and clear purchase instructions with configured URL`() {
        sender.send(recipient)

        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
        val message = smtp.receivedMessages.single()
        val body = message.content.toString().replace("\r\n", "\n").trim()

        assertEquals(recipient, message.allRecipients.single().toString())
        assertEquals(from, message.getHeader("From", null))
        assertEquals("Instruções para assinar o Saqz", message.subject)
        assertEquals(
            """
            Olá!

            Para iniciar sua assinatura do Saqz, acesse:
            $purchaseUrl

            Se você não solicitou este e-mail, ignore esta mensagem.
            """.trimIndent(),
            body,
        )
        assertFalse(body.contains(recipient))
        assertFalse(body.contains("R$"))
        assertFalse(body.contains("token", ignoreCase = true))
        assertFalse(body.contains("?"))
        assertFalse(body.contains("#"))
        assertEquals(1, Regex("https://[^\\s]+/").findAll(body).count())
        assertTrue(body.contains(purchaseUrl))
    }

    @Test
    fun `propagates SMTP delivery failure`() {
        val unavailablePort = ServerSocket(0).use { it.localPort }
        val unavailableSender = JavaMailSenderImpl().apply {
            host = "127.0.0.1"
            port = unavailablePort
            javaMailProperties["mail.smtp.connectiontimeout"] = "1000"
            javaMailProperties["mail.smtp.timeout"] = "1000"
            javaMailProperties["mail.smtp.writetimeout"] = "1000"
        }
        val mailer = SmtpPurchaseInformationSender(unavailableSender, from, purchaseUrl)

        assertFailsWith<MailException> { mailer.send(recipient) }
        assertTrue(smtp.receivedMessages.isEmpty())
    }

    @Test
    fun `rejects URLs that are not the exact HTTPS signing path`() {
        listOf(
            "http://checkout.test/assinar/",
            "https://checkout.test/checkout/",
            "https://checkout.test/assinar",
            "https://checkout.test:443/assinar/",
            "https://checkout.test/assinar/?token=secret",
            "https://checkout.test/assinar/#fragment",
            "https://user@example.test/assinar/",
        ).forEach { invalidUrl ->
            assertFailsWith<IllegalArgumentException>(invalidUrl) {
                SmtpPurchaseInformationSender(JavaMailSenderImpl(), from, invalidUrl)
            }
        }
    }

    private fun greenMailSender() = JavaMailSenderImpl().apply {
        host = "127.0.0.1"
        port = smtp.smtp.port
        javaMailProperties["mail.smtp.starttls.enable"] = "false"
        javaMailProperties["mail.smtp.starttls.required"] = "false"
    }
}
