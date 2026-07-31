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
        ownerUserId: UUID? = null,
    ): Boolean

    fun markProcessed(asaasEventId: String, processedAt: Instant)

    /** True when this asaasEventId was already inserted (prior delivery). */
    fun exists(asaasEventId: String): Boolean

    /** Processed webhook rows of the given type and owner, newest first. */
    fun listProcessedByTypeForOwner(
        type: String,
        ownerUserId: UUID,
        limit: Int,
        offset: Int,
    ): List<SubscriptionEvent>
}
