package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.presentation.sampleGame
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class GroupAgendaTest {
    private val now = Instant.parse("2026-08-01T00:00:00Z")
    private val weekdays = mapOf(
        GroupWeekday.MONDAY to "Segunda",
        GroupWeekday.TUESDAY to "Terça",
        GroupWeekday.WEDNESDAY to "Quarta",
        GroupWeekday.THURSDAY to "Quinta",
        GroupWeekday.FRIDAY to "Sexta",
    )

    @Test
    fun `agenda keeps upcoming published and draft games without the hero sorted by start`() = runTest {
        val games = listOf(
            game("late", "2026-08-13T19:30:00-03:00"),
            game("hero", "2026-08-04T19:30:00-03:00"),
            game("draft", "2026-08-11T19:30:00-03:00").copy(status = GameStatus.Draft),
            game("next", "2026-08-06T19:30:00-03:00"),
            game("past", "2026-07-28T19:30:00-03:00"),
            game("cancelled", "2026-08-07T19:30:00-03:00").copy(status = GameStatus.Cancelled),
            game("completed", "2026-08-08T19:30:00-03:00").copy(status = GameStatus.Completed),
            game("broken", "sem data"),
        )

        assertEquals(listOf("next", "draft", "late"), groupAgenda(games, "hero", now, "CERET").map { it.gameId })
    }

    @Test
    fun `row is written in the game time zone with the home keys`() = runTest {
        // 01h30 UTC de sexta ainda é quinta, 22h30, em São Paulo.
        val game = game("game-2", "2026-08-07T01:30:00Z").copy(ownAttendance = AttendanceStatus.Confirmed)

        assertEquals(
            GroupAgendaRowUi(
                gameId = "game-2",
                day = "6",
                month = "AGO",
                title = "Quinta · 22h30",
                meta = "8 de 12 confirmados",
                status = GroupAgendaStatus.Going,
                statusLabel = "Você vai",
                contentDescription = "Quinta, 06/08 às 22h30, Você vai",
            ),
            groupAgenda(listOf(game), null, now, "CERET").single(),
        )
    }

    @Test
    fun `meta prefers draft then full then another venue then the plain count`() = runTest {
        val base = game("g", "2026-08-06T19:30:00-03:00")
        val elsewhere = base.copy(venue = GameVenue(name = "Arena Mooca", address = "Av. Paes de Barros, 1000"))
        val full = elsewhere.copy(confirmedCount = 12, availableSpots = 0)

        assertEquals("Só você vê até publicar", meta(full.copy(status = GameStatus.Draft)))
        assertEquals("Lotado · 12 de 12", meta(full))
        assertEquals("8 de 12 · Arena Mooca", meta(elsewhere))
        assertEquals("8 de 12 confirmados", meta(base))
    }

    @Test
    fun `group without a default venue always names the game venue`() = runTest {
        val row = groupAgenda(listOf(game("g", "2026-08-06T19:30:00-03:00")), null, now, defaultVenueName = null).single()

        assertEquals("8 de 12 · CERET", row.meta)
    }

    @Test
    fun `status follows the own attendance and draft wins`() = runTest {
        val base = game("g", "2026-08-06T19:30:00-03:00")

        assertEquals(GroupAgendaStatus.Pending to "Sem resposta", status(base))
        assertEquals(GroupAgendaStatus.Going to "Você vai", status(base.copy(ownAttendance = AttendanceStatus.Confirmed)))
        assertEquals(GroupAgendaStatus.Out to "Não vai", status(base.copy(ownAttendance = AttendanceStatus.Declined)))
        assertEquals(GroupAgendaStatus.Waitlisted to "Na espera", status(base.copy(ownAttendance = AttendanceStatus.Waitlisted)))
        assertEquals(
            GroupAgendaStatus.Draft to "Rascunho",
            status(base.copy(status = GameStatus.Draft, ownAttendance = AttendanceStatus.Confirmed)),
        )
    }

    @Test
    fun `agenda stops at twelve rows`() = runTest {
        val games = (1..14).map { game("g$it", "2026-08-${(it + 1).toString().padStart(2, '0')}T19:30:00-03:00") }

        assertEquals((1..12).map { "g$it" }, groupAgenda(games, null, now, "CERET").map { it.gameId })
    }

    @Test
    fun `schedule summary joins distinct days in week order and appends a shared time`() {
        assertEquals("Terça · 19h30", summary(slot(GroupWeekday.TUESDAY)))
        assertEquals(
            "Terça e Quinta · 19h30",
            summary(slot(GroupWeekday.THURSDAY), slot(GroupWeekday.TUESDAY), slot(GroupWeekday.TUESDAY)),
        )
        assertEquals(
            "Segunda, Quarta e Sexta · 19h30",
            summary(slot(GroupWeekday.FRIDAY), slot(GroupWeekday.MONDAY), slot(GroupWeekday.WEDNESDAY)),
        )
    }

    @Test
    fun `schedule summary drops the time when slots disagree and is null without slots`() {
        assertEquals("Terça e Quinta", summary(slot(GroupWeekday.TUESDAY), slot(GroupWeekday.THURSDAY, "20:00")))
        assertNull(summary())
    }

    private suspend fun meta(game: Game) = groupAgenda(listOf(game), null, now, "CERET").single().meta

    private suspend fun status(game: Game) =
        groupAgenda(listOf(game), null, now, "CERET").single().let { it.status to it.statusLabel }

    private fun summary(vararg slots: GroupRegularSlot) = groupScheduleSummary(slots.toList()) { weekdays.getValue(it) }

    private fun slot(weekday: GroupWeekday, startTime: String = "19:30") =
        GroupRegularSlot(weekday = weekday, startTime = startTime, durationMinutes = 120)

    // `sampleGame()`: CERET, 8 de 12, 4 vagas, America/Sao_Paulo, publicado.
    private fun game(id: String, startsAt: String) = sampleGame().copy(id = id, startsAt = startsAt)
}
