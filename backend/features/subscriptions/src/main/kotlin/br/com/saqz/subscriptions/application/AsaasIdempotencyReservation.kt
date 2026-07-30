package br.com.saqz.subscriptions.application

import java.time.Instant

data class AsaasIdempotencyReservation(
    val resourceId: String?,
    val createdAt: Instant,
)
