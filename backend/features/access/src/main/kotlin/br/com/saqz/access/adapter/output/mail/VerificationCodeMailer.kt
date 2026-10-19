package br.com.saqz.access.adapter.output.mail

import org.springframework.core.io.ClassPathResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Monta e envia o e-mail com o código de acesso. Quem gera o código, guarda o hash
 * e decide a validade é o VUL-80; aqui só se escreve o texto e se entrega ao SMTP.
 *
 * HTML + texto puro: clientes que não renderizam HTML ainda leem o código. A paleta
 * segue o app (`#0638DF`, `#C7F300`, `#0E1738`) porque o `SimpleMailMessage` saía
 * sem cara nenhuma — e o e-mail é a cara do Saqz fora do app.
 *
 * A marca vai embutida (`cid:`), não em base64: o Gmail descarta `data:image`.
 * O HTML de confirmação de e-mail do Firebase não aceita anexo — lá a mesma
 * arte sai de `https://saqz.app/assets/saqz-mark.png`.
 *
 * ponytail: mora em adapter/output porque ARCH-15 proíbe `org.springframework.` em
 * application; não existe porta `EmailSender` — o `JavaMailSender` já é a interface.
 */
class VerificationCodeMailer(
    private val sender: JavaMailSender,
    private val from: String,
) {
    fun send(recipient: String, code: String, validity: Duration) {
        val deadline = deadlineLabel(validity)
        val mime = sender.createMimeMessage()
        val helper = MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name())
        helper.setFrom(from)
        helper.setTo(recipient)
        helper.setSubject(SUBJECT)
        helper.setText(plainBody(code, deadline), htmlBody(code, deadline))
        helper.addInline(LOGO_CONTENT_ID, ClassPathResource(LOGO_RESOURCE), "image/png")
        sender.send(mime)
    }

    /**
     * Aviso de que a senha foi trocada pelo fluxo de recuperação (DEB-083). O reply-to leva a resposta de quem não
     * reconhece a troca ao contato da equipe, não ao remetente "não responda".
     */
    fun sendPasswordChanged(recipient: String) {
        val mime = sender.createMimeMessage()
        val helper = MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name())
        helper.setFrom(from)
        helper.setReplyTo(CONTACT)
        helper.setTo(recipient)
        helper.setSubject(PASSWORD_CHANGED_SUBJECT)
        helper.setText(PASSWORD_CHANGED_PLAIN, passwordChangedHtml())
        helper.addInline(LOGO_CONTENT_ID, ClassPathResource(LOGO_RESOURCE), "image/png")
        sender.send(mime)
    }

    internal companion object {
        const val SUBJECT = "Seu código de acesso Saqz"
        const val PASSWORD_CHANGED_SUBJECT = "Sua senha do Saqz foi alterada"
        const val PASSWORD_CHANGED_LINE = "Sua senha do Saqz foi alterada."
        const val PASSWORD_CHANGED_ADVICE = "Se não foi você, responda este e-mail."
        const val CONTACT = "contato@egysis.com"
        const val LOGO_CONTENT_ID = "saqz-mark"
        const val LOGO_RESOURCE = "mail/saqz-mark.png"
        const val PRIMARY = "#0638DF"
        const val ACCENT = "#C7F300"
        const val INK = "#0E1738"
        const val MUTED = "#667085"
        const val CANVAS = "#F5F5F7"

        fun deadlineLabel(validity: Duration): String {
            val minutes = validity.toMinutes()
            return if (minutes == 1L) "1 minuto" else "$minutes minutos"
        }

        fun plainBody(code: String, deadline: String): String = """
            Olá!

            Seu código de acesso é $code.

            Ele vale por $deadline. Depois disso, peça um novo.

            Se não foi você que pediu este código, ignore este e-mail.

            — Equipe Saqz
        """.trimIndent()

        val PASSWORD_CHANGED_PLAIN: String = """
            Olá!

            $PASSWORD_CHANGED_LINE

            $PASSWORD_CHANGED_ADVICE

            — Equipe Saqz
        """.trimIndent()

        fun passwordChangedHtml(): String = layout(
            title = PASSWORD_CHANGED_SUBJECT,
            preheader = PASSWORD_CHANGED_LINE,
            content = """
                              <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:1.5;color:$INK;">
                                $PASSWORD_CHANGED_LINE
                              </p>
                              <p style="margin:20px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.5;color:$MUTED;">
                                $PASSWORD_CHANGED_ADVICE
                              </p>
            """.trimIndent(),
        )

        fun htmlBody(code: String, deadline: String): String {
            val digits = escape(code)
            val until = escape(deadline)
            val content = """
                              <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:1.5;color:$INK;">
                                Use este código no app para criar uma nova senha.
                              </p>
                              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin:24px 0;">
                                <tr>
                                  <td align="center" style="background:#F4F8FB;border:1px solid #D8DDE8;border-radius:12px;padding:20px 12px;">
                                    <p style="margin:0;font-family:'Courier New',Courier,monospace;font-size:36px;letter-spacing:0.35em;font-weight:700;color:$PRIMARY;">
                                      $digits
                                    </p>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.5;color:$MUTED;">
                                Ele vale por $until. Depois disso, peça um novo no app.
                              </p>
                              <p style="margin:20px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:1.5;color:$MUTED;">
                                Se não foi você que pediu este código, ignore este e-mail.
                              </p>
            """.trimIndent()
            return layout(SUBJECT, "Seu código Saqz: $digits. Vale por $until.", content)
        }

        private fun layout(title: String, preheader: String, content: String): String {
            return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width">
                  <title>$title</title>
                </head>
                <body style="margin:0;padding:0;background:$CANVAS;">
                  <div style="display:none;max-height:0;overflow:hidden;">
                    $preheader
                  </div>
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:$CANVAS;">
                    <tr>
                      <td align="center" style="padding:32px 16px;">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:480px;">
                          <tr>
                            <td style="background:$PRIMARY;border-radius:16px 16px 0 0;padding:24px 28px;">
                              <img src="cid:$LOGO_CONTENT_ID" width="48" height="48" alt="Saqz" style="display:block;border:2px solid #FFFFFF;border-radius:12px;">
                              <p style="margin:16px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;letter-spacing:0.18em;text-transform:uppercase;color:$ACCENT;">Saqz</p>
                              <p style="margin:12px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:26px;line-height:1.2;font-weight:700;color:#FFFFFF;">Sem stress.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#FFFFFF;padding:28px;border:1px solid #D8DDE8;border-top:0;border-radius:0 0 16px 16px;">
                              $content
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
