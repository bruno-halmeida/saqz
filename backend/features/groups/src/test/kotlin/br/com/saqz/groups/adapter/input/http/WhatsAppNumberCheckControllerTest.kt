package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WhatsAppNumberCheckControllerTest {
    @Test
    fun `repassa a resposta do provedor para o numero sem espacos`() {
        val asked = mutableListOf<String>()
        val controller = WhatsAppNumberCheckController { phone -> asked += phone; false }

        assertEquals(WhatsAppNumberCheckResponse(hasWhatsApp = false), controller.check(" +5511987654321 "))
        assertEquals(listOf("+5511987654321"), asked)
    }

    @Test
    fun `sem verificador ou sem resposta o app nao recebe nada para avisar`() {
        assertEquals(WhatsAppNumberCheckResponse(null), WhatsAppNumberCheckController(null).check("+5511987654321"))
        assertEquals(WhatsAppNumberCheckResponse(null), WhatsAppNumberCheckController(WhatsAppNumberCheck { null }).check("+5511987654321"))
    }
}
