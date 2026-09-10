package br.com.saqz.bootstrap

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailTestBodiesTest {
    @Test
    fun `decodes quoted printable accents before matching the code`() {
        val message = mail("""
            MIME-Version: 1.0
            Content-Type: text/plain; charset=UTF-8
            Content-Transfer-Encoding: quoted-printable

            Seu c=C3=B3digo de acesso =C3=A9 1234
        """)
        assertEquals("Seu código de acesso é 1234", message.plainTextBody())
    }

    @Test
    fun `finds plain text inside nested multipart instead of html or attachments`() {
        val message = mail("""
            MIME-Version: 1.0
            Content-Type: multipart/mixed; boundary=outer

            --outer
            Content-Type: multipart/alternative; boundary=inner

            --inner
            Content-Type: text/html; charset=UTF-8

            <p>Não extrair daqui</p>
            --inner
            Content-Type: text/plain; charset=UTF-8
            Content-Transfer-Encoding: 8bit

            Seu código de acesso é 5678
            --inner--
            --outer--
        """)
        assertEquals("Seu código de acesso é 5678", message.plainTextBody())
    }

    @Test
    fun `missing plain body is not silently accepted`() {
        assertNull(mail("Content-Type: text/html\n\n<p>1234</p>").plainTextBody())
    }

    private fun mail(raw: String) = MimeMessage(
        Session.getInstance(Properties()),
        raw.trimIndent().replace("\n", "\r\n").byteInputStream(Charsets.UTF_8),
    )
}
