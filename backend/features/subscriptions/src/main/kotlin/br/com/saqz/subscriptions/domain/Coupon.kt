package br.com.saqz.subscriptions.domain

import java.time.Instant
import java.util.UUID

data class Coupon(
    val id: UUID,
    val code: String,
    val discountPercent: Int,
    val durationCycles: Int? = null,
    val validUntil: Instant? = null,
) {
    init {
        require(discountPercent in 1..100) { "discountPercent must be between 1 and 100" }
        require(durationCycles == null || durationCycles >= 1) { "durationCycles must be at least 1 when present" }
    }
}
