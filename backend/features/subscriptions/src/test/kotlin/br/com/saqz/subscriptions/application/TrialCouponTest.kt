package br.com.saqz.subscriptions.application

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrialCouponTest {
    private val now = Instant.parse("2026-10-18T12:00:00Z")

    private fun coupon(
        active: Boolean = true,
        validUntil: Instant? = null,
        maxUses: Int? = null,
        uses: Long = 0,
    ) = TrialCoupon(
        id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
        code = "COPA",
        campaign = "copa-2026",
        trialDays = 30,
        validUntil = validUntil,
        maxUses = maxUses,
        active = active,
        uses = uses,
    )

    @Test
    fun `an open coupon is available`() {
        assertTrue(coupon().isAvailable(now))
    }

    @Test
    fun `an inactive coupon is never available`() {
        assertFalse(coupon(active = false).isAvailable(now))
        assertFalse(coupon(active = false, validUntil = now.plusSeconds(60), maxUses = 10).isAvailable(now))
    }

    @Test
    fun `a coupon is available until the instant it expires`() {
        assertTrue(coupon(validUntil = now.plusSeconds(1)).isAvailable(now))
        assertFalse(coupon(validUntil = now).isAvailable(now))
        assertFalse(coupon(validUntil = now.minusSeconds(1)).isAvailable(now))
    }

    @Test
    fun `a coupon is available while uses are below the maximum`() {
        assertTrue(coupon(maxUses = 10, uses = 9).isAvailable(now))
        assertFalse(coupon(maxUses = 10, uses = 10).isAvailable(now))
        assertFalse(coupon(maxUses = 10, uses = 11).isAvailable(now))
        assertTrue(coupon(maxUses = null, uses = 1_000_000).isAvailable(now))
    }

    @Test
    fun `every condition must hold at once`() {
        assertTrue(coupon(validUntil = now.plusSeconds(60), maxUses = 10, uses = 9).isAvailable(now))
        assertFalse(coupon(validUntil = now.minusSeconds(60), maxUses = 10, uses = 9).isAvailable(now))
        assertFalse(coupon(validUntil = now.plusSeconds(60), maxUses = 10, uses = 10).isAvailable(now))
    }

    @Test
    fun `the open offer is always available and grants the default 14 days`() {
        val owner = UUID.randomUUID()
        var started: Int? = null

        assertTrue(TrialOffer.Open.isAvailable(owner))
        assertTrue(TrialOffer.Open.grant(owner) { started = it })
        assertEquals(14, started)
    }
}
