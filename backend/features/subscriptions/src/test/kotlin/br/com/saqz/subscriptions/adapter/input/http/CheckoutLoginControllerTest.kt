package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.CheckoutIdentitySessions
import br.com.saqz.subscriptions.application.CheckoutIdentityUnavailable
import br.com.saqz.subscriptions.application.CheckoutLoginTokens
import br.com.saqz.subscriptions.application.OpenCheckoutLogin
import br.com.saqz.subscriptions.application.RedeemCheckoutLogin
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CheckoutLoginControllerTest {
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val raw = "A".repeat(43)
    private val clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `valid code returns a custom token`() {
        val open = OpenCheckoutLogin(UUID.randomUUID(), ownerId)
        val controller = controller(open = open, customToken = "firebase-custom")

        val response = controller.redeem(CheckoutLoginRequest(raw))

        assertEquals("firebase-custom", response.customToken)
    }

    @Test
    fun `missing or invalid code is a typed failure`() {
        val controller = controller(open = null)

        assertFailsWith<CheckoutLoginTokenInvalidException> {
            controller.redeem(CheckoutLoginRequest(null))
        }
        assertFailsWith<CheckoutLoginTokenInvalidException> {
            controller.redeem(CheckoutLoginRequest("garbage"))
        }
    }

    @Test
    fun `identity outage is not mapped to an invalid token`() {
        val open = OpenCheckoutLogin(UUID.randomUUID(), ownerId)
        val controller = controller(open = open, failure = CheckoutIdentityUnavailable())

        assertFailsWith<CheckoutIdentityUnavailable> {
            controller.redeem(CheckoutLoginRequest(raw))
        }
    }

    private fun controller(
        open: OpenCheckoutLogin?,
        customToken: String? = null,
        failure: Exception? = null,
    ) = CheckoutLoginController(
        RedeemCheckoutLogin(
            tokens = object : CheckoutLoginTokens {
                override fun issue(ownerUserId: UUID, now: Instant) = error("not issued here")
                override fun findOpen(rawToken: String, now: Instant) = open
                override fun consume(id: UUID, consumedAt: Instant) = true
            },
            sessions = CheckoutIdentitySessions { ownerUserId ->
                assertEquals(ownerId, ownerUserId)
                failure?.let { throw it }
                customToken
            },
            clock = clock,
        ),
    )
}
