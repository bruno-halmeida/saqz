package br.com.saqz.subscriptions.adapter.output.mail

import br.com.saqz.subscriptions.application.PurchaseInformationSender
import java.net.URI
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class SmtpPurchaseInformationSender(
    private val sender: JavaMailSender,
    private val from: String,
    purchaseUrl: String,
) : PurchaseInformationSender {
    private val purchaseUrl = validatePurchaseUrl(purchaseUrl)

    override fun send(recipient: String) {
        val message = SimpleMailMessage()
        message.from = from
        message.setTo(recipient)
        message.subject = SUBJECT
        message.text = body()
        sender.send(message)
    }

    private fun body() = """
        Olá!

        Para iniciar sua assinatura do Saqz, acesse:
        $purchaseUrl

        Se você não solicitou este e-mail, ignore esta mensagem.
    """.trimIndent()

    private companion object {
        const val SUBJECT = "Instruções para assinar o Saqz"

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
    }
}
