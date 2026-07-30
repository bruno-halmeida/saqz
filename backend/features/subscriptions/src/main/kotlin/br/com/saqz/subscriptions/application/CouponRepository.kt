package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import java.util.UUID

interface CouponRepository {
    fun findByCode(code: String): Coupon?

    fun findById(couponId: UUID): Coupon?

    fun hasRedemption(couponId: UUID, userId: UUID): Boolean

    fun saveRedemption(redemption: CouponRedemption)
}
