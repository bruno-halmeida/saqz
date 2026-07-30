package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.application.ValidateCoupon
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CouponControllerTest {
    private val now = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `maps applied coupon`() {
        val controller = CouponController(
            ValidateCoupon(
                FixedCoupons(
                    Coupon(id = UUID.randomUUID(), code = "SAVE10", discountPercent = 10),
                ),
                clock,
            ),
        )

        val response = controller.validate(ValidateCouponRequest("SAVE10", "TITULAR"))

        assertEquals("APPLIED", response.status)
        assertEquals(Plan.TITULAR, response.planId)
        assertEquals(SubscriptionCycle.MONTHLY, response.cycle)
        assertEquals(10, response.discountPercent)
        assertEquals(3_990, response.listPriceCents)
        assertEquals(3_591, response.finalPriceCents)
    }

    @Test
    fun `maps applied annual coupon`() {
        val controller = CouponController(
            ValidateCoupon(
                FixedCoupons(
                    Coupon(id = UUID.randomUUID(), code = "SAVE10", discountPercent = 10),
                ),
                clock,
            ),
        )

        val response = controller.validate(
            ValidateCouponRequest("SAVE10", "TITULAR", "ANNUAL"),
        )

        assertEquals("APPLIED", response.status)
        assertEquals(SubscriptionCycle.ANNUAL, response.cycle)
        assertEquals(39_900, response.listPriceCents)
        assertEquals(35_910, response.finalPriceCents)
    }

    @Test
    fun `maps not found`() {
        val controller = CouponController(ValidateCoupon(FixedCoupons(null), clock))
        val response = controller.validate(ValidateCouponRequest("NOPE", "TITULAR"))
        assertEquals("NOT_FOUND", response.status)
        assertNull(response.discountPercent)
    }

    @Test
    fun `maps expired`() {
        val validUntil = Instant.parse("2026-01-01T00:00:00Z")
        val controller = CouponController(
            ValidateCoupon(
                FixedCoupons(
                    Coupon(
                        id = UUID.randomUUID(),
                        code = "OLD",
                        discountPercent = 15,
                        validUntil = validUntil,
                    ),
                ),
                clock,
            ),
        )

        val response = controller.validate(ValidateCouponRequest("OLD", "ORGANIZADOR"))
        assertEquals("EXPIRED", response.status)
        assertEquals(validUntil, response.validUntil)
    }

    @Test
    fun `missing code is validation error`() {
        val controller = CouponController(ValidateCoupon(FixedCoupons(null), clock))
        assertThrows<InvalidCouponRequestException> {
            controller.validate(ValidateCouponRequest(null, "TITULAR"))
        }
    }

    private class FixedCoupons(private val coupon: Coupon?) : CouponRepository {
        override fun findByCode(code: String): Coupon? = coupon
        override fun findById(couponId: java.util.UUID): Coupon? = coupon?.takeIf { it.id == couponId }
        override fun hasRedemption(couponId: java.util.UUID, userId: java.util.UUID) = false
        override fun saveRedemption(redemption: br.com.saqz.subscriptions.domain.CouponRedemption) = error("unused")
    }
}
