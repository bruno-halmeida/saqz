package br.com.saqz.subscriptions.adapter.output.asaas

import br.com.saqz.subscriptions.application.AsaasIdempotencyStore
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InMemoryAsaasIdempotencyStore : AsaasIdempotencyStore {
    private data class Entry(val createdAt: Instant, var resourceId: String?)

    private val entries = ConcurrentHashMap<String, Entry>()

    override fun tryBegin(key: String, now: Instant): Boolean {
        val existing = entries.putIfAbsent(key, Entry(now, null))
        return existing == null
    }

    override fun findResourceId(key: String): String? = entries[key]?.resourceId

    override fun complete(key: String, resourceId: String) {
        entries.computeIfPresent(key) { _, entry -> entry.copy(resourceId = resourceId) }
    }

    override fun release(key: String) {
        entries.computeIfPresent(key) { _, entry ->
            if (entry.resourceId == null) null else entry
        }
    }
}
