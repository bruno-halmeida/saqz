package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.SubscriptionEvent
import java.time.Instant
import java.util.UUID

interface SubscriptionEventStore {
    /**
     * Inserts the webhook event for audit/idempotency.
     * @return true when this call won the insert (first delivery), false on redelivery.
     */
    fun tryInsert(
        id: UUID,
        asaasEventId: String,
        type: String,
        payload: String,
        now: Instant,
    ): Boolean

    fun markProcessed(asaasEventId: String, processedAt: Instant)

    /** Processed webhook rows of the given type, newest first. */
    fun listProcessedByType(type: String): List<SubscriptionEvent>
}
