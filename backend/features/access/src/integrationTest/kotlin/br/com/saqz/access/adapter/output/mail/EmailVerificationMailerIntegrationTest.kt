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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailVerificationMailerIntegrationTest {
    private val smtp = GreenMail(ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP))
    private lateinit var mailer: EmailVerificationMailer

    @BeforeAll
    fun startSmtp() {
        smtp.start()
        val sender = JavaMailSenderImpl()
        sender.host = "127.0.0.1"
        sender.port = smtp.smtp.port
        mailer = EmailVerificationMailer(sender, "nao-responda@saqz.local")
    }

    @AfterAll
    fun stopSmtp() {
        smtp.stop()
    }

    @Test
    fun `entrega o botao com a marca e esconde a url do firebase`() {
        val link = "https://saqz-dev.firebaseapp.com/__/auth/action?oobCode=abc&mode=verifyEmail"
        mailer.send("atleta@saqz.test", link)

        assertTrue(smtp.waitForIncomingEmail(5_000, 1))
        val message = smtp.receivedMessages.single()
        assertEquals("atleta@saqz.test", message.allRecipients.single().toString())
        assertEquals("nao-responda@saqz.local", message.from.single().toString())
        assertEquals("Confirme seu e-mail no Saqz", message.subject)
        assertTrue(message.isMimeType("multipart/*"), message.contentType)
        val plain = message.plainText()
        val html = message.htmlText()
        assertTrue(plain.contains(link), plain)
        assertTrue(html.contains("Confirmar e-mail"), html)
        assertTrue(html.contains("Quase lá."), html)
        assertTrue(html.contains("cid:saqz-mark"), html)
        assertTrue(html.contains("href=\"https://saqz-dev.firebaseapp.com/__/auth/action?oobCode=abc&amp;mode=verifyEmail\""), html)
        assertFalse(html.contains("oobCode=abc&mode=verifyEmail"), html)
        assertTrue(message.hasInlinePng(), message.contentType)
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
