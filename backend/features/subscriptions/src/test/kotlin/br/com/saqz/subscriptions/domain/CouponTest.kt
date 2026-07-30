package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

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

    private fun coupon(discountPercent: Int = 10, durationCycles: Int? = 3) = Coupon(
        id = UUID.randomUUID(),
        code = "WELCOME10",
        discountPercent = discountPercent,
        durationCycles = durationCycles,
    )
}
