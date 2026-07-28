package br.com.saqz.access.adapter.output.mail

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import java.time.Duration

/**
 * Monta e envia o e-mail com o código de acesso. Quem gera o código, guarda o hash
 * e decide a validade é o VUL-80; aqui só se escreve o texto e se entrega ao SMTP.
 *
 * ponytail: mora em adapter/output porque ARCH-15 proíbe `org.springframework.` em
 * application; não existe porta `EmailSender` — o `JavaMailSender` já é a interface.
 */
class VerificationCodeMailer(
    private val sender: JavaMailSender,
    private val from: String,
) {
    fun send(recipient: String, code: String, validity: Duration) {
        val message = SimpleMailMessage()
        message.from = from
        message.setTo(recipient)
        message.subject = SUBJECT
        message.text = body(code, validity)
        sender.send(message)
    }

    private fun body(code: String, validity: Duration): String {
        val minutes = validity.toMinutes()
        val deadline = if (minutes == 1L) "1 minuto" else "$minutes minutos"
        return """
            Olá!

            Seu código de acesso é $code.

            Ele vale por $deadline. Depois disso, peça um novo.

            Se não foi você que pediu este código, ignore este e-mail.
        """.trimIndent()
    }

    private companion object {
        const val SUBJECT = "Seu código de acesso Saqz"
    }
}
