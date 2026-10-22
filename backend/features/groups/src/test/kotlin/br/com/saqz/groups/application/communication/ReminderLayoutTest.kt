package br.com.saqz.groups.application.communication

import br.com.saqz.groups.domain.AthletePosition.CENTRAL
import br.com.saqz.groups.domain.AthletePosition.LEVANTADOR
import br.com.saqz.groups.domain.AthletePosition.LIBERO
import br.com.saqz.groups.domain.AthletePosition.OPOSTO
import br.com.saqz.groups.domain.AthletePosition.PONTA
import br.com.saqz.groups.domain.attendance.GameSystem
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Ajustes de formato do dono (22/10): 6-0 numerado até a capacidade, 5-1 por posição e espera numerada só em FIFO puro. */
class ReminderLayoutTest {
    private val header = "Jogo: domingo, 20/09 às 19:30\nLocal: Arena Central"

    @Test fun `six zero numbers the confirmed up to the capacity with empty slots as bare numbers`() {
        val roster = roster(
            GameSystem.SIX_ZERO, emptyMap(),
            confirmed = listOf(RosterEntry("Ana"), RosterEntry("Bia")),
            waitlisted = listOf(RosterEntry("Caio"), RosterEntry("Duda")),
            declined = listOf(RosterEntry("Eva")),
            numberedWaitlist = true,
        )
        assertEquals(
            "$header\nVagas: 2/4\n\n✅ Confirmados:\n1. Ana\n2. Bia\n3.\n4.\n\n🕒 Lista de espera:\n1. Caio\n2. Duda\n\n❌ Fora:\nEva",
            reminderBody(game(4), roster),
        )
    }

    @Test fun `six zero shows the empty slots even when nobody answered`() {
        assertEquals(
            "$header\nVagas: 0/2\n\n✅ Confirmados:\n1.\n2.",
            reminderBody(game(2), roster(GameSystem.SIX_ZERO, emptyMap())),
        )
    }

    @Test fun `the waitlist has no numbers when the printed order is not the promotion order`() {
        val roster = roster(
            GameSystem.SIX_ZERO, emptyMap(), waitlisted = listOf(RosterEntry("Caio"), RosterEntry("Duda")), numberedWaitlist = false,
        )
        assertEquals(
            "$header\nVagas: 0/1\n\n✅ Confirmados:\n1.\n\n🕒 Lista de espera:\nCaio\nDuda",
            reminderBody(game(1), roster),
        )
    }

    @Test fun `five one splits every list by position with slots numbered inside the position`() {
        val roster = roster(
            GameSystem.FIVE_ONE,
            mapOf(LIBERO to 0, LEVANTADOR to 1, PONTA to 2, CENTRAL to 2, OPOSTO to 1),
            confirmed = listOf(RosterEntry("Ana", PONTA), RosterEntry("Bia", LEVANTADOR), RosterEntry("Cris", PONTA)),
            waitlisted = listOf(RosterEntry("Duda", PONTA), RosterEntry("Edu", PONTA), RosterEntry("Fabi", CENTRAL)),
            declined = listOf(RosterEntry("Gil", OPOSTO), RosterEntry("Hugo", null)),
            numberedWaitlist = true,
        )
        assertEquals(
            "$header\nVagas: 3/6\n\n" +
                "✅ Confirmados:\nLevantadores(as):\n1. Bia\n\nPonteiros(as):\n1. Ana\n2. Cris\n\nCentrais:\n1.\n2.\n\nOpostos(as):\n1.\n\n" +
                "🕒 Lista de espera:\nPonteiros(as):\n1. Duda\n2. Edu\n\nCentrais:\n1. Fabi\n\n" +
                "❌ Fora:\nOpostos(as):\nGil\n\nSem posição:\nHugo",
            reminderBody(game(6), roster),
        )
    }

    @Test fun `five one waitlist by position has no numbers with manual promotion or monthly priority`() {
        val roster = roster(
            GameSystem.FIVE_ONE, mapOf(PONTA to 1),
            waitlisted = listOf(RosterEntry("Duda", PONTA), RosterEntry("Edu", PONTA)),
            numberedWaitlist = false,
        )
        assertEquals(
            "$header\nVagas: 0/1\n\n✅ Confirmados:\nPonteiros(as):\n1.\n\n🕒 Lista de espera:\nPonteiros(as):\nDuda\nEdu",
            reminderBody(game(1), roster),
        )
    }

    @Test fun `a legacy game keeps the current body byte for byte`() {
        val legacy = ReminderRoster(listOf("Ana", "Bia"), listOf("Caio"), listOf("Dora"))
        assertEquals(
            "$header\nVagas: 2/4\n\n✅ Confirmados:\nAna\nBia\n\n🕒 Lista de espera:\nCaio\n\n❌ Fora:\nDora",
            reminderBody(game(4), legacy),
        )
    }

    @Test fun `the digest changes with the bucket, the position, a new name and the order, and ignores the deadline`() {
        val base = roster(GameSystem.FIVE_ONE, mapOf(PONTA to 2), confirmed = listOf(RosterEntry("Ana", PONTA)), declined = listOf(RosterEntry("Bia", PONTA)))
        val same = rosterDigest(base)
        assertEquals(same, rosterDigest(base.copy()))
        val layout = base.layout!!
        val moved = base.copy(layout = layout.copy(confirmed = emptyList(), declined = layout.declined + RosterEntry("Ana", PONTA)))
        val back = base.copy(layout = layout.copy(confirmed = layout.confirmed + RosterEntry("Bia", PONTA), declined = emptyList()))
        val otherPosition = base.copy(layout = layout.copy(confirmed = listOf(RosterEntry("Ana", CENTRAL))))
        val newName = base.copy(layout = layout.copy(confirmed = layout.confirmed + RosterEntry("Caio", PONTA)))
        listOf(moved, back, otherPosition, newName).forEach { assertNotEquals(same, rosterDigest(it)) }
        assertNotEquals(rosterDigest(ReminderRoster(listOf("A"), emptyList(), emptyList())), rosterDigest(ReminderRoster(emptyList(), emptyList(), listOf("A"))))
    }

    private fun roster(
        system: GameSystem,
        slots: Map<br.com.saqz.groups.domain.AthletePosition, Int>,
        confirmed: List<RosterEntry> = emptyList(),
        waitlisted: List<RosterEntry> = emptyList(),
        declined: List<RosterEntry> = emptyList(),
        numberedWaitlist: Boolean = false,
    ) = ReminderRoster(
        confirmed.map { it.name }, waitlisted.map { it.name }, declined.map { it.name },
        RosterLayout(system, slots, confirmed, waitlisted, declined, numberedWaitlist),
    )

    private fun game(capacity: Int) = ReminderGame(LocalDate.of(2026, 9, 20), LocalTime.of(19, 30), "Arena Central", capacity = capacity)
}
