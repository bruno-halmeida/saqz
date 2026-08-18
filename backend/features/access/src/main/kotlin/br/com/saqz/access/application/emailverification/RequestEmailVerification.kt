package br.com.saqz.access.application.emailverification

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
 */
class RequestEmailVerification(
    private val links: VerificationLinkGenerator,
    private val mailer: VerificationLinkMailer,
    private val sends: VerificationSendLog,
    private val clock: Clock,
) {
    fun request(identity: RequestIdentity, ip: String): RequestVerificationResult {
        val email = identity.email?.trim()?.lowercase().orEmpty()
        if (email.isEmpty() || identity.emailVerified == true) return RequestVerificationResult.Accepted
        val now = clock.instant()
        val ipWindow = sends.record("email-verification-ip:$ip", now, now.minus(RATE_LIMIT_WINDOW))
        if (ipWindow.count > MAX_PER_IP) {
            return RequestVerificationResult.RateLimited(secondsUntil(now, ipWindow.startedAt.plus(RATE_LIMIT_WINDOW)))
        }
        val resend = sends.record("email-verification:${identity.subject}", now, now.minus(RESEND_WINDOW))
        if (resend.count > 1) {
            return RequestVerificationResult.TooSoon(secondsUntil(now, resend.startedAt.plus(RESEND_WINDOW)))
        }
        val quota = sends.record("email-verification-quota:${identity.subject}", now, now.minus(RATE_LIMIT_WINDOW))
        if (quota.count > MAX_PER_SUBJECT) {
            return RequestVerificationResult.RateLimited(secondsUntil(now, quota.startedAt.plus(RATE_LIMIT_WINDOW)))
        }
        val link = links.generate(email) ?: return RequestVerificationResult.Accepted
        deliver(email, link)
        return RequestVerificationResult.Accepted
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
    }
}
