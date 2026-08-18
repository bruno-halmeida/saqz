package br.com.saqz.subscriptions.adapter.output.mail

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import jakarta.mail.Multipart
import jakarta.mail.internet.MimeMessage
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
    private val checkoutToken = "A".repeat(43)
    private val checkoutLink = "$purchaseUrl?t=$checkoutToken"
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
        sender.send(recipient, checkoutLink)

        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
        val message = smtp.receivedMessages.single()
        val plain = message.plainText().replace("\r\n", "\n").trim()
        val html = message.htmlText()

        assertEquals(recipient, message.allRecipients.single().toString())
        assertEquals(from, message.from.single().toString())
        assertEquals("Instruções para assinar o Saqz", message.subject)
        assertTrue(message.isMimeType("multipart/*"), message.contentType)
        assertEquals(
            """
            Olá!

            Para iniciar sua assinatura do Saqz, acesse:
            $checkoutLink

            Se você não solicitou este e-mail, ignore esta mensagem.

            — Equipe Saqz
            """.trimIndent(),
            plain,
        )
        assertTrue(html.contains("Assinar o Saqz"), html)
        assertTrue(html.contains("Sua vez."), html)
        assertTrue(html.contains("cid:saqz-mark"), html)
        assertTrue(html.contains("href=\"$checkoutLink\""), html)
        assertFalse(html.contains("$checkoutLink</a>"), html)
        assertTrue(message.hasInlinePng(), message.contentType)
        assertFalse(plain.contains(recipient))
        assertFalse(plain.contains("R$"))
        assertFalse(plain.contains("token", ignoreCase = true))
        assertFalse(plain.contains("#"))
        assertEquals(1, Regex("https://[^\\s]+").findAll(plain).count())
        assertTrue(plain.contains(checkoutLink))
        assertTrue(plain.contains("?t=$checkoutToken"))
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

        assertFailsWith<MailException> { mailer.send(recipient, checkoutLink) }
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

private fun MimeMessage.plainText(): String = firstPart("text/plain")

private fun MimeMessage.htmlText(): String = firstPart("text/html")

private fun MimeMessage.hasInlinePng(): Boolean = containsPart(content, "image/png")

private fun containsPart(content: Any?, mimeType: String): Boolean {
    if (content !is Multipart) return false
    for (index in 0 until content.count) {
        val part = content.getBodyPart(index)
        if (part.isMimeType(mimeType)) return true
        if (containsPart(part.content, mimeType)) return true
    }
    return false
}

private fun MimeMessage.firstPart(mimeType: String): String {
    val found = findPart(content, mimeType)
    return found ?: error("mensagem sem parte $mimeType")
}

private fun findPart(content: Any?, mimeType: String): String? {
    if (content is String) return content.takeIf { mimeType.startsWith("text/plain") }
    if (content !is Multipart) return null
    for (index in 0 until content.count) {
        val part = content.getBodyPart(index)
        if (part.isMimeType(mimeType) && part.content is String) return part.content.toString()
        findPart(part.content, mimeType)?.let { return it }
    }
    return null
}
