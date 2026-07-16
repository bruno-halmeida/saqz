package br.com.saqz.identity.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class SessionResponse(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean?,
)

@RestController
class SessionController {
    @GetMapping("/api/session")
    fun session(@AuthenticationPrincipal principal: RequestIdentity) = SessionResponse(
        subject = principal.subject,
        email = principal.email,
        emailVerified = principal.emailVerified,
    )
}
