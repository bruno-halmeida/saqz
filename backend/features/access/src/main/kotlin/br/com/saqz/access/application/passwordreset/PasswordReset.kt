package br.com.saqz.access.application.passwordreset

import java.time.Clock
import java.time.Duration
import java.time.Instant

sealed interface RequestCodeResult {
    /** O pedido foi aceito. Não diz se a conta existe — quem responde devolve 202 nos dois casos. */
    data object Accepted : RequestCodeResult

    data class TooSoon(val retryAfterSeconds: Int) : RequestCodeResult

    data class RateLimited(val retryAfterSeconds: Int) : RequestCodeResult
}

sealed interface VerifyCodeResult {
    data class Success(val token: String, val validity: Duration) : VerifyCodeResult

    data class InvalidCode(val remainingAttempts: Int) : VerifyCodeResult

    data object Expired : VerifyCodeResult

    data object AttemptLimit : VerifyCodeResult

    data class RateLimited(val retryAfterSeconds: Int) : VerifyCodeResult
}

sealed interface ConfirmResetResult {
    data object Success : ConfirmResetResult

    data object InvalidToken : ConfirmResetResult

    data object WeakPassword : ConfirmResetResult
}

/**
 * Recuperação de senha por código de quatro dígitos. Quatro dígitos são dez mil
 * combinações, então cada defesa aqui é o que separa isso de força bruta em minutos:
 * validade curta, teto de tentativas contado atomicamente, janela de reenvio e limite
 * por IP nos dois passos que aceitam palpite.
 */
class PasswordReset(
    private val repository: PasswordResetRepository,
    private val accounts: PasswordAccounts,
    private val notifier: ResetCodeNotifier,
    private val secrets: ResetSecrets,
    private val hasher: ResetSecretHasher,
    private val clock: Clock,
) {
    fun request(rawEmail: String, ip: String): RequestCodeResult {
        val now = clock.instant()
        val window = repository.recordRateLimit("request:$ip", now, now.minus(RATE_LIMIT_WINDOW))
        if (window.count > MAX_REQUESTS_PER_IP) {
            return RequestCodeResult.RateLimited(secondsUntil(now, window.startedAt.plus(RATE_LIMIT_WINDOW)))
        }

        // E-mail malformado consome a cota do IP e sai como aceito: responder diferente
        // já distinguiria pedidos, e a tela 1d segue para o código sem checar nada.
        val email = normalize(rawEmail) ?: return RequestCodeResult.Accepted

        val code = secrets.code()
        val outcome = repository.replaceCode(
            NewResetCode(email, hasher.ofCode(email, code), now, now.plus(VALIDITY)),
            now.minus(RESEND_WINDOW),
        )
        if (outcome is ReplaceCodeOutcome.TooSoon) {
            return RequestCodeResult.TooSoon(
                secondsUntil(now, outcome.previousCreatedAt.plus(RESEND_WINDOW)),
            )
        }

        // A linha é gravada exista a conta ou não: é ela que segura a janela de reenvio,
        // e uma janela que só existisse para quem tem conta seria o oráculo de volta.
        if (accounts.exists(email)) deliver(email, code)
        return RequestCodeResult.Accepted
    }

    fun verify(rawEmail: String, rawCode: String, ip: String): VerifyCodeResult {
        val now = clock.instant()
        val window = repository.recordRateLimit("verify:$ip", now, now.minus(RATE_LIMIT_WINDOW))
        if (window.count > MAX_VERIFICATIONS_PER_IP) {
            return VerifyCodeResult.RateLimited(secondsUntil(now, window.startedAt.plus(RATE_LIMIT_WINDOW)))
        }

        val email = normalize(rawEmail) ?: return VerifyCodeResult.Expired
        val outcome = repository.consumeAttempt(email, now, MAX_ATTEMPTS) ?: return VerifyCodeResult.Expired
        if (outcome is AttemptOutcome.Exhausted) {
            repository.retireCode(email)
            return VerifyCodeResult.AttemptLimit
        }

        val consumed = outcome as AttemptOutcome.Consumed
        if (!hasher.ofCode(email, rawCode).matches(consumed.codeDigest)) {
            if (consumed.attempts >= MAX_ATTEMPTS) {
                repository.retireCode(email)
                return VerifyCodeResult.AttemptLimit
            }
            return VerifyCodeResult.InvalidCode(MAX_ATTEMPTS - consumed.attempts)
        }

        // Emitir o token apaga o código na mesma escrita. Quem perdeu a corrida não
        // ganha um segundo token que sobrescreveria o primeiro.
        val token = secrets.token()
        return if (repository.issueToken(email, hasher.ofToken(token), now.plus(TOKEN_VALIDITY))) {
            VerifyCodeResult.Success(token, TOKEN_VALIDITY)
        } else {
            VerifyCodeResult.Expired
        }
    }

    fun confirm(rawToken: String, newPassword: String): ConfirmResetResult {
        if (newPassword.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            return ConfirmResetResult.WeakPassword
        }

        // Consumir antes de trocar é o que garante uso único. Se o provedor estiver
        // fora, a exceção sobe como indisponibilidade em vez de virar "token inválido".
        val email = repository.consumeToken(hasher.ofToken(rawToken), clock.instant())
            ?: return ConfirmResetResult.InvalidToken

        return if (accounts.updatePassword(email, newPassword)) {
            ConfirmResetResult.Success
        } else {
            ConfirmResetResult.InvalidToken
        }
    }

    /**
     * Falha de entrega não pode mudar a resposta: conta existente que estourasse no SMTP
     * viraria 500 enquanto a inexistente devolve 202, e a diferença diria exatamente quem
     * tem conta no Saqz. Quem registra a falha é o adapter, que tem log.
     */
    private fun deliver(email: String, code: String) {
        try {
            notifier.send(email, code, VALIDITY)
        } catch (_: Exception) {
            // ponytail: silêncio deliberado; o alarme de SMTP fora do ar é do log do adapter
            // e do health check, não da resposta deste endpoint.
        }
    }

    private fun normalize(rawEmail: String): String? =
        rawEmail.trim().lowercase().takeIf { it.length <= MAX_EMAIL_LENGTH && EMAIL.matches(it) }

    private fun secondsUntil(now: Instant, deadline: Instant): Int {
        val remainingMillis = Duration.between(now, deadline).toMillis()
        return ((remainingMillis + 999) / 1_000).coerceAtLeast(1).toInt()
    }

    companion object {
        val VALIDITY: Duration = Duration.ofMinutes(10)
        val TOKEN_VALIDITY: Duration = Duration.ofMinutes(5)
        val RESEND_WINDOW: Duration = Duration.ofSeconds(60)
        val RATE_LIMIT_WINDOW: Duration = Duration.ofMinutes(10)
        const val MAX_ATTEMPTS = 5
        const val MAX_REQUESTS_PER_IP = 10
        const val MAX_VERIFICATIONS_PER_IP = 30
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
        private const val MAX_EMAIL_LENGTH = 320
        private val EMAIL = Regex("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+")
    }
}
