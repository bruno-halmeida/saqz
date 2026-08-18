package br.com.saqz.access.adapter.output.mail

import org.springframework.core.io.ClassPathResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import java.nio.charset.StandardCharsets

/**
 * Confirmação de e-mail com a cara do Saqz. O Firebase trava o HTML desse template;
 * o Admin SDK só devolve o link, e daí pra frente o SMTP é nosso. O botão esconde a
 * URL do `__/auth/action`. A marca vai em `cid:`, não em base64 — o Gmail descarta
 * `data:image`.
 */
class EmailVerificationMailer(
    private val sender: JavaMailSender,
    private val from: String,
) {
    fun send(recipient: String, confirmationLink: String) {
        val mime = sender.createMimeMessage()
        val helper = MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name())
        helper.setFrom(from)
        helper.setTo(recipient)
        helper.setSubject(SUBJECT)
        helper.setText(plainBody(confirmationLink), htmlBody(confirmationLink))
        helper.addInline(LOGO_CONTENT_ID, ClassPathResource(LOGO_RESOURCE), "image/png")
        sender.send(mime)
    }

    internal companion object {
        const val SUBJECT = "Confirme seu e-mail no Saqz"
        const val LOGO_CONTENT_ID = "saqz-mark"
        const val LOGO_RESOURCE = "mail/saqz-mark.png"
        const val PRIMARY = "#0638DF"
        const val ACCENT = "#C7F300"
        const val INK = "#0E1738"
        const val MUTED = "#667085"
        const val CANVAS = "#F5F5F7"

        fun plainBody(link: String): String = """
            Olá!

            Confirme que este e-mail é seu. Depois é só voltar ao app e organizar a galera.

            $link

            Se não foi você que criou esta conta, ignore este e-mail.

            — Equipe Saqz
        """.trimIndent()

        fun htmlBody(link: String): String {
            val href = escape(link)
            return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width">
                  <title>$SUBJECT</title>
                </head>
                <body style="margin:0;padding:0;background:$CANVAS;">
                  <div style="display:none;max-height:0;overflow:hidden;">
                    Confirme seu e-mail no Saqz para continuar.
                  </div>
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:$CANVAS;">
                    <tr>
                      <td align="center" style="padding:32px 16px;">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:480px;">
                          <tr>
                            <td style="background:$PRIMARY;border-radius:16px 16px 0 0;padding:24px 28px;">
                              <img src="cid:$LOGO_CONTENT_ID" width="48" height="48" alt="Saqz" style="display:block;border:2px solid #FFFFFF;border-radius:12px;">
                              <p style="margin:16px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;letter-spacing:0.18em;text-transform:uppercase;color:$ACCENT;">Saqz</p>
                              <p style="margin:12px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:26px;line-height:1.2;font-weight:700;color:#FFFFFF;">Quase lá.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#FFFFFF;padding:28px;border:1px solid #D8DDE8;border-top:0;border-radius:0 0 16px 16px;">
                              <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:1.5;color:$INK;">
                                Confirme que este e-mail é seu. Depois é só voltar ao app e organizar a galera.
                              </p>
                              <table role="presentation" cellspacing="0" cellpadding="0" style="margin:28px 0 8px;">
                                <tr>
                                  <td align="center" bgcolor="$ACCENT" style="border-radius:12px;">
                                    <a href="$href" style="display:inline-block;padding:14px 28px;font-family:Arial,Helvetica,sans-serif;font-size:16px;font-weight:700;color:$INK;text-decoration:none;">
                                      Confirmar e-mail
                                    </a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:20px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:1.5;color:$MUTED;">
                                Se não foi você que criou esta conta, ignore este e-mail.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
            """.trimIndent()
        }

        private fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
