package br.com.saqz.bootstrap

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * O GreenMail sobe SMTP puro, sem anunciar STARTTLS — exatamente o cenário em que
 * `starttls.enable` sozinho seguiria em texto puro. O teste usa o `JavaMailSender`
 * que o `application.properties` configura (só o host e a porta são apontados para
 * cá), então quem regredir `starttls.required` derruba este teste.
 */
@SpringBootTest
@Import(TestIdentityConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class SmtpStarttlsIntegrationTest {
    @Autowired
    private lateinit var sender: JavaMailSender

    @Test
    fun `recusa entregar sem STARTTLS em vez de cair para texto puro`() {
        val message = SimpleMailMessage()
        message.from = "nao-responda@saqz.local"
        message.setTo("atleta@saqz.test")
        message.subject = "Seu código de acesso Saqz"
        message.text = "Seu código de acesso é 4821."

        assertFailsWith<MailException> { sender.send(message) }
        assertEquals(0, smtp.receivedMessages.size, "nada pode ter trafegado em texto puro")
    }

    private companion object {
        private val smtp = GreenMail(ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun pointMailAtGreenMail(registry: DynamicPropertyRegistry) {
            registry.add("spring.mail.host") { "127.0.0.1" }
            registry.add("spring.mail.port") { smtp.smtp.port }
        }
    }
}
