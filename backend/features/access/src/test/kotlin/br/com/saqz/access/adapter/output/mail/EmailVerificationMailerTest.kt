package br.com.saqz.access.adapter.output.mail

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailVerificationMailerTest {
    @Test
    fun `o html destaca o botao e nao o endereco cru do firebase`() {
        val html = EmailVerificationMailer.htmlBody("https://saqz-dev.firebaseapp.com/__/auth/action?oobCode=abc&mode=verifyEmail")

        assertTrue(html.contains("Confirmar e-mail"))
        assertTrue(html.contains("Quase lá."))
        assertTrue(html.contains("#0638DF"))
        assertTrue(html.contains("#C7F300"))
        assertTrue(html.contains("cid:saqz-mark"))
        assertTrue(html.contains("href=\"https://saqz-dev.firebaseapp.com/__/auth/action?oobCode=abc&amp;mode=verifyEmail\""))
        assertFalse(html.contains("oobCode=abc&mode=verifyEmail"))
        assertEquals("Confirme seu e-mail no Saqz", EmailVerificationMailer.SUBJECT)
    }

    @Test
    fun `o html escapa o que entra no href`() {
        val html = EmailVerificationMailer.htmlBody("https://x.test/a?b=\"onclick")

        assertTrue(html.contains("href=\"https://x.test/a?b=&quot;onclick\""))
        assertFalse(html.contains("href=\"https://x.test/a?b=\"onclick\""))
    }

    @Test
    fun `o texto puro ainda carrega o link para cliente sem html`() {
        val body = EmailVerificationMailer.plainBody("https://confirm.test/action")

        assertTrue(body.contains("https://confirm.test/action"))
        assertTrue(body.contains("Se não foi você que criou esta conta, ignore este e-mail."))
    }
}
