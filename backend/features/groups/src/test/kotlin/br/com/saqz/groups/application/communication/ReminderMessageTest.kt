package br.com.saqz.groups.application.communication

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderMessageTest {
    @Test fun `formats the title and the sections in order with one name per line`() {
        assertEquals(
            "*Treino de sábado*\n\n" +
                "✅ Confirmados:\nAna\nBruno\n\n" +
                "🕒 Lista de espera:\nCaio\n\n" +
                "❌ Fora:\nDora\nElisa",
            reminderBody(
                "Treino de sábado",
                ReminderRoster(
                    confirmed = listOf("Ana", "Bruno"),
                    waitlisted = listOf("Caio"),
                    declined = listOf("Dora", "Elisa"),
                ),
            ),
        )
    }

    @Test fun `omits empty sections and keeps a lone title when nobody responded`() {
        assertEquals("*Treino*", reminderBody("Treino", ReminderRoster(emptyList(), emptyList(), emptyList())))
        assertEquals(
            "*Treino*\n\n🕒 Lista de espera:\nAna",
            reminderBody("Treino", ReminderRoster(emptyList(), listOf("Ana"), emptyList())),
        )
        assertEquals(
            "*Treino*\n\n❌ Fora:\nAna",
            reminderBody("Treino", ReminderRoster(emptyList(), emptyList(), listOf("Ana"))),
        )
    }

    @Test fun `clips long rosters to the body limit on a line boundary`() {
        val names = (1..400).map { "Atleta de nome comprido número %03d".format(it) }
        val body = reminderBody("Treino", ReminderRoster(names, emptyList(), emptyList()))
        assertTrue(body.length <= REMINDER_BODY_LIMIT, "length ${body.length}")
        assertTrue(body.endsWith("\n…"), body)
        assertTrue(
            body.startsWith(
                "*Treino*\n\n✅ Confirmados:\n" +
                    "Atleta de nome comprido número 001\nAtleta de nome comprido número 002\n",
            ),
            body,
        )
    }
}
