package br.com.saqz.access.application.emailverification

import br.com.saqz.access.application.session.AppOnboardingSecrets
import br.com.saqz.access.application.session.SecureAppOnboardingSecrets
import br.com.saqz.sharedkernel.RequestIdentity
import java.time.Clock
import java.time.Duration
import java.time.Instant

sealed interface RequestVerificationResult {
    data object Accepted : RequestVerificationResult
    data class TooSoon(val retryAfterSeconds: Int) : RequestVerificationResult
    data class RateLimited(val retryAfterSeconds: Int) : RequestVerificationResult
}

/**
 * Confirmação de e-mail com HTML nosso. O token Bearer já prova quem é; por isso
 * o e-mail sai só para o endereço da sessão, nunca para um destinatário escolhido
 * no corpo. Conta já confirmada ou sem e-mail responde 202 e não manda nada.
 *
 * Com [phones] e [whatsApp] ligados, o mesmo pedido manda também o link de confirmação
 * da conta para o WhatsApp do cadastro, debaixo das mesmas travas de reenvio.
 */
class RequestEmailVerification(
    private val links: VerificationLinkGenerator,
    private val mailer: VerificationLinkMailer,
    private val sends: VerificationSendLog,
    private val clock: Clock,
    private val phones: PhoneConfirmationStore? = null,
    private val whatsApp: PhoneConfirmationSender? = null,
    private val secrets: AppOnboardingSecrets = SecureAppOnboardingSecrets(),
) {
    fun request(identity: RequestIdentity, ip: String): RequestVerificationResult {
        val email = identity.email?.trim()?.lowercase().orEmpty()
        val phoneChannel = phones != null && whatsApp != null
        if (identity.emailVerified == true || email.isEmpty() && !phoneChannel) return RequestVerificationResult.Accepted
        val now = clock.instant()
        // A janela de reenvio vem antes da cota de IP: toque cedo demais não pode
        // queimar o teto global e impedir o e-mail que sai depois de um minuto.
        val resend = sends.record("email-verification:${identity.subject}", now, now.minus(RESEND_WINDOW))
        if (resend.count > 1) {
            return RequestVerificationResult.TooSoon(secondsUntil(now, resend.startedAt.plus(RESEND_WINDOW)))
        }
        val ipWindow = sends.record("email-verification-ip:$ip", now, now.minus(RATE_LIMIT_WINDOW))
        if (ipWindow.count > MAX_PER_IP) {
            return RequestVerificationResult.RateLimited(secondsUntil(now, ipWindow.startedAt.plus(RATE_LIMIT_WINDOW)))
        }
        val quota = sends.record("email-verification-quota:${identity.subject}", now, now.minus(RATE_LIMIT_WINDOW))
        if (quota.count > MAX_PER_SUBJECT) {
            return RequestVerificationResult.RateLimited(secondsUntil(now, quota.startedAt.plus(RATE_LIMIT_WINDOW)))
        }
        // WhatsApp antes: o Firebase fora do ar lança no `generate` e não pode levar o outro canal junto.
        if (phones != null && whatsApp != null) deliverWhatsApp(phones, whatsApp, identity.subject, now)
        if (email.isNotEmpty()) links.generate(email)?.let { deliver(email, it) }
        return RequestVerificationResult.Accepted
    }

    /**
     * Telefone recém-gravado no perfil. No cadastro o pedido do e-mail chega antes do
     * telefone existir; é daqui que sai o primeiro WhatsApp. Sem resposta: é efeito
     * colateral do PATCH, e as travas só fazem calar.
     */
    fun requestPhone(identity: RequestIdentity) {
        if (identity.emailVerified == true || phones == null || whatsApp == null) return
        val now = clock.instant()
        val resend = sends.record("phone-confirmation:${identity.subject}", now, now.minus(RESEND_WINDOW))
        if (resend.count > 1) return
        deliverWhatsApp(phones, whatsApp, identity.subject, now)
    }

    private fun deliverWhatsApp(
        phones: PhoneConfirmationStore,
        whatsApp: PhoneConfirmationSender,
        subject: String,
        now: Instant,
    ) {
        val secret = secrets.next()
        val phone = phones.issue(subject, secret.digest, now.plus(PHONE_TOKEN_TTL)) ?: return
        // O telefone é digitado e ainda não provado: sem teto por número, uma conta que
        // troca o telefone faz o WhatsApp do Saqz mandar mensagem para qualquer pessoa.
        val perPhone = sends.record("phone-confirmation-number:$phone", now, now.minus(PHONE_WINDOW))
        if (perPhone.count > MAX_PER_PHONE) return
        try {
            whatsApp.send(phone, secret.code)
        } catch (_: Exception) {
            // ponytail: mesmo contrato do e-mail — o log é do adapter, a resposta segue 202.
        }
    }

    private fun deliver(email: String, link: String) {
        try {
            mailer.send(email, link)
        } catch (_: Exception) {
            // ponytail: o alarme de SMTP fora do ar é do log do adapter; a resposta
            // continua 202 para o banner do app não virar oráculo de infraestrutura.
        }
    }

    private fun secondsUntil(now: Instant, deadline: Instant): Int {
        val remainingMillis = Duration.between(now, deadline).toMillis()
        return ((remainingMillis + 999) / 1_000).coerceAtLeast(1).toInt()
    }

    companion object {
        val RESEND_WINDOW: Duration = Duration.ofSeconds(60)
        val RATE_LIMIT_WINDOW: Duration = Duration.ofMinutes(10)
        const val MAX_PER_SUBJECT = 8
        const val MAX_PER_IP = 20
        val PHONE_TOKEN_TTL: Duration = Duration.ofHours(24)
        val PHONE_WINDOW: Duration = Duration.ofHours(24)
        const val MAX_PER_PHONE = 5
    }
}
