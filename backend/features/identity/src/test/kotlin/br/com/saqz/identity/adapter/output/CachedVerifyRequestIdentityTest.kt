package br.com.saqz.identity.adapter.output

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.assertEquals

class CachedVerifyRequestIdentityTest {
    private val start = Instant.parse("2026-09-17T12:00:00Z")
    private var now = start
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val verified = TokenVerification.Verified(RequestIdentity("subject-1"))
    private var outcome: TokenVerification = verified
    private var calls = 0
    private val cache = CachedVerifyRequestIdentity({ calls++; outcome }, clock, Duration.ofMinutes(3))

    @Test
    fun `verified token is served from cache inside the window and re-verified after it`() {
        val token = token(expiresIn = 3600)

        assertEquals(verified, cache.execute(token))
        now = start.plusSeconds(179)
        assertEquals(verified, cache.execute(token))
        assertEquals(1, calls)

        now = start.plusSeconds(181)
        cache.execute(token)
        assertEquals(2, calls)
    }

    @Test
    fun `entry never outlives the token expiry`() {
        val token = token(expiresIn = 60)

        cache.execute(token)
        now = start.plusSeconds(61)
        cache.execute(token)

        assertEquals(2, calls)
    }

    @Test
    fun `rejection and tokens without a readable expiry are never cached`() {
        outcome = TokenVerification.Rejected
        repeat(2) { cache.execute(token(expiresIn = 3600)) }
        assertEquals(2, calls)

        outcome = verified
        repeat(2) { cache.execute(RawIdentityToken("opaque-token")) }
        assertEquals(4, calls)
    }

    @Test
    fun `fresh verification bypasses the cache and a revoked result drops the entry`() {
        val token = token(expiresIn = 3600)
        cache.execute(token)

        outcome = TokenVerification.Rejected
        assertEquals(TokenVerification.Rejected, cache.executeFresh(token))
        assertEquals(TokenVerification.Rejected, cache.execute(token))
        assertEquals(3, calls)
    }

    private fun token(expiresIn: Long): RawIdentityToken {
        val payload = """{"sub":"subject-1","exp":${start.epochSecond + expiresIn}}"""
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return RawIdentityToken("header.$body.signature")
    }
}
