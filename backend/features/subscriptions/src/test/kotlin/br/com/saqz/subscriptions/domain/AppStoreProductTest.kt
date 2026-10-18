package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppStoreProductTest {
    @Test
    fun `each plan and cycle maps to its App Store product id`() {
        for (plan in Plan.entries) {
            for (cycle in SubscriptionCycle.entries) {
                val product = AppStoreProduct.of(plan, cycle)
                val cycleName = if (cycle == SubscriptionCycle.MONTHLY) "mensal" else "anual"
                assertEquals(plan, product.plan)
                assertEquals(cycle, product.cycle)
                assertEquals("app.saqz.${plan.name.lowercase()}.$cycleName", product.productId)
                assertEquals(product, AppStoreProduct.fromProductId(product.productId))
            }
        }
    }

    @Test
    fun `unknown or missing product ids resolve to null`() {
        assertNull(AppStoreProduct.fromProductId("app.saqz.titular"))
        assertNull(AppStoreProduct.fromProductId("app.outro.mensal"))
        assertNull(AppStoreProduct.fromProductId(null))
    }
}
