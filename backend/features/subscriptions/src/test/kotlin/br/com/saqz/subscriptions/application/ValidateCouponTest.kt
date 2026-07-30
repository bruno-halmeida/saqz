package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidateCouponTest {
    private val now = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `applied coupon returns percent and final monthly price`() {
        val coupons = FixedCouponRepository(
            Coupon(
                id = UUID.randomUUID(),
                code = "SAVE20",
                discountPercent = 20,
                validUntil = Instant.parse("2026-12-31T00:00:00Z"),
            ),
        )
        val useCase = ValidateCoupon(coupons, clock)

        val result = useCase.execute("save20", "ORGANIZADOR")

        val applied = assertIs<ValidateCouponResult.Applied>(result)
        assertEquals("SAVE20", applied.code)
        assertEquals(Plan.ORGANIZADOR, applied.planId)
        assertEquals(SubscriptionCycle.MONTHLY, applied.cycle)
        assertEquals(20, applied.discountPercent)
        assertEquals(5_990, applied.listPriceCents)
        assertEquals(4_792, applied.finalPriceCents)
    }

    @Test
    fun `applied coupon on annual cycle uses annual list price`() {
        val coupons = FixedCouponRepository(
            Coupon(
                id = UUID.randomUUID(),
                code = "SAVE20",
                discountPercent = 20,
            ),
        )
        val useCase = ValidateCoupon(coupons, clock)

        val result = useCase.execute("SAVE20", "ORGANIZADOR", "ANNUAL")

        val applied = assertIs<ValidateCouponResult.Applied>(result)
        assertEquals(SubscriptionCycle.ANNUAL, applied.cycle)
        assertEquals(59_900, applied.listPriceCents)
        assertEquals(47_920, applied.finalPriceCents)
    }

    @Test
    fun `missing cycle defaults to monthly`() {
        val coupons = FixedCouponRepository(
            Coupon(id = UUID.randomUUID(), code = "X", discountPercent = 10),
        )
        val applied = assertIs<ValidateCouponResult.Applied>(
            ValidateCoupon(coupons, clock).execute("X", "TITULAR", null),
        )
        assertEquals(SubscriptionCycle.MONTHLY, applied.cycle)
        assertEquals(3_990, applied.listPriceCents)
    }

    @Test
    fun `invalid cycle is rejected`() {
        val coupons = FixedCouponRepository(
            Coupon(id = UUID.randomUUID(), code = "X", discountPercent = 10),
        )
        assertEquals(
            ValidateCouponResult.InvalidCycle,
            ValidateCoupon(coupons, clock).execute("X", "TITULAR", "WEEKLY"),
        )
    }

    @Test
    fun `unknown coupon is not found`() {
        val useCase = ValidateCoupon(FixedCouponRepository(null), clock)

        assertEquals(ValidateCouponResult.NotFound, useCase.execute("MISSING", "TITULAR"))
    }

    @Test
    fun `coupon with validUntil in the past is expired`() {
        val expiredAt = Instant.parse("2026-07-01T00:00:00Z")
        val coupons = FixedCouponRepository(
            Coupon(
                id = UUID.randomUUID(),
                code = "OLD10",
                discountPercent = 10,
                validUntil = expiredAt,
            ),
        )
        val useCase = ValidateCoupon(coupons, clock)

        val result = useCase.execute("OLD10", "TITULAR")

        val expired = assertIs<ValidateCouponResult.Expired>(result)
        assertEquals("OLD10", expired.code)
        assertEquals(expiredAt, expired.validUntil)
    }

    @Test
    fun `coupon valid until exactly now is expired`() {
        val coupons = FixedCouponRepository(
            Coupon(
                id = UUID.randomUUID(),
                code = "EDGE",
                discountPercent = 5,
                validUntil = now,
            ),
        )

        assertIs<ValidateCouponResult.Expired>(ValidateCoupon(coupons, clock).execute("EDGE", "ILIMITADO"))
    }

    @Test
    fun `invalid plan id is rejected`() {
        val useCase = ValidateCoupon(
            FixedCouponRepository(
                Coupon(id = UUID.randomUUID(), code = "OK", discountPercent = 10),
            ),
            clock,
        )

        assertEquals(ValidateCouponResult.InvalidPlan, useCase.execute("OK", "FREE"))
    }

    private class FixedCouponRepository(private val coupon: Coupon?) : CouponRepository {
        override fun findByCode(code: String): Coupon? =
            coupon?.takeIf { it.code.equals(code, ignoreCase = true) }
    }
}
