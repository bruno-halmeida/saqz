package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.session.AppOnboardingIssuedCode
import br.com.saqz.access.application.session.IssueAppOnboardingLink
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class AppOnboardingLinkResponse(
    val url: String,
    val expiresAt: Instant,
)

@RestController
class AppOnboardingController(
    private val issue: IssueAppOnboardingLink,
    private val linkFactory: (br.com.saqz.access.application.session.AppOnboardingCode) -> java.net.URI,
) {
    @PostMapping("/api/session/app-link")
    fun issue(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<AppOnboardingLinkResponse> {
        val issued = issue.execute(identity) ?: throw AccountNotFoundException()
        return issued.noStoreResponse(linkFactory(issued.code).toString())
    }
}

private fun AppOnboardingIssuedCode.noStoreResponse(url: String): ResponseEntity<AppOnboardingLinkResponse> =
    ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(AppOnboardingLinkResponse(url, expiresAt))
