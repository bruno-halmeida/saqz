package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.sharedkernel.RequestIdentity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class AppOnboardingLoginTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z")

    @Test
    fun `issue uses authenticated subject and exact ten minute expiry`() {
        val store = RecordingAppOnboardingTokenStore(
            issue = AppOnboardingIssuedCode(
                ownerUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                code = AppOnboardingCode.from("A".repeat(43))!!,
                expiresAt = now.plus(Duration.ofMinutes(10)),
            ),
        )

        val result = IssueAppOnboardingLink(store, fixedClock()).execute(
            RequestIdentity("firebase-subject", displayName = "Ignored by issue"),
        )

        assertEquals(store.issue, result)
        assertEquals("firebase-subject", store.subject)
        assertEquals(now, store.now)
    }

    @Test
    fun `missing active account cannot issue a link`() {
        val store = RecordingAppOnboardingTokenStore(issue = null)

        assertNull(IssueAppOnboardingLink(store, fixedClock()).execute(RequestIdentity("unknown")))
        assertEquals("unknown", store.subject)
    }

    @Test
    fun `code accepts exactly forty three url safe characters and redacts itself`() {
        val code = AppOnboardingCode.from("a".repeat(43))

        assertEquals("a".repeat(43), code!!.value)
        assertEquals("AppOnboardingCode([REDACTED])", code.toString())
        assertNull(AppOnboardingCode.from("a".repeat(42)))
        assertNull(AppOnboardingCode.from("a".repeat(44)))
        assertNull(AppOnboardingCode.from("a".repeat(42) + "+"))
    }

    @Test
    fun `secure secret contains thirty two random bytes encoded without padding`() {
        val secret = SecureAppOnboardingSecrets { bytes -> bytes.indices.forEach { bytes[it] = it.toByte() } }.next()

        assertEquals(43, secret.code.value.length)
        assertEquals(32, secret.digest.toByteArray().size)
        assertEquals("AppOnboardingSecret([REDACTED])", secret.toString())
    }

    private fun fixedClock(): Clock = Clock.fixed(now, ZoneOffset.UTC)

    private class RecordingAppOnboardingTokenStore(
        val issue: AppOnboardingIssuedCode?,
    ) : AppOnboardingTokenStore {
        var subject: String? = null
        var now: Instant? = null

        override fun issue(subject: String, now: Instant): AppOnboardingIssuedCode? {
            this.subject = subject
            this.now = now
            return issue
        }

        override fun consumeOpen(code: AppOnboardingCode, now: Instant): AppOnboardingOwner? = null

        override fun onboardingCompleted(ownerUserId: UUID): Boolean = false

        override fun completeOnboarding(ownerUserId: UUID): Boolean = true
    }
}
