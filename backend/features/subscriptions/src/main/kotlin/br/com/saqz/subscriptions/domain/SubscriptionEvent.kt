package br.com.saqz.subscriptions.domain

import java.time.Instant
import java.util.UUID

data class SubscriptionEvent(
    val id: UUID,
    val asaasEventId: String,
    val type: String,
    val payload: String,
    val processedAt: Instant? = null,
)
