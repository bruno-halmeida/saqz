package br.com.saqz.groups.application.communication

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderMessageTest {
    @Test fun `formats the schedule with the pt br weekday and zero padded values`() {
        assertEquals("domingo, 20/09 às 19:30", gameSchedule(LocalDate.of(2026, 9, 20), LocalTime.of(19, 30)))
        assertEquals("quarta, 12/08 às 07:05", gameSchedule(LocalDate.of(2026, 8, 12), LocalTime.of(7, 5)))
    }

    @Test fun `formats the game and venue lines and the sections in order with one name per line`() {
        assertEquals(
            "Jogo: domingo, 20/09 às 19:30\n" +
                "Local: Arena Central\n\n" +
                "✅ Confirmados:\nAna\nBruno\n\n" +
                "🕒 Lista de espera:\nCaio\n\n" +
                "❌ Fora:\nDora\nElisa",
            reminderBody(
                game(),
                ReminderRoster(
                    confirmed = listOf("Ana", "Bruno"),
                    waitlisted = listOf("Caio"),
                    declined = listOf("Dora", "Elisa"),
                ),
            ),
        )
    }

    @Test fun `omits empty sections and keeps the game and venue lines when nobody responded`() {
        assertEquals(
            "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central",
            reminderBody(game(), ReminderRoster(emptyList(), emptyList(), emptyList())),
        )
        assertEquals(
            "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central\n\n🕒 Lista de espera:\nAna",
            reminderBody(game(), ReminderRoster(emptyList(), listOf("Ana"), emptyList())),
        )
        assertEquals(
            "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central\n\n❌ Fora:\nAna",
            reminderBody(game(), ReminderRoster(emptyList(), emptyList(), listOf("Ana"))),
        )
    }

    @Test fun `clips long rosters to the body limit on a line boundary`() {
        val names = (1..400).map { "Atleta de nome comprido número %03d".format(it) }
        val body = reminderBody(game(), ReminderRoster(names, emptyList(), emptyList()))
        assertTrue(body.length <= REMINDER_BODY_LIMIT, "length ${body.length}")
        assertTrue(body.endsWith("\n…"), body)
        assertTrue(
            body.startsWith(
                "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central\n\n✅ Confirmados:\n" +
                    "Atleta de nome comprido número 001\nAtleta de nome comprido número 002\n",
            ),
            body,
        )
    }

    @Test fun `adds the monthly priority deadline and the vacancies before the deadline`() {
        // 2026-09-17 17:00Z = quinta 14h00 em São Paulo
        val deadline = Instant.parse("2026-09-17T17:00:00Z")
        val body = reminderBody(
            game(deadline, 12, priority = true),
            ReminderRoster(List(10) { "Atleta $it" }, emptyList(), emptyList()),
            now = Instant.parse("2026-09-17T14:00:00Z"),
        )
        assertTrue(
            body.startsWith(
                "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central\nPrioridade dos mensalistas até qui 14h00\nVagas: 10/12\n\n✅ Confirmados:\n",
            ),
            body,
        )
    }

    @Test fun `prints no deadline line after the deadline and says the list is complete`() {
        val deadline = Instant.parse("2026-09-17T17:00:00Z")
        assertEquals(
            "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central\nLista completa\n\n" +
                "✅ Confirmados:\nAna\nBruno\n\n🕒 Lista de espera:\nCaio",
            reminderBody(
                game(deadline, 2, priority = true),
                ReminderRoster(listOf("Ana", "Bruno"), listOf("Caio"), emptyList()),
                now = Instant.parse("2026-09-18T12:00:00Z"),
            ),
        )
    }

    @Test fun `keeps the waitlist in the order it is given, which is the promotion order`() {
        assertEquals(
            "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central\n\n🕒 Lista de espera:\nZilda\nAna\nBruno",
            reminderBody(game(), ReminderRoster(emptyList(), listOf("Zilda", "Ana", "Bruno"), emptyList())),
        )
    }

    @Test fun `treats the exact deadline instant as over`() {
        val deadline = Instant.parse("2026-09-17T17:00:00Z")
        assertEquals(null, deadlineLine(deadline, deadline, mensalistaPriority = true))
    }

    @Test fun `prints no deadline line when the group has no monthly priority`() {
        val deadline = Instant.parse("2026-09-17T17:00:00Z")
        assertEquals(
            "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central\nVagas: 1/12\n\n✅ Confirmados:\nAna",
            reminderBody(game(deadline, 12), ReminderRoster(listOf("Ana"), emptyList(), emptyList()), now = Instant.parse("2026-09-17T14:00:00Z")),
        )
    }

    private fun game(deadline: Instant? = null, capacity: Int? = null, priority: Boolean = false) =
        ReminderGame(LocalDate.of(2026, 9, 20), LocalTime.of(19, 30), "Arena Central", deadline, capacity, priority)
}
