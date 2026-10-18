package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.create.TransactionRunner
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class RemindStalledEntryRequestsTest {
    private val passThrough = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
    private fun request(name: String) = StalledEntryRequest(UUID.randomUUID(), UUID.randomUUID(), name, "QA Vôlei", Instant.now())

    @Test fun `conta so os avisos novos e uma falha nao leva os outros`() {
        val ok = request("Rafa")
        val broken = request("Bia")
        val done = request("Caio")
        val failed = mutableListOf<StalledEntryRequest>()
        val reminders = object : StalledEntryRequestReminders {
            override fun stalled() = listOf(broken, ok, done)
            override fun remind(request: StalledEntryRequest) = when (request) {
                broken -> error("falhou")
                done -> false
                else -> true
            }
        }

        assertEquals(1, RemindStalledEntryRequests(passThrough, reminders) { r, _ -> failed += r }.run())
        assertEquals(listOf(broken), failed)
    }

    @Test fun `grupo congelado não recebe o segundo aviso, os outros seguem`() {
        val frozen = request("Rafa")
        val paid = request("Bia")
        val reminded = mutableListOf<StalledEntryRequest>()
        val reminders = object : StalledEntryRequestReminders {
            override fun stalled() = listOf(frozen, paid)
            override fun remind(request: StalledEntryRequest) = true.also { reminded += request }
        }

        assertEquals(1, RemindStalledEntryRequests(passThrough, reminders, { it != frozen.groupId }).run())

        assertEquals(listOf(paid), reminded)
    }

    @Test fun `texto do segundo aviso`() {
        assertEquals("Rafa ainda espera sua resposta para entrar no QA Vôlei. Abra o app para aprovar ou recusar.", entryRequestReminderBody("Rafa", "QA Vôlei"))
    }
}
