package br.com.saqz.subscriptions.domain

import java.time.Instant
import java.util.UUID

data class CouponRedemption(
    val couponId: UUID,
    val userId: String,
    val redeemedAt: Instant,
)
