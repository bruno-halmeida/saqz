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
}

sealed interface ConfirmResetResult {
    data object Success : ConfirmResetResult

    data object InvalidToken : ConfirmResetResult

    data object WeakPassword : ConfirmResetResult
}

/**
 * Recuperação de senha por código de quatro dígitos. Quatro dígitos são dez mil
 * combinações, então cada defesa aqui é o que separa isso de força bruta em minutos:
 * validade curta, teto de tentativas, janela de reenvio e limite por IP.
 */
class PasswordReset(
    private val repository: PasswordResetRepository,
    private val accounts: PasswordAccounts,
    private val notifier: ResetCodeNotifier,
    private val secrets: ResetSecrets,
    private val clock: Clock,
) {
    fun request(rawEmail: String, ip: String): RequestCodeResult {
        val now = clock.instant()
        val window = repository.recordIpRequest(ip, now, now.minus(IP_WINDOW))
        if (window.count > MAX_REQUESTS_PER_IP) {
            return RequestCodeResult.RateLimited(secondsUntil(now, window.startedAt.plus(IP_WINDOW)))
        }

        // E-mail malformado consome a cota do IP e sai como aceito: responder diferente
        // já distinguiria pedidos, e a tela 1d segue para o código sem checar nada.
        val email = normalize(rawEmail) ?: return RequestCodeResult.Accepted

        val code = secrets.code()
        val outcome = repository.replaceCode(
            StoredResetCode(email, ResetDigest.ofCode(email, code), 0, now, now.plus(VALIDITY)),
            now.minus(RESEND_WINDOW),
        )
        if (outcome is ReplaceCodeOutcome.TooSoon) {
            return RequestCodeResult.TooSoon(
                secondsUntil(now, outcome.previousCreatedAt.plus(RESEND_WINDOW)),
            )
        }

        // A linha é gravada exista a conta ou não: é ela que segura a janela de reenvio,
        // e uma janela que só existe para quem tem conta seria o oráculo de volta.
        if (accounts.exists(email)) notifier.send(email, code, VALIDITY)
        return RequestCodeResult.Accepted
    }

    fun verify(rawEmail: String, rawCode: String): VerifyCodeResult {
        val now = clock.instant()
        val email = normalize(rawEmail) ?: return VerifyCodeResult.Expired
        val stored = repository.findByEmail(email) ?: return VerifyCodeResult.Expired
        if (!now.isBefore(stored.expiresAt)) {
            repository.delete(email)
            return VerifyCodeResult.Expired
        }

        if (!ResetDigest.ofCode(email, rawCode).matches(stored.codeDigest)) {
            val attempts = stored.attempts + 1
            if (attempts >= MAX_ATTEMPTS) {
                repository.delete(email)
                return VerifyCodeResult.AttemptLimit
            }
            repository.recordAttempt(email, attempts)
            return VerifyCodeResult.InvalidCode(MAX_ATTEMPTS - attempts)
        }

        val token = secrets.token()
        repository.issueToken(email, ResetDigest.ofToken(token), now.plus(TOKEN_VALIDITY))
        return VerifyCodeResult.Success(token, TOKEN_VALIDITY)
    }

    fun confirm(rawToken: String, newPassword: String): ConfirmResetResult {
        if (newPassword.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            return ConfirmResetResult.WeakPassword
        }

        // Consumir antes de trocar é o que garante uso único. Se a troca falhar, o
        // token já morreu e a pessoa pede outro código — melhor do que deixá-lo vivo.
        val email = repository.consumeToken(ResetDigest.ofToken(rawToken), clock.instant())
            ?: return ConfirmResetResult.InvalidToken

        return if (accounts.updatePassword(email, newPassword)) {
            ConfirmResetResult.Success
        } else {
            ConfirmResetResult.InvalidToken
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
        val IP_WINDOW: Duration = Duration.ofMinutes(10)
        const val MAX_ATTEMPTS = 5
        const val MAX_REQUESTS_PER_IP = 10
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
        private const val MAX_EMAIL_LENGTH = 320
        private val EMAIL = Regex("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+")
    }
}
