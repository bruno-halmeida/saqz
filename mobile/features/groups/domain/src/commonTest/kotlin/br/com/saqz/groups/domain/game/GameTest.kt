package br.com.saqz.groups.domain.game

import br.com.saqz.domain.DataError
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.domain.GroupId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {
    @Test fun `game statuses are complete`() = assertEquals(4, GameStatus.entries.size)
    @Test fun `draft status is represented`() = assertEquals(GameStatus.Draft, GameStatus.entries[0])
    @Test fun `published status is represented`() = assertEquals(GameStatus.Published, GameStatus.entries[1])
    @Test fun `cancelled status is represented`() = assertEquals(GameStatus.Cancelled, GameStatus.entries[2])
    @Test fun `completed status is represented`() = assertEquals(GameStatus.Completed, GameStatus.entries[3])
    @Test fun `boundary scopes are complete`() = assertEquals(2, SeriesBoundaryScope.entries.size)
    @Test fun `boundary actions are complete`() = assertEquals(2, SeriesBoundaryAction.entries.size)
    @Test fun `weekdays are complete`() = assertEquals(7, Weekday.entries.size)
    @Test fun `lifecycle actions are complete`() = assertEquals(3, GameLifecycleAction.entries.size)
    @Test fun `venue accepts null venue id`() = assertNull(venue().venueId)
    @Test fun `venue retains court`() = assertEquals("Quadra 2", venue().court)
    @Test fun `game retains aggregate counts`() = assertEquals(listOf(3, 21, 2), listOf(game().confirmedCount, game().availableSpots, game().waitlistCount))
    @Test fun `game retains terminal status`() = assertTrue(game().copy(status = GameStatus.Completed).status == GameStatus.Completed)
    @Test fun `game retains nullable fee`() = assertNull(game().copy(gameFeeCents = null).gameFeeCents)
    @Test fun `game retains nullable notes`() = assertNull(game().copy(notes = null).notes)
    @Test fun `game retains finance review flag`() = assertTrue(game().copy(financeReviewRequired = true).financeReviewRequired)
    @Test fun `version token preserves quoted etag`() = assertEquals("\"7\"", GameVersionToken("\"7\"").value)
    @Test fun `versioned game retains game`() = assertEquals("game-1", VersionedGame(game(), GameVersionToken("v")).game.id)
    @Test fun `weekly slot retains weekday`() = assertEquals(Weekday.Wednesday, slot().weekday)
    @Test fun `weekly slot retains nullable fee`() = assertNull(slot().copy(gameFeeCents = null).gameFeeCents)
    @Test fun `series retains recurrence slots`() = assertEquals(1, series().slots.size)
    @Test fun `series retains occurrences`() = assertEquals("2026-08-12T22:30:00Z", series().occurrences.single().startsAt)
    @Test fun `series end date is optional`() = assertNull(series().localEndDate)
    @Test fun `game command retains default fee choice`() = assertFalse(command().useDefaultGameFee)
    @Test fun `series command retains local start date`() = assertEquals("2026-08-12", seriesCommand().localStartDate)
    @Test fun `only this boundary retains game id`() = assertEquals("game-1", boundary().gameId)
    @Test fun `future boundary retains successor`() = assertEquals("revision-1", boundary().copy(scope = SeriesBoundaryScope.ThisAndFuture, successor = seriesCommand()).successor?.revisionId)
    @Test fun `validation error retains safe details`() = assertEquals(listOf("invalid"), assertIs<GameError.Validation>(GameError.Validation(DataError.Validation(ValidationDetails(listOf("invalid"), emptyMap())))).error.details.globalMessages)
    @Test fun `all permanent errors are distinct`() = assertEquals(4, setOf(GameError.HiddenResource, GameError.Conflict, GameError.InvalidLifecycle, GameError.Authentication).size)
    @Test fun `shared data error is retained`() = assertEquals(DataError.Connectivity, assertIs<GameError.Data>(GameError.Data(DataError.Connectivity)).error)

    private fun venue() = GameVenue(null, "Arena", "Rua", "Quadra 2")
    private fun game() = Game("game-1", GroupId("group-1"), "Treino", venue(), "2026-08-12", "19:30:00", "America/Sao_Paulo", "2026-08-12T22:30:00Z", 90, 24, "2026-08-12T19:30:00Z", 2500, "Levar bola", GameStatus.Draft, 1, 3, 21, 2)
    private fun slot() = WeeklySlot("slot-1", Weekday.Wednesday, "19:30:00", 90, venue(), 24, 180, 2500, "Treino")
    private fun series() = WeeklySeries("series-1", "revision-1", 1, "America/Sao_Paulo", "2026-08-12", slots = listOf(slot()), occurrences = listOf(SeriesOccurrence("game-1", "2026-08-12", "19:30:00", "2026-08-12T22:30:00Z", GameStatus.Draft, 1)), version = 1)
    private fun command() = GameWriteCommand("key", "Treino", venue(), "2026-08-12", "19:30:00", "America/Sao_Paulo", "2026-08-12T22:30:00Z", 90, 24, "2026-08-12T19:30:00Z", 2500, false, "Levar bola")
    private fun seriesCommand() = WeeklySeriesWriteCommand("key", "revision-1", "America/Sao_Paulo", "2026-08-12", slots = listOf(slot()))
    private fun boundary() = SeriesBoundaryCommand("key", SeriesBoundaryScope.OnlyThis, SeriesBoundaryAction.Cancel, gameId = "game-1")
}
