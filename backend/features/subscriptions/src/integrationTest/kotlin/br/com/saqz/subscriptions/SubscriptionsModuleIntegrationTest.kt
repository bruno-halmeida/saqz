package br.com.saqz.subscriptions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SubscriptionsModuleIntegrationTest {
    @Test
    fun `executes the subscriptions PostgreSQL integration source set`() {
        assertEquals("br.com.saqz.subscriptions", javaClass.packageName)
    }
}
