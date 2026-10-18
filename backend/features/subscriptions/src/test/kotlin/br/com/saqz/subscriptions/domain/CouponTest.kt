package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CouponTest {
    @Test
    fun `discount percent must be between 1 and 100`() {
        assertFailsWith<IllegalArgumentException> { coupon(discountPercent = 0) }
        assertFailsWith<IllegalArgumentException> { coupon(discountPercent = 101) }
    }

    @Test
    fun `duration cycles must be at least 1 when present`() {
        assertFailsWith<IllegalArgumentException> { coupon(durationCycles = 0) }
    }

    @Test
    fun `the limits themselves are valid`() {
        assertEquals(1, coupon(discountPercent = 1).discountPercent)
        assertEquals(100, coupon(discountPercent = 100).discountPercent)
        assertEquals(1, coupon(durationCycles = 1).durationCycles)
        assertNull(coupon(durationCycles = null).durationCycles)
    }

    @Test
    fun `a negative discount is refused`() {
        assertFailsWith<IllegalArgumentException> { coupon(discountPercent = -5) }
        assertFailsWith<IllegalArgumentException> { coupon(durationCycles = -1) }
    }

    private fun coupon(discountPercent: Int = 10, durationCycles: Int? = 3) = Coupon(
        id = UUID.randomUUID(),
        code = "WELCOME10",
        discountPercent = discountPercent,
        durationCycles = durationCycles,
    )
}
