package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GooglePlaySubscriptionTest {
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val now = Instant.parse("2026-10-18T12:00:00Z")
    private val expires = Instant.parse("2026-11-18T12:00:00Z")

    @Test
    fun `each plan and cycle maps to its product and base plan`() {
        for (plan in Plan.entries) {
            for (cycle in SubscriptionCycle.entries) {
                val product = GooglePlayProduct.of(plan, cycle)
                assertEquals(plan, product.plan)
                assertEquals(cycle, product.cycle)
                assertEquals("app.saqz.${plan.name.lowercase()}", product.productId)
                assertEquals(if (cycle == SubscriptionCycle.MONTHLY) "mensal" else "anual", product.basePlanId)
                assertEquals(product, GooglePlayProduct.of(product.productId, product.basePlanId))
            }
        }
    }

    @Test
    fun `product lookup needs both the product id and the base plan to match`() {
        assertNull(GooglePlayProduct.of("app.saqz.titular", "semanal"))
        assertNull(GooglePlayProduct.of("app.saqz.titular", null))
        assertNull(GooglePlayProduct.of("app.outro", "mensal"))
        assertNull(GooglePlayProduct.of(null, "mensal"))
    }

    @Test
    fun `state is read from the subscriptionsv2 name and unknown values are null`() {
        for (state in GooglePlayState.entries) {
            assertEquals(state, GooglePlayState.fromApi("SUBSCRIPTION_STATE_${state.name}"))
        }
        assertNull(GooglePlayState.fromApi("ACTIVE"))
        assertNull(GooglePlayState.fromApi("SUBSCRIPTION_STATE_OUTRO"))
        assertNull(GooglePlayState.fromApi(null))
    }

    @Test
    fun `only active, canceled and grace period entitle and only until the paid period ends`() {
        val entitling = setOf(GooglePlayState.ACTIVE, GooglePlayState.CANCELED, GooglePlayState.IN_GRACE_PERIOD)
        assertEquals(entitling, GooglePlaySubscription.ENTITLING_STATES)
        for (state in GooglePlayState.entries) {
            val subscription = stored(state = state)
            assertEquals(state in entitling, subscription.isEntitlingAt(expires.minusSeconds(1)), state.name)
            assertFalse(subscription.isEntitlingAt(expires), state.name)
        }
    }

    @Test
    fun `a superseded subscription never entitles`() {
        assertFalse(stored(supersededAt = now).isEntitlingAt(expires.minusSeconds(1)))
    }

    @Test
    fun `starting from a purchase copies every field and the pending plan`() {
        val purchase = purchase(
            state = GooglePlayState.CANCELED,
            autoRenew = false,
            canceledAt = now,
            linkedPurchaseToken = "token_old",
            latestOrderId = "GPA.1",
            acknowledged = true,
            testPurchase = true,
            pendingProductId = "app.saqz.titular",
            pendingBasePlanId = "mensal",
        )

        val started = GooglePlaySubscription.startedBy(purchase, GooglePlayProduct.ORGANIZADOR_MENSAL, owner)

        assertEquals(
            GooglePlaySubscription(
                purchaseToken = "token_1",
                ownerUserId = owner,
                product = GooglePlayProduct.ORGANIZADOR_MENSAL,
                state = GooglePlayState.CANCELED,
                expiresAt = expires,
                autoRenew = false,
                canceledAt = now,
                latestOrderId = "GPA.1",
                linkedPurchaseToken = "token_old",
                supersededAt = null,
                acknowledged = true,
                testPurchase = true,
                pendingPlan = Plan.TITULAR,
            ),
            started,
        )
    }

    @Test
    fun `starting from a plain purchase keeps the test flag off and no pending plan`() {
        val started = GooglePlaySubscription.startedBy(purchase(), GooglePlayProduct.ORGANIZADOR_MENSAL, owner)

        assertFalse(started.testPurchase)
        assertFalse(started.acknowledged)
        assertNull(started.pendingPlan)
        assertNull(started.supersededAt)
        assertEquals(true, started.autoRenew)
        assertEquals("GPA.0", started.latestOrderId)
    }

    @Test
    fun `starting from a purchase without expiry time is refused`() {
        assertFailsWith<IllegalArgumentException> {
            GooglePlaySubscription.startedBy(purchase(expiresAt = null), GooglePlayProduct.TITULAR_MENSAL, owner)
        }
    }

    @Test
    fun `a refresh takes the state of the last read`() {
        val refreshed = stored(autoRenew = true, testPurchase = false).refreshedFrom(
            purchase(
                state = GooglePlayState.IN_GRACE_PERIOD,
                expiresAt = expires.plusSeconds(3_600),
                autoRenew = false,
                canceledAt = now,
                linkedPurchaseToken = "token_new",
                latestOrderId = "GPA.2",
                testPurchase = true,
            ),
            GooglePlayProduct.ILIMITADO_ANUAL,
        )

        assertEquals(GooglePlayProduct.ILIMITADO_ANUAL, refreshed.product)
        assertEquals(GooglePlayState.IN_GRACE_PERIOD, refreshed.state)
        assertEquals(expires.plusSeconds(3_600), refreshed.expiresAt)
        assertEquals(false, refreshed.autoRenew)
        assertEquals(now, refreshed.canceledAt)
        assertEquals("GPA.2", refreshed.latestOrderId)
        assertEquals("token_new", refreshed.linkedPurchaseToken)
        assertTrue(refreshed.testPurchase)
        assertEquals(owner, refreshed.ownerUserId)
        assertEquals("token_1", refreshed.purchaseToken)
    }

    @Test
    fun `a refresh keeps expiry and order id the read did not bring`() {
        val refreshed = stored(latestOrderId = "GPA.1").refreshedFrom(
            purchase(expiresAt = null, latestOrderId = null),
            GooglePlayProduct.TITULAR_MENSAL,
        )

        assertEquals(expires, refreshed.expiresAt)
        assertEquals("GPA.1", refreshed.latestOrderId)
    }

    @Test
    fun `a refresh never takes back an acknowledgement`() {
        assertTrue(stored(acknowledged = true).refreshedFrom(purchase(acknowledged = false), GooglePlayProduct.TITULAR_MENSAL).acknowledged)
        assertTrue(stored(acknowledged = false).refreshedFrom(purchase(acknowledged = true), GooglePlayProduct.TITULAR_MENSAL).acknowledged)
        assertFalse(stored(acknowledged = false).refreshedFrom(purchase(acknowledged = false), GooglePlayProduct.TITULAR_MENSAL).acknowledged)
    }

    @Test
    fun `a refresh recomputes the pending plan from the new read`() {
        val withPending = stored(pendingPlan = Plan.TITULAR)

        assertNull(withPending.refreshedFrom(purchase(), GooglePlayProduct.ORGANIZADOR_MENSAL).pendingPlan)
        assertEquals(
            Plan.ILIMITADO,
            stored().refreshedFrom(
                purchase(pendingProductId = "app.saqz.ilimitado", pendingBasePlanId = "mensal"),
                GooglePlayProduct.ORGANIZADOR_MENSAL,
            ).pendingPlan,
        )
    }

    @Test
    fun `pending plan is the deferred plan only when it differs from the current one`() {
        val current = GooglePlayProduct.ORGANIZADOR_MENSAL

        assertNull(purchase().pendingPlanAfter(current))
        assertNull(purchase(pendingProductId = "app.saqz.organizador", pendingBasePlanId = "anual").pendingPlanAfter(current))
        assertEquals(
            Plan.TITULAR,
            purchase(pendingProductId = "app.saqz.titular", pendingBasePlanId = "mensal").pendingPlanAfter(current),
        )
    }

    @Test
    fun `pending plan falls back to the product when the base plan is unknown`() {
        val current = GooglePlayProduct.ORGANIZADOR_MENSAL

        assertEquals(
            Plan.ILIMITADO,
            purchase(pendingProductId = "app.saqz.ilimitado", pendingBasePlanId = "trimestral").pendingPlanAfter(current),
        )
        assertEquals(
            Plan.ILIMITADO,
            purchase(pendingProductId = "app.saqz.ilimitado", pendingBasePlanId = null).pendingPlanAfter(current),
        )
        assertNull(purchase(pendingProductId = "app.outro", pendingBasePlanId = "mensal").pendingPlanAfter(current))
    }

    private fun purchase(
        state: GooglePlayState = GooglePlayState.ACTIVE,
        expiresAt: Instant? = expires,
        autoRenew: Boolean? = true,
        canceledAt: Instant? = null,
        linkedPurchaseToken: String? = null,
        latestOrderId: String? = "GPA.0",
        acknowledged: Boolean = false,
        testPurchase: Boolean = false,
        pendingProductId: String? = null,
        pendingBasePlanId: String? = null,
    ) = GooglePlayPurchase(
        purchaseToken = "token_1",
        productId = "app.saqz.organizador",
        basePlanId = "mensal",
        state = state,
        expiresAt = expiresAt,
        autoRenew = autoRenew,
        canceledAt = canceledAt,
        obfuscatedAccountId = owner.toString(),
        linkedPurchaseToken = linkedPurchaseToken,
        latestOrderId = latestOrderId,
        acknowledged = acknowledged,
        testPurchase = testPurchase,
        pendingProductId = pendingProductId,
        pendingBasePlanId = pendingBasePlanId,
    )

    private fun stored(
        state: GooglePlayState = GooglePlayState.ACTIVE,
        autoRenew: Boolean? = true,
        latestOrderId: String? = "GPA.0",
        supersededAt: Instant? = null,
        acknowledged: Boolean = false,
        testPurchase: Boolean = false,
        pendingPlan: Plan? = null,
    ) = GooglePlaySubscription(
        purchaseToken = "token_1",
        ownerUserId = owner,
        product = GooglePlayProduct.ORGANIZADOR_MENSAL,
        state = state,
        expiresAt = expires,
        autoRenew = autoRenew,
        canceledAt = null,
        latestOrderId = latestOrderId,
        linkedPurchaseToken = null,
        supersededAt = supersededAt,
        acknowledged = acknowledged,
        testPurchase = testPurchase,
        pendingPlan = pendingPlan,
    )
}
