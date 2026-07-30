package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionLimitsAdapterTest {
    private val ownerId = UUID.randomUUID()

    @Test
    fun `no entitling subscription yields zero group and athlete limits`() {
        val adapter = adapter(null)

        assertEquals(0, adapter.groupLimitFor(ownerId))
        assertEquals(0, adapter.athleteLimitFor(ownerId))
    }

    @Test
    fun `titular returns plan caps`() {
        val adapter = adapter(EntitlingSubscription(Plan.TITULAR))

        assertEquals(1, adapter.groupLimitFor(ownerId))
        assertEquals(25, adapter.athleteLimitFor(ownerId))
    }

    @Test
    fun `organizador returns finite groups and unlimited athletes`() {
        val adapter = adapter(EntitlingSubscription(Plan.ORGANIZADOR))

        assertEquals(3, adapter.groupLimitFor(ownerId))
        assertNull(adapter.athleteLimitFor(ownerId))
    }

    @Test
    fun `ilimitado returns unlimited groups and athletes`() {
        val adapter = adapter(EntitlingSubscription(Plan.ILIMITADO))

        assertNull(adapter.groupLimitFor(ownerId))
        assertNull(adapter.athleteLimitFor(ownerId))
    }

    @Test
    fun `pending downgrade uses the more restrictive group limit`() {
        val adapter = adapter(
            EntitlingSubscription(plan = Plan.ORGANIZADOR, pendingPlan = Plan.TITULAR),
        )

        assertEquals(1, adapter.groupLimitFor(ownerId))
        assertEquals(25, adapter.athleteLimitFor(ownerId))
    }

    @Test
    fun `pending downgrade from ilimitado to organizador caps groups only`() {
        val adapter = adapter(
            EntitlingSubscription(plan = Plan.ILIMITADO, pendingPlan = Plan.ORGANIZADOR),
        )

        assertEquals(3, adapter.groupLimitFor(ownerId))
        assertNull(adapter.athleteLimitFor(ownerId))
    }

    @Test
    fun `pending upgrade does not loosen current limits before effective date`() {
        val adapter = adapter(
            EntitlingSubscription(plan = Plan.TITULAR, pendingPlan = Plan.ILIMITADO),
        )

        assertEquals(1, adapter.groupLimitFor(ownerId))
        assertEquals(25, adapter.athleteLimitFor(ownerId))
    }

    @Test
    fun `moreRestrictive prefers finite caps over unlimited`() {
        assertEquals(1, SubscriptionLimitsAdapter.moreRestrictive(null, 1))
        assertEquals(1, SubscriptionLimitsAdapter.moreRestrictive(1, null))
        assertEquals(1, SubscriptionLimitsAdapter.moreRestrictive(3, 1))
        assertNull(SubscriptionLimitsAdapter.moreRestrictive(null, null))
    }

    private fun adapter(subscription: EntitlingSubscription?) =
        SubscriptionLimitsAdapter { id ->
            check(id == ownerId)
            subscription
        }
}
