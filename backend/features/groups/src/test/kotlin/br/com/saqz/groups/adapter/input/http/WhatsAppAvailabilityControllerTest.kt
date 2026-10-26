package br.com.saqz.groups.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.ClientApp
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class WhatsAppAvailabilityControllerTest {
    @Test
    fun `answers what the server was started with`() {
        assertEquals(WhatsAppAvailabilityResponse(enabled = true), WhatsAppAvailabilityController(enabled = true).availability(null, null))
        assertEquals(WhatsAppAvailabilityResponse(enabled = false), WhatsAppAvailabilityController(enabled = false).availability(null, null))
    }

    @Test
    fun `asks the flag with the subject and the app of the call`() {
        var asked: Pair<String?, ClientApp>? = null
        val controller = WhatsAppAvailabilityController { subject, client -> asked = subject to client; subject == "on" }
        assertEquals(WhatsAppAvailabilityResponse(enabled = true), controller.availability(RequestIdentity("on"), null))
        assertEquals("on" to ClientApp.Legacy, asked)
        assertEquals(WhatsAppAvailabilityResponse(enabled = false), controller.availability(RequestIdentity("off"), null))
    }
}
