package br.com.saqz.groups.application.communication

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderMessageTest {
    @Test fun `formats the title and the sections in order with emojis`() {
        assertEquals(
            "*Treino de sábado*\n\n" +
                "✅ Confirmados:\nAna, Bruno\n\n" +
                "🕒 Lista de espera:\nCaio\n\n" +
                "❌ Fora:\nDora\n\n" +
                "⏳ A confirmar:\nElisa, Fábio",
            reminderBody(
                "Treino de sábado",
                ReminderRoster(
                    confirmed = listOf("Ana", "Bruno"),
                    waitlisted = listOf("Caio"),
                    declined = listOf("Dora"),
                    pending = listOf("Elisa", "Fábio"),
                ),
            ),
        )
    }

    @Test fun `omits empty sections and keeps a lone title when nobody responded`() {
        assertEquals(
            "*Treino*\n\n⏳ A confirmar:\nAna",
            reminderBody("Treino", ReminderRoster(emptyList(), emptyList(), emptyList(), listOf("Ana"))),
        )
        assertEquals(
            "*Treino*\n\n🕒 Lista de espera:\nAna",
            reminderBody("Treino", ReminderRoster(emptyList(), listOf("Ana"), emptyList(), emptyList())),
        )
        assertEquals("*Treino*", reminderBody("Treino", ReminderRoster(emptyList(), emptyList(), emptyList(), emptyList())))
    }

    @Test fun `clips long rosters to the body limit without cutting a name`() {
        val names = (1..400).map { "Atleta de nome comprido número %03d".format(it) }
        val body = reminderBody(
            "Treino",
            ReminderRoster(confirmed = names, waitlisted = emptyList(), declined = emptyList(), pending = emptyList()),
        )
        assertTrue(body.length <= REMINDER_BODY_LIMIT, "length ${body.length}")
        assertTrue(body.endsWith("…"), body)
        assertTrue(body.startsWith("*Treino*\n\n✅ Confirmados:\nAtleta de nome comprido número 001, "), body)
    }
}
