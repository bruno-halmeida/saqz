package br.com.saqz.subscriptions.application

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class GooglePlayOrderAmountsTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val store = MemoryStore()
    private val api = ScriptedOrders()
    private val amounts = GooglePlayOrderAmounts(api, store, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `fetch saves the amount right after the order is recorded and skips orders that already have one`() {
        store.missing += "GPA.1"
        api.known["GPA.1"] = amount("GPA.1")

        amounts.fetch("GPA.1")
        amounts.fetch("GPA.1")

        assertEquals(mapOf("GPA.1" to amount("GPA.1")), store.saved)
        assertEquals(listOf(listOf("GPA.1")), api.calls)
    }

    @Test
    fun `a failing Orders API leaves the order without amount for the job`() {
        store.missing += "GPA.1"
        api.unavailable = true

        amounts.fetch("GPA.1")

        assertEquals(emptyMap(), store.saved)
        assertEquals(mapOf("GPA.1" to now), store.checked)
    }

    @Test
    fun `backfill falls back to one by one when one id rejects the whole batch`() {
        store.missing += listOf("GPA.1", "GPA.ruim", "GPA.2")
        api.known["GPA.1"] = amount("GPA.1")
        api.known["GPA.2"] = amount("GPA.2")

        assertEquals(2, amounts.backfill())

        assertEquals(setOf("GPA.1", "GPA.2"), store.saved.keys)
        assertEquals(mapOf("GPA.ruim" to now), store.checked)
        assertEquals(listOf("GPA.1", "GPA.ruim", "GPA.2"), api.calls.first())
        assertEquals(now.minusSeconds(3_600), store.lastCheckedBefore)
    }

    @Test
    fun `backfill with the API down marks the batch and returns nothing`() {
        store.missing += listOf("GPA.1", "GPA.2")
        api.unavailable = true

        assertEquals(0, amounts.backfill())
        assertEquals(setOf("GPA.1", "GPA.2"), store.checked.keys)
        assertEquals(0, GooglePlayOrderAmounts(api, MemoryStore()).backfill())
    }

    private fun amount(id: String) = GooglePlayOrderAmount(id, 5_990, 540, "BRL", "PROCESSED")

    private class ScriptedOrders : GooglePlayOrdersApi {
        val known = mutableMapOf<String, GooglePlayOrderAmount>()
        val calls = mutableListOf<List<String>>()
        var unavailable = false

        override fun orders(orderIds: List<String>): List<GooglePlayOrderAmount> {
            calls += orderIds
            if (unavailable) throw GooglePlayUnavailableException("fora do ar")
            if (orderIds.any { it !in known }) throw GooglePlayOrdersRejectedException("404")
            return orderIds.map { known.getValue(it) }
        }
    }

    private class MemoryStore : GooglePlayOrderAmountStore {
        val missing = mutableListOf<String>()
        val saved = linkedMapOf<String, GooglePlayOrderAmount>()
        val checked = linkedMapOf<String, Instant>()
        var lastCheckedBefore: Instant? = null

        override fun orderAmountMissing(orderId: String) = orderId in missing && orderId !in saved

        override fun ordersWithoutAmount(checkedBefore: Instant, limit: Int): List<String> {
            lastCheckedBefore = checkedBefore
            return missing.filter { it !in saved }.take(limit)
        }

        override fun saveOrderAmount(amount: GooglePlayOrderAmount, at: Instant) {
            saved[amount.orderId] = amount
        }

        override fun markOrderAmountChecked(orderIds: List<String>, at: Instant) {
            orderIds.forEach { checked[it] = at }
        }
    }
}
