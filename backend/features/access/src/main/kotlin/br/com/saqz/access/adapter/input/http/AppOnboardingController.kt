package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.session.AppOnboardingIssuedCode
import br.com.saqz.access.application.session.RedeemAppOnboardingLink
import br.com.saqz.access.application.session.RedeemAppOnboardingResult
import br.com.saqz.access.application.session.IssueAppOnboardingLink
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class AppOnboardingLinkResponse(
    val url: String,
    val expiresAt: Instant,
)

data class RedeemAppOnboardingLinkRequest(@JsonProperty("code") val code: String)

data class RedeemAppOnboardingLinkResponse(
    val customToken: String,
    val ownerUserId: java.util.UUID,
    val displayName: String,
    val onboardingCompleted: Boolean,
)

class AppOnboardingCodeInvalidException : RuntimeException()
class AppOnboardingIdentityUnavailableException : RuntimeException()

@RestController
class AppOnboardingController(
    private val issue: IssueAppOnboardingLink,
    private val redeem: RedeemAppOnboardingLink,
    private val linkFactory: (br.com.saqz.access.application.session.AppOnboardingCode) -> java.net.URI,
) {
    @PostMapping("/api/session/app-link")
    fun issue(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<AppOnboardingLinkResponse> {
        val issued = issue.execute(identity) ?: throw AccountNotFoundException()
        return issued.noStoreResponse(linkFactory(issued.code).toString())
    }

    @PostMapping("/api/session/app-link/redeem")
    fun redeem(@RequestBody request: RedeemAppOnboardingLinkRequest): ResponseEntity<RedeemAppOnboardingLinkResponse> {
        val result = when (val outcome = redeem.execute(request.code)) {
            RedeemAppOnboardingResult.Invalid -> throw AppOnboardingCodeInvalidException()
            RedeemAppOnboardingResult.ProviderUnavailable -> throw AppOnboardingIdentityUnavailableException()
            is RedeemAppOnboardingResult.Success -> outcome
        }
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .header("Pragma", "no-cache")
            .body(
                RedeemAppOnboardingLinkResponse(
                    customToken = result.customToken,
                    ownerUserId = result.owner.ownerUserId,
                    displayName = result.owner.displayName.value,
                    onboardingCompleted = result.owner.onboardingCompleted,
                ),
            )
    }
}

private fun AppOnboardingIssuedCode.noStoreResponse(url: String): ResponseEntity<AppOnboardingLinkResponse> =
    ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .header("Pragma", "no-cache")
        .body(AppOnboardingLinkResponse(url, expiresAt))
