package br.com.saqz.access.adapter.output.mail

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
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
        val body = message.content.toString()

        assertEquals("atleta@saqz.test", message.allRecipients.single().toString())
        assertEquals("nao-responda@saqz.local", message.from.single().toString())
        assertEquals("Seu código de acesso Saqz", message.subject)
        assertTrue(body.contains("4821"), body)
        assertTrue(body.contains("10 minutos"), body)
        assertTrue(body.contains("Se não foi você que pediu este código, ignore este e-mail."), body)
    }
}
