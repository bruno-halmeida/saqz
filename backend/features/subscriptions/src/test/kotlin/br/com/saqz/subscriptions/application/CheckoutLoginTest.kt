package br.com.saqz.subscriptions.application

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckoutLoginTest {
    private val ownerUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val start = Instant.parse("2026-08-18T12:00:00Z")
    private val clock = Clock.fixed(start, ZoneOffset.UTC)
    private val raw = "A".repeat(43)

    @Test
    fun `link factory appends the issued code as a query parameter`() {
        val tokens = FakeCheckoutLoginTokens(raw)
        val factory = CheckoutLoginLinkFactory(tokens, "https://checkout.test/assinar/")

        val link = factory.issue(ownerUserId, start)

        assertEquals("https://checkout.test/assinar/?t=$raw", link)
        assertEquals(listOf(ownerUserId), tokens.issuedOwners)
        assertEquals(listOf(start), tokens.issuedAt)
    }

    @Test
    fun `link factory rejects a configured URL that already carries a query`() {
        assertFailsWith<IllegalArgumentException> {
            CheckoutLoginLinkFactory(FakeCheckoutLoginTokens(raw), "https://checkout.test/assinar/?preset=1")
        }
    }

    @Test
    fun `generated secret is 32 bytes of url-safe entropy whose digest is sha-256 of the code`() {
        val secret = SecureCheckoutLoginSecrets { target -> target.fill(7) }.next()
        val expected = CheckoutLoginCode.from(
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }),
        )

        assertEquals(expected, secret.code)
        assertEquals(CheckoutLoginDigest.sha256(checkNotNull(expected)), secret.digest)
        assertEquals("CheckoutLoginSecret([REDACTED])", secret.toString())
        assertEquals("CheckoutLoginCode([REDACTED])", secret.code.toString())
    }

    @Test
    fun `malformed codes are not parsed`() {
        assertNull(CheckoutLoginCode.from("short"))
        assertNull(CheckoutLoginCode.from("A".repeat(42)))
        assertNull(CheckoutLoginCode.from("A".repeat(44)))
        assertNull(CheckoutLoginCode.from("${"A".repeat(42)}+"))
    }

    @Test
    fun `redeem mints a custom token then consumes the one-time code`() {
        val open = OpenCheckoutLogin(UUID.randomUUID(), ownerUserId)
        val tokens = FakeCheckoutLoginTokens(raw, open)
        val sessions = FakeCheckoutIdentitySessions("firebase-custom-token")

        val result = RedeemCheckoutLogin(tokens, sessions, clock).execute(raw)

        assertEquals(RedeemCheckoutLoginResult.Success("firebase-custom-token"), result)
        assertEquals(listOf(raw to start), tokens.finds)
        assertEquals(listOf(ownerUserId), sessions.lookups)
        assertEquals(listOf(open.id to start), tokens.consumed)
    }

    @Test
    fun `unknown expired or garbage codes are invalid without minting a session`() {
        val tokens = FakeCheckoutLoginTokens(raw)
        val sessions = FakeCheckoutIdentitySessions("unused")

        val result = RedeemCheckoutLogin(tokens, sessions, clock).execute("not-a-code")

        assertEquals(RedeemCheckoutLoginResult.Invalid, result)
        assertTrue(sessions.lookups.isEmpty())
        assertTrue(tokens.consumed.isEmpty())
    }

    @Test
    fun `missing identity consumes the code so a dead mailbox link cannot retry`() {
        val open = OpenCheckoutLogin(UUID.randomUUID(), ownerUserId)
        val tokens = FakeCheckoutLoginTokens(raw, open)
        val sessions = FakeCheckoutIdentitySessions(customToken = null)

        val result = RedeemCheckoutLogin(tokens, sessions, clock).execute(raw)

        assertEquals(RedeemCheckoutLoginResult.Invalid, result)
        assertEquals(listOf(open.id to start), tokens.consumed)
    }

    @Test
    fun `identity outage propagates without consuming the code`() {
        val open = OpenCheckoutLogin(UUID.randomUUID(), ownerUserId)
        val tokens = FakeCheckoutLoginTokens(raw, open)
        val sessions = FakeCheckoutIdentitySessions(failure = CheckoutIdentityUnavailable())

        val failure = assertFailsWith<CheckoutIdentityUnavailable> {
            RedeemCheckoutLogin(tokens, sessions, clock).execute(raw)
        }

        assertIs<CheckoutIdentityUnavailable>(failure)
        assertTrue(tokens.consumed.isEmpty())
    }

    @Test
    fun `two issues from the factory are distinct links`() {
        val factory = CheckoutLoginLinkFactory(FakeCheckoutLoginTokens(raw), "https://checkout.test/assinar/")
        val first = factory.issue(ownerUserId, start)
        val second = factory.issue(ownerUserId, start)

        assertNotEquals(first, second)
        assertTrue(first.endsWith("?t=${"A".repeat(43)}"))
        assertTrue(second.endsWith("?t=${"B".repeat(43)}"))
    }

    private class FakeCheckoutLoginTokens(
        var nextRaw: String,
        var open: OpenCheckoutLogin? = null,
    ) : CheckoutLoginTokens {
        val issuedOwners = mutableListOf<UUID>()
        val issuedAt = mutableListOf<Instant>()
        val finds = mutableListOf<Pair<String, Instant>>()
        val consumed = mutableListOf<Pair<UUID, Instant>>()

        override fun issue(ownerUserId: UUID, now: Instant): String {
            issuedOwners += ownerUserId
            issuedAt += now
            val issued = nextRaw
            nextRaw = "B".repeat(43)
            return issued
        }

        override fun findOpen(rawToken: String, now: Instant): OpenCheckoutLogin? {
            finds += rawToken to now
            return open
        }

        override fun consume(id: UUID, consumedAt: Instant): Boolean {
            consumed += id to consumedAt
            return true
        }
    }

    private class FakeCheckoutIdentitySessions(
        private val customToken: String? = null,
        private val failure: Exception? = null,
    ) : CheckoutIdentitySessions {
        val lookups = mutableListOf<UUID>()

        override fun customTokenFor(ownerUserId: UUID): String? {
            lookups += ownerUserId
            failure?.let { throw it }
            return customToken
        }
    }
}
