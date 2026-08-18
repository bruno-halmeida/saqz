package br.com.saqz.access.adapter.output.mail

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationCodeMailerTest {
    @Test
    fun `o texto puro ainda entrega o codigo em uma frase so`() {
        val body = VerificationCodeMailer.plainBody("4821", "10 minutos")

        assertTrue(body.contains("Seu código de acesso é 4821"))
        assertTrue(body.contains("10 minutos"))
        assertTrue(body.contains("Se não foi você que pediu este código, ignore este e-mail."))
    }

    @Test
    fun `o html usa a paleta do app e o codigo em destaque`() {
        val html = VerificationCodeMailer.htmlBody("4821", "10 minutos")

        assertTrue(html.contains("#0638DF"))
        assertTrue(html.contains("#C7F300"))
        assertTrue(html.contains("4821"))
        assertTrue(html.contains("Sem stress."))
        assertTrue(html.contains("Use este código no app para criar uma nova senha."))
        assertTrue(html.contains("cid:saqz-mark"))
        assertEquals("Seu código de acesso Saqz", VerificationCodeMailer.SUBJECT)
    }

    @Test
    fun `o html escapa o que entra no template`() {
        val html = VerificationCodeMailer.htmlBody("<b>x</b>", "1 minuto")

        assertTrue(html.contains("&lt;b&gt;x&lt;/b&gt;"))
        assertFalse(html.contains("<b>x</b>"))
    }

    @Test
    fun `um minuto fica no singular`() {
        assertEquals("1 minuto", VerificationCodeMailer.deadlineLabel(Duration.ofMinutes(1)))
        assertEquals("10 minutos", VerificationCodeMailer.deadlineLabel(Duration.ofMinutes(10)))
    }
}
