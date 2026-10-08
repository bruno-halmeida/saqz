package br.com.saqz.groups.adapter.input.http

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class WhatsAppAvailabilityControllerTest {
    @Test
    fun `answers what the server was started with`() {
        assertEquals(WhatsAppAvailabilityResponse(enabled = true), WhatsAppAvailabilityController(enabled = true).availability())
        assertEquals(WhatsAppAvailabilityResponse(enabled = false), WhatsAppAvailabilityController(enabled = false).availability())
    }
}
