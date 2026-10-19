package br.com.saqz.access.adapter.output.mail

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import jakarta.mail.Multipart
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerificationCodeMailerIntegrationTest {
    private val smtp = GreenMail(ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP))
    private lateinit var mailer: VerificationCodeMailer

    @BeforeAll
    fun startSmtp() {
        smtp.start()
        val sender = JavaMailSenderImpl()
        sender.host = "127.0.0.1"
        sender.port = smtp.smtp.port
        mailer = VerificationCodeMailer(sender, "nao-responda@saqz.local")
    }

    @AfterAll
    fun stopSmtp() {
        smtp.stop()
    }

    @Test
    fun `entrega o codigo de acesso ao destinatario`() {
        mailer.send("atleta@saqz.test", "4821", Duration.ofMinutes(10))

        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
        val message = smtp.receivedMessages.single()
        assertEquals("atleta@saqz.test", message.allRecipients.single().toString())
        assertEquals("nao-responda@saqz.local", message.from.single().toString())
        assertEquals("Seu código de acesso Saqz", message.subject)
        assertTrue(message.isMimeType("multipart/*"), message.contentType)
        val plain = message.plainText()
        val html = message.htmlText()
        assertTrue(plain.contains("4821"), plain)
        assertTrue(plain.contains("10 minutos"), plain)
        assertTrue(plain.contains("Se não foi você que pediu este código, ignore este e-mail."), plain)
        assertTrue(html.contains("4821"), html)
        assertTrue(html.contains("#0638DF"), html)
        assertTrue(html.contains("Sem stress."), html)
        assertTrue(html.contains("cid:saqz-mark"), html)
        assertTrue(message.hasPart("image/png"), message.contentType)
    }

    @Test
    fun `avisa que a senha foi alterada, com reply-to para o contato da equipe`() {
        smtp.purgeEmailFromAllMailboxes()

        mailer.sendPasswordChanged("atleta@saqz.test")

        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
        val message = smtp.receivedMessages.single()
        assertEquals("atleta@saqz.test", message.allRecipients.single().toString())
        assertEquals("nao-responda@saqz.local", message.from.single().toString())
        assertEquals("contato@egysis.com", message.replyTo.single().toString())
        assertEquals("Sua senha do Saqz foi alterada", message.subject)
        val plain = message.plainText()
        val html = message.htmlText()
        assertTrue(plain.contains("Sua senha do Saqz foi alterada."), plain)
        assertTrue(plain.contains("Se não foi você, responda este e-mail."), plain)
        assertTrue(html.contains("Sua senha do Saqz foi alterada."), html)
        assertTrue(html.contains("Se não foi você, responda este e-mail."), html)
        assertTrue(html.contains("cid:saqz-mark"), html)
    }
}

private fun MimeMessage.plainText(): String = firstPart("text/plain")

private fun MimeMessage.htmlText(): String = firstPart("text/html")

private fun MimeMessage.hasPart(mimeType: String): Boolean = containsPart(content, mimeType)

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
