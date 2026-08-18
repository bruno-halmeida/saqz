package br.com.saqz.access.application.emailverification

import br.com.saqz.access.application.passwordreset.RateLimitWindow
import java.time.Instant

/**
 * O Firebase trava o HTML da confirmação; o Admin SDK só devolve o link. Quem manda
 * o e-mail é o SMTP nosso. `null` quando a conta sumiu entre o token e a geração.
 */
fun interface VerificationLinkGenerator {
    fun generate(email: String): String?
}

fun interface VerificationLinkMailer {
    fun send(recipient: String, confirmationLink: String)
}

fun interface VerificationSendLog {
    fun record(bucket: String, now: Instant, windowFloor: Instant): RateLimitWindow
}

class VerificationLinksUnavailable(cause: Throwable? = null) : RuntimeException(cause)
