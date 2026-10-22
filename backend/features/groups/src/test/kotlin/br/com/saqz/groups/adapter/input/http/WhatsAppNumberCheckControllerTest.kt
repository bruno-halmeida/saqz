package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import br.com.saqz.groups.application.ratelimit.PerUserRateLimiter
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class WhatsAppNumberCheckControllerTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC)
    private val identity = RequestIdentity("user-subject", "user@saqz.test", true)
    private val other = RequestIdentity("other-subject", "other@saqz.test", true)

    private fun controller(perMinute: Int = WhatsAppNumberCheckController.CHECKS_PER_MINUTE, check: WhatsAppNumberCheck?) =
        WhatsAppNumberCheckController(check, PerUserRateLimiter(clock, perMinute))

    @Test
    fun `repassa a resposta do provedor para o numero sem espacos`() {
        val asked = mutableListOf<String>()
        val controller = controller { phone -> asked += phone; false }

        assertEquals(WhatsAppNumberCheckResponse(hasWhatsApp = false), controller.check(identity, " +5511987654321 "))
        assertEquals(listOf("+5511987654321"), asked)
    }

    @Test
    fun `sem verificador ou sem resposta o app nao recebe nada para avisar`() {
        assertEquals(WhatsAppNumberCheckResponse(null), controller(check = null).check(identity, "+5511987654321"))
        assertEquals(WhatsAppNumberCheckResponse(null), controller(check = WhatsAppNumberCheck { null }).check(identity, "+5511987654321"))
    }

    @Test
    fun `alem da cota por usuario o provedor nao e consultado e a resposta e vazia`() {
        val asked = mutableListOf<String>()
        val controller = controller(perMinute = 2) { phone -> asked += phone; true }

        repeat(2) { assertEquals(WhatsAppNumberCheckResponse(true), controller.check(identity, "+551198765432$it")) }
        assertEquals(WhatsAppNumberCheckResponse(null), controller.check(identity, "+5511987654329"))
        assertEquals(WhatsAppNumberCheckResponse(true), controller.check(other, "+5511987654329"))

        assertEquals(3, asked.size)
    }
}
