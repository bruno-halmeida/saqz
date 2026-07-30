package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon

interface CouponRepository {
    fun findByCode(code: String): Coupon?
}
