package br.com.saqz.bootstrap.configuration

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsaasWebhookTokenConfiguredConditionTest {
    @Test
    fun `matches non-blank tokens including quotes and special chars`() {
        assertTrue(AsaasWebhookTokenConfiguredCondition.isConfigured("token-with-'quotes'-and-\${braces}"))
        assertTrue(AsaasWebhookTokenConfiguredCondition.isConfigured(" plain "))
    }

    @Test
    fun `rejects missing blank or empty tokens`() {
        assertFalse(AsaasWebhookTokenConfiguredCondition.isConfigured(null))
        assertFalse(AsaasWebhookTokenConfiguredCondition.isConfigured(""))
        assertFalse(AsaasWebhookTokenConfiguredCondition.isConfigured("   "))
    }
}
