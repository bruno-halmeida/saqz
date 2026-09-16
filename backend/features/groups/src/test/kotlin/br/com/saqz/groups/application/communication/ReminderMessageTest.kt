package br.com.saqz.groups.application.communication

import org.junit.jupiter.api.Test
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

    private fun game() = ReminderGame(LocalDate.of(2026, 9, 20), LocalTime.of(19, 30), "Arena Central")
}
