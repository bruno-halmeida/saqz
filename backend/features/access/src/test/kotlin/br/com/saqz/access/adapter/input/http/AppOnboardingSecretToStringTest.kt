package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.session.AppOnboardingOwner
import br.com.saqz.access.application.session.RedeemAppOnboardingResult
import br.com.saqz.access.domain.AccessName
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse

class AppOnboardingSecretToStringTest {
    private val code = "a".repeat(43)
    private val customToken = "firebase-custom-token"
    private val url = "https://join.test/?saqz_onboarding=$code"
    private val owner = AppOnboardingOwner(UUID.randomUUID(), "firebase-subject", AccessName.from("Owner"))

    @Test
    fun `secret-bearing onboarding values are absent from diagnostic strings`() {
        assertFalse(AppOnboardingLinkResponse(url, Instant.parse("2026-09-12T12:10:00Z")).toString().contains(url))
        assertFalse(RedeemAppOnboardingLinkRequest(code).toString().contains(code))
        assertFalse(
            RedeemAppOnboardingLinkResponse(customToken, owner.ownerUserId, "Owner", false)
                .toString()
                .contains(customToken),
        )
        assertFalse(RedeemAppOnboardingResult.Success(customToken, owner).toString().contains(customToken))
    }
}
