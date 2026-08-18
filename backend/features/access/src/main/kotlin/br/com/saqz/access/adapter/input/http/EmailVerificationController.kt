package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.emailverification.RequestEmailVerification
import br.com.saqz.access.application.emailverification.RequestVerificationResult
import br.com.saqz.sharedkernel.RequestIdentity
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

class EmailVerificationRateLimitException(val retryAfterSeconds: Int) : RuntimeException()

/**
 * Autenticado de propósito: o Firebase não deixa vestir o HTML da confirmação, então
 * o app pede o envio aqui e o SMTP nosso manda o botão. Sem corpo — o destinatário
 * é o e-mail do token.
 */
@RestController
class EmailVerificationController(
    private val verification: RequestEmailVerification,
) {
    @PostMapping("/api/email-verification/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun request(@AuthenticationPrincipal identity: RequestIdentity, request: HttpServletRequest) {
        when (val result = verification.request(identity, request.remoteAddr.orEmpty())) {
            is RequestVerificationResult.RateLimited -> throw EmailVerificationRateLimitException(result.retryAfterSeconds)
            is RequestVerificationResult.TooSoon -> throw EmailVerificationRateLimitException(result.retryAfterSeconds)
            RequestVerificationResult.Accepted -> Unit
        }
    }
}
