package br.com.saqz.subscriptions.adapter.output.mail

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmtpPurchaseInformationSenderTest {
    @Test
    fun `o html destaca o botao e nao o endereco cru do checkout`() {
        val html = SmtpPurchaseInformationSender.htmlBody(
            "https://checkout.test/assinar/?t=${"A".repeat(43)}",
        )

        assertTrue(html.contains("Assinar o Saqz"))
        assertTrue(html.contains("Sua vez."))
        assertTrue(html.contains("#0638DF"))
        assertTrue(html.contains("#C7F300"))
        assertTrue(html.contains("cid:saqz-mark"))
        assertTrue(html.contains("href=\"https://checkout.test/assinar/?t=${"A".repeat(43)}\""))
        assertFalse(html.contains("https://checkout.test/assinar/?t=${"A".repeat(43)}</a>"))
        assertEquals("Instruções para assinar o Saqz", SmtpPurchaseInformationSender.SUBJECT)
    }

    @Test
    fun `o html escapa o que entra no href`() {
        val html = SmtpPurchaseInformationSender.htmlBody("https://x.test/a?b=\"onclick")

        assertTrue(html.contains("href=\"https://x.test/a?b=&quot;onclick\""))
        assertFalse(html.contains("href=\"https://x.test/a?b=\"onclick\""))
    }

    @Test
    fun `o texto puro ainda carrega o link para cliente sem html`() {
        val body = SmtpPurchaseInformationSender.plainBody(
            "https://checkout.test/assinar/?t=${"A".repeat(43)}",
        )

        assertTrue(body.contains("https://checkout.test/assinar/?t=${"A".repeat(43)}"))
        assertTrue(body.contains("Se você não solicitou este e-mail, ignore esta mensagem."))
        assertFalse(body.contains("R$"))
        assertFalse(body.contains("token", ignoreCase = true))
    }
}
