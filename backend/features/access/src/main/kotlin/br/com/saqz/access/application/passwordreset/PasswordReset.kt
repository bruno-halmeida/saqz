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
 * Recuperação de senha por código numérico curto: seis dígitos para o app atual
 * ([CODE_LENGTH]) e quatro para o app das lojas anterior a ele ([LEGACY_CODE_LENGTH]), cuja
 * tela só tem quatro caixas; quem escolhe é o controller, pela versão do app. Poucos dígitos
 * são poucas combinações, então cada defesa aqui é o que separa isso de força bruta:
 * validade curta, teto de tentativas contado atomicamente, janela de reenvio, teto de
 * códigos por e-mail e limite por IP nos dois passos que aceitam palpite.
 */
class PasswordReset(
    private val repository: PasswordResetRepository,
    private val accounts: PasswordAccounts,
    private val notifier: ResetCodeNotifier,
    private val secrets: ResetSecrets,
    private val hasher: ResetSecretHasher,
    private val clock: Clock,
    private val passwordChanged: PasswordChangedNotifier = PasswordChangedNotifier { },
) {
    fun request(rawEmail: String, ip: String, codeLength: Int = LEGACY_CODE_LENGTH): RequestCodeResult {
        require(codeLength in MIN_CODE_LENGTH..MAX_CODE_LENGTH) { "Unsupported reset code length" }
        val now = clock.instant()
        val window = repository.recordRateLimit("request:$ip", now, now.minus(RATE_LIMIT_WINDOW))
        if (window.count > MAX_REQUESTS_PER_IP) {
            return RequestCodeResult.RateLimited(secondsUntil(now, window.startedAt.plus(RATE_LIMIT_WINDOW)))
        }

        // E-mail malformado consome a cota do IP e sai como aceito: responder diferente
        // já distinguiria pedidos, e a tela 1d segue para o código sem checar nada.
        val email = normalize(rawEmail) ?: return RequestCodeResult.Accepted

        val code = secrets.code(codeLength)
        val outcome = repository.replaceCode(
            NewResetCode(email, hasher.ofCode(email, code), now, now.plus(VALIDITY)),
            now.minus(RESEND_WINDOW),
        )
        if (outcome is ReplaceCodeOutcome.TooSoon) {
            return RequestCodeResult.TooSoon(
                secondsUntil(now, outcome.previousCreatedAt.plus(RESEND_WINDOW)),
            )
        }

        // Teto de códigos por e-mail, contado só quando um código novo foi emitido (o reenvio
        // cedo demais não gasta a cota de ninguém). Sem ele, cinco palpites por código e um
        // código novo a cada 60 s esgotariam os dez mil números de um alvo fixo em um dia; com
        // ele, são no máximo 30 palpites por hora e 60 por dia. O código estourado é
        // descartado antes de qualquer entrega, e a conta vale exista o e-mail ou não.
        emailQuotaExceeded(email, now)?.let { retryAfter ->
            repository.retireCode(email)
            return RequestCodeResult.RateLimited(retryAfter)
        }

        // A linha é gravada exista a conta ou não: é ela que segura a janela de reenvio,
        // e uma janela que só existisse para quem tem conta seria o oráculo de volta.
        if (accounts.exists(email)) deliver(email, code)
        return RequestCodeResult.Accepted
    }

    /** Segundos até a cota por e-mail liberar, ou null se ainda há cota na hora e no dia. */
    private fun emailQuotaExceeded(email: String, now: Instant): Int? {
        val hour = repository.recordRateLimit("request-email-hour:$email", now, now.minus(EMAIL_HOUR_WINDOW))
        val day = repository.recordRateLimit("request-email-day:$email", now, now.minus(EMAIL_DAY_WINDOW))
        return when {
            day.count > MAX_CODES_PER_EMAIL_DAY -> secondsUntil(now, day.startedAt.plus(EMAIL_DAY_WINDOW))
            hour.count > MAX_CODES_PER_EMAIL_HOUR -> secondsUntil(now, hour.startedAt.plus(EMAIL_HOUR_WINDOW))
            else -> null
        }
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

        if (!accounts.updatePassword(email, newPassword)) return ConfirmResetResult.InvalidToken
        notifyPasswordChanged(email)
        return ConfirmResetResult.Success
    }

    /** A senha já foi trocada: falha no aviso não pode desfazer nem falhar a troca (quem registra é o adapter). */
    private fun notifyPasswordChanged(email: String) {
        try {
            passwordChanged.send(email)
        } catch (_: Exception) {
            // ponytail: silêncio deliberado; o log com rótulo próprio sai do adapter.
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
        /** O app a partir de 1.0.12 desenha as caixas pelo tamanho que o `request` devolve. */
        const val CODE_LENGTH = 6

        /** O app 1.0.11 das lojas tem quatro caixas fixas: até ele sair de circulação, recebe quatro. */
        const val LEGACY_CODE_LENGTH = 4
        const val MIN_CODE_LENGTH = 4
        const val MAX_CODE_LENGTH = 8
        val VALIDITY: Duration = Duration.ofMinutes(10)
        val TOKEN_VALIDITY: Duration = Duration.ofMinutes(5)
        val RESEND_WINDOW: Duration = Duration.ofSeconds(60)
        val RATE_LIMIT_WINDOW: Duration = Duration.ofMinutes(10)
        const val MAX_ATTEMPTS = 5
        const val MAX_REQUESTS_PER_IP = 10
        val EMAIL_HOUR_WINDOW: Duration = Duration.ofHours(1)
        val EMAIL_DAY_WINDOW: Duration = Duration.ofHours(24)
        const val MAX_CODES_PER_EMAIL_HOUR = 6
        const val MAX_CODES_PER_EMAIL_DAY = 12
        const val MAX_VERIFICATIONS_PER_IP = 30
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 128
        private const val MAX_EMAIL_LENGTH = 320
        private val EMAIL = Regex("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+")
    }
}
