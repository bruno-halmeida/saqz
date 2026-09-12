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

    @Test
    fun `redeem consumes before mint and returns owner payload`() {
        val owner = AppOnboardingOwner(
            UUID.randomUUID(),
            "firebase-owner",
            AccessName.from("Owner Person"),
            onboardingCompleted = true,
        )
        val store = RedeemingTokenStore(owner)
        val result = RedeemAppOnboardingLink(store, AppOnboardingIdentitySessions { "custom-token" }, fixedClock())
            .execute("a".repeat(43))

        assertEquals(RedeemAppOnboardingResult.Success("custom-token", owner), result)
        assertEquals(now, store.consumedAt)
    }

    @Test
    fun `malformed redeem code is rejected before token store access`() {
        val store = RedeemingTokenStore(null)

        assertEquals(
            RedeemAppOnboardingResult.Invalid,
            RedeemAppOnboardingLink(store, AppOnboardingIdentitySessions { "never" }, fixedClock()).execute("too-short"),
        )
        assertNull(store.consumedAt)
    }

    @Test
    fun `mint failure leaves redemption invalid and cannot be replayed`() {
        val owner = AppOnboardingOwner(UUID.randomUUID(), "firebase-owner", AccessName.from("Owner Person"))
        val store = RedeemingTokenStore(owner)
        val result = RedeemAppOnboardingLink(
            store,
            AppOnboardingIdentitySessions { throw AppOnboardingIdentityUnavailable() },
            fixedClock(),
        ).execute("a".repeat(43))

        assertEquals(RedeemAppOnboardingResult.ProviderUnavailable, result)
        assertEquals(now, store.consumedAt)
        assertEquals(RedeemAppOnboardingResult.Invalid, store.nextResult)
    }

    @Test
    fun `missing provider identity is invalid after one-time consumption`() {
        val owner = AppOnboardingOwner(UUID.randomUUID(), "firebase-owner", AccessName.from("Owner Person"))
        val store = RedeemingTokenStore(owner)

        val result = RedeemAppOnboardingLink(store, AppOnboardingIdentitySessions { null }, fixedClock())
            .execute("a".repeat(43))

        assertEquals(RedeemAppOnboardingResult.Invalid, result)
        assertEquals(now, store.consumedAt)
    }

    @Test
    fun `onboarding completion is account scoped and idempotent`() {
        val store = StatusTokenStore()
        val get = GetAppOnboarding(store)
        val complete = CompleteAppOnboarding(store)

        assertEquals(AppOnboardingAccount.Active(false), get.execute("firebase-owner"))
        assertEquals(AppOnboardingAccount.Active(true), complete.execute("firebase-owner"))
        assertEquals(AppOnboardingAccount.Active(true), complete.execute("firebase-owner"))
        assertEquals(AppOnboardingAccount.Active(true), get.execute("firebase-owner"))
        assertEquals(2, store.completions)
    }

    @Test
    fun `onboarding account status distinguishes suspended and missing accounts`() {
        val store = StatusTokenStore()
        store.status = AppOnboardingAccount.Suspended
        assertEquals(AppOnboardingAccount.Suspended, GetAppOnboarding(store).execute("firebase-owner"))
        store.status = AppOnboardingAccount.Missing
        assertEquals(AppOnboardingAccount.Missing, GetAppOnboarding(store).execute("firebase-owner"))
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

    private class RedeemingTokenStore(private val owner: AppOnboardingOwner?) : AppOnboardingTokenStore {
        var consumedAt: Instant? = null
        var nextResult: RedeemAppOnboardingResult = RedeemAppOnboardingResult.Invalid

        override fun issue(subject: String, now: Instant): AppOnboardingIssuedCode? = null

        override fun consumeOpen(code: AppOnboardingCode, now: Instant): AppOnboardingOwner? {
            if (consumedAt != null) return null
            consumedAt = now
            return owner
        }

        override fun onboardingCompleted(ownerUserId: UUID): Boolean = owner?.onboardingCompleted == true

        override fun completeOnboarding(ownerUserId: UUID): Boolean = true
    }

    private class StatusTokenStore : AppOnboardingTokenStore {
        var status: AppOnboardingAccount = AppOnboardingAccount.Active(false)
        var completions = 0

        override fun issue(subject: String, now: Instant): AppOnboardingIssuedCode? = null
        override fun consumeOpen(code: AppOnboardingCode, now: Instant): AppOnboardingOwner? = null
        override fun onboardingCompleted(ownerUserId: UUID): Boolean = false
        override fun completeOnboarding(ownerUserId: UUID): Boolean = true
        override fun onboardingStatus(subject: String): AppOnboardingAccount = status
        override fun completeOnboardingFor(subject: String): AppOnboardingAccount {
            completions += 1
            status = AppOnboardingAccount.Active(true)
            return status
        }
    }
}
