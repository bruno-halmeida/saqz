package br.com.saqz.subscriptions.adapter.output.mail

import br.com.saqz.subscriptions.application.PurchaseInformationSender
import java.net.URI
import java.nio.charset.StandardCharsets
import org.springframework.core.io.ClassPathResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper

/**
 * Instruções de assinatura com a cara do Saqz. O botão esconde a URL do checkout;
 * o texto puro ainda carrega o endereço para cliente sem HTML. A marca vai em
 * `cid:`, não em base64 — o Gmail descarta `data:image`.
 */
class SmtpPurchaseInformationSender(
    private val sender: JavaMailSender,
    private val from: String,
    purchaseUrl: String,
) : PurchaseInformationSender {
    private val purchaseUrl = validatePurchaseUrl(purchaseUrl)

    override fun send(recipient: String, checkoutLink: String) {
        val mime = sender.createMimeMessage()
        val helper = MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name())
        helper.setFrom(from)
        helper.setTo(recipient)
        helper.setSubject(SUBJECT)
        helper.setText(plainBody(checkoutLink), htmlBody(checkoutLink))
        helper.addInline(LOGO_CONTENT_ID, ClassPathResource(LOGO_RESOURCE), "image/png")
        sender.send(mime)
    }

    internal companion object {
        const val SUBJECT = "Instruções para assinar o Saqz"
        const val LOGO_CONTENT_ID = "saqz-mark"
        const val LOGO_RESOURCE = "mail/saqz-mark.png"
        const val PRIMARY = "#0638DF"
        const val ACCENT = "#C7F300"
        const val INK = "#0E1738"
        const val MUTED = "#667085"
        const val CANVAS = "#F5F5F7"

        fun plainBody(url: String): String = """
            Olá!

            Para iniciar sua assinatura do Saqz, acesse:
            $url

            Se você não solicitou este e-mail, ignore esta mensagem.

            — Equipe Saqz
        """.trimIndent()

        fun htmlBody(url: String): String {
            val href = escape(url)
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
                    Instruções para assinar o Saqz. É só clicar.
                  </div>
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:$CANVAS;">
                    <tr>
                      <td align="center" style="padding:32px 16px;">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:480px;">
                          <tr>
                            <td style="background:$PRIMARY;border-radius:16px 16px 0 0;padding:24px 28px;">
                              <img src="cid:$LOGO_CONTENT_ID" width="48" height="48" alt="Saqz" style="display:block;border:2px solid #FFFFFF;border-radius:12px;">
                              <p style="margin:16px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;letter-spacing:0.18em;text-transform:uppercase;color:$ACCENT;">Saqz</p>
                              <p style="margin:12px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:26px;line-height:1.2;font-weight:700;color:#FFFFFF;">Sua vez.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#FFFFFF;padding:28px;border:1px solid #D8DDE8;border-top:0;border-radius:0 0 16px 16px;">
                              <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:16px;line-height:1.5;color:$INK;">
                                Para iniciar sua assinatura, toque no botão. Depois é só escolher o plano e pagar.
                              </p>
                              <table role="presentation" cellspacing="0" cellpadding="0" style="margin:28px 0 8px;">
                                <tr>
                                  <td align="center" bgcolor="$ACCENT" style="border-radius:12px;">
                                    <a href="$href" style="display:inline-block;padding:14px 28px;font-family:Arial,Helvetica,sans-serif;font-size:16px;font-weight:700;color:$INK;text-decoration:none;">
                                      Assinar o Saqz
                                    </a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:20px 0 0;font-family:Arial,Helvetica,sans-serif;font-size:13px;line-height:1.5;color:$MUTED;">
                                Se você não solicitou este e-mail, ignore esta mensagem.
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

        fun validatePurchaseUrl(value: String): String {
            val uri = runCatching { URI(value) }
                .getOrElse { cause -> throw IllegalArgumentException("Invalid purchase URL", cause) }
            require(uri.scheme.equals("https", ignoreCase = true)) {
                "Purchase URL must use HTTPS"
            }
            require(uri.host != null) { "Purchase URL must include a host" }
            require(uri.port == -1) { "Purchase URL must not include a port" }
            require(uri.userInfo == null) { "Purchase URL must not include user info" }
            require(uri.query == null) { "Purchase URL must not include a query" }
            require(uri.fragment == null) { "Purchase URL must not include a fragment" }
            require(uri.rawPath == "/assinar/") {
                "Purchase URL path must be exactly /assinar/"
            }
            return value
        }

        private fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
