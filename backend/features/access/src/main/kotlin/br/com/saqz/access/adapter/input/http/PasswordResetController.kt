package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.passwordreset.ConfirmResetResult
import br.com.saqz.access.application.passwordreset.PasswordReset
import br.com.saqz.access.application.passwordreset.RequestCodeResult
import br.com.saqz.access.application.passwordreset.VerifyCodeResult
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class PasswordResetRequestBody @JsonCreator constructor(
    @JsonProperty("email") val email: String?,
)

data class PasswordResetVerifyBody @JsonCreator constructor(
    @JsonProperty("email") val email: String?,
    @JsonProperty("code") val code: String?,
)

data class PasswordResetConfirmBody @JsonCreator constructor(
    @JsonProperty("token") val token: String?,
    @JsonProperty("novaSenha") val novaSenha: String?,
)

data class PasswordResetTokenResponse(val token: String, val expiraEmSegundos: Long)

class PasswordResetRateLimitException(val retryAfterSeconds: Int) : RuntimeException()

class PasswordResetCodeInvalidException(val remainingAttempts: Int) : RuntimeException()

class PasswordResetCodeExpiredException : RuntimeException()

class PasswordResetAttemptLimitException : RuntimeException()

class PasswordResetTokenInvalidException : RuntimeException()

class WeakPasswordException : RuntimeException()

/**
 * Os três passos são anônimos: quem esqueceu a senha não tem sessão. O `request`
 * responde 202 exista a conta ou não — responder diferente transformaria o endpoint
 * num oráculo de quem tem conta no Saqz.
 */
@RestController
class PasswordResetController(
    private val passwordReset: PasswordReset,
) {
    // ponytail: o limite por IP usa o `remoteAddr`; se um proxy entrar na frente da API,
    // é aqui que passa a valer o `X-Forwarded-For` — e só com a lista de proxies confiáveis,
    // senão o cabeçalho vira o próprio jeito de furar o limite.
    @PostMapping("/api/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(@RequestBody body: PasswordResetRequestBody, request: HttpServletRequest) {
        when (val result = passwordReset.request(body.email.orEmpty(), request.remoteAddr.orEmpty())) {
            is RequestCodeResult.RateLimited -> throw PasswordResetRateLimitException(result.retryAfterSeconds)
            is RequestCodeResult.TooSoon -> throw PasswordResetRateLimitException(result.retryAfterSeconds)
            RequestCodeResult.Accepted -> Unit
        }
    }

    @PostMapping("/api/password-reset/verify")
    fun verify(@RequestBody body: PasswordResetVerifyBody): PasswordResetTokenResponse =
        when (val result = passwordReset.verify(body.email.orEmpty(), body.code.orEmpty())) {
            is VerifyCodeResult.InvalidCode -> throw PasswordResetCodeInvalidException(result.remainingAttempts)
            VerifyCodeResult.Expired -> throw PasswordResetCodeExpiredException()
            VerifyCodeResult.AttemptLimit -> throw PasswordResetAttemptLimitException()
            is VerifyCodeResult.Success -> PasswordResetTokenResponse(result.token, result.validity.toSeconds())
        }

    @PostMapping("/api/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirm(@RequestBody body: PasswordResetConfirmBody) {
        when (passwordReset.confirm(body.token.orEmpty(), body.novaSenha.orEmpty())) {
            ConfirmResetResult.InvalidToken -> throw PasswordResetTokenInvalidException()
            ConfirmResetResult.WeakPassword -> throw WeakPasswordException()
            ConfirmResetResult.Success -> Unit
        }
    }
}
