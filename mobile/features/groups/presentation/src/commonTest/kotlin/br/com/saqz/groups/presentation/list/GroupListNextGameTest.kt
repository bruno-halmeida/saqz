package br.com.saqz.groups.presentation.list

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.sampleGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** GER-003 · qual jogo a linha do grupo mostra e o que a foto diz da sua resposta. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupListNextGameTest {
    // Sábado, 01/08, 09h em São Paulo.
    private val now = Instant.parse("2026-08-01T12:00:00Z")
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    // Começou 10 min antes de "now" e dura 2 h.
    private fun running() = sampleGame().copy(id = "running", startsAt = "2026-08-01T11:50:00Z", durationMinutes = 120)

    @Test fun `nearest published game not started yet is shown with the seal in the group time zone`() = runTest {
        val next = sampleGame().copy(id = "next", startsAt = "2026-08-04T22:30:00Z")
        val game = listOf(next.copy(id = "later", startsAt = "2026-08-10T22:30:00Z"), next).nextGroupCardGame(now)!!
        assertEquals("next", game.gameId)
        assertEquals("TER", game.weekday)
        assertEquals("04", game.day)
        assertEquals("19h30", game.time)
        assertFalse(game.live)
    }

    @Test fun `a game that started gives way to the next one`() = runTest {
        val later = sampleGame().copy(id = "later", startsAt = "2026-08-04T22:30:00Z")
        assertEquals("later", listOf(running(), later).nextGroupCardGame(now)?.gameId)
    }

    @Test fun `game in progress stays only when the group has no other game and reads as now`() = runTest {
        val game = listOf(running()).nextGroupCardGame(now)!!
        assertEquals("running", game.gameId)
        assertTrue(game.live)
        // Passou da duração: some.
        assertNull(listOf(running().copy(durationMinutes = 5)).nextGroupCardGame(now))
    }

    @Test fun `draft never shows - not even to owner or admin`() = runTest {
        val draft = sampleGame().copy(id = "draft", status = GameStatus.Draft, startsAt = "2026-08-02T22:30:00Z")
        assertNull(listOf(draft).nextGroupCardGame(now))
        val published = sampleGame().copy(id = "published")
        assertEquals("published", listOf(draft, published).nextGroupCardGame(now)?.gameId)
    }

    @Test fun `cancelled completed and past games are excluded`() = runTest {
        val games = listOf(
            sampleGame().copy(status = GameStatus.Cancelled),
            sampleGame().copy(status = GameStatus.Completed),
            sampleGame().copy(startsAt = "2026-07-01T22:30:00Z"),
        )
        assertNull(games.nextGroupCardGame(now))
    }

    @Test fun `own answer becomes the photo tag`() = runTest {
        suspend fun attendance(own: AttendanceStatus?) =
            listOf(sampleGame().copy(ownAttendance = own)).nextGroupCardGame(now)?.attendance
        assertEquals(GroupCardAttendance.Going, attendance(AttendanceStatus.Confirmed))
        assertEquals(GroupCardAttendance.Waitlisted, attendance(AttendanceStatus.Waitlisted))
        assertEquals(GroupCardAttendance.Out, attendance(AttendanceStatus.Declined))
        assertEquals(GroupCardAttendance.Pending, attendance(null))
    }

    @Test fun `confirm stays until the start - also after the deadline`() = runTest {
        val late = listOf(sampleGame().copy(confirmationDeadline = "2026-07-31T12:00:00Z")).nextGroupCardGame(now)!!
        assertEquals(GroupCardAttendance.Pending, late.attendance)
        assertTrue(late.needsConfirmation)
    }

    @Test fun `game in progress without answer shows not going and asks nothing`() = runTest {
        val game = listOf(running()).nextGroupCardGame(now)!!
        assertEquals(GroupCardAttendance.Out, game.attendance)
        assertFalse(game.needsConfirmation)
        val going = listOf(running().copy(ownAttendance = AttendanceStatus.Confirmed)).nextGroupCardGame(now)!!
        assertEquals(GroupCardAttendance.Going, going.attendance)
    }

    @Test fun `place is the venue with the court - or only the venue`() {
        val game = sampleGame().copy(venue = GameVenue(name = "CERET", address = "Rua", court = "Quadra 2"))
        assertEquals("CERET — Quadra 2", game.placeLabel())
        assertEquals("CERET", game.copy(venue = game.venue.copy(court = null)).placeLabel())
        assertEquals("CERET", game.copy(venue = game.venue.copy(court = " ")).placeLabel())
        assertNull(game.copy(venue = game.venue.copy(name = "")).placeLabel())
    }

    @Test fun `spots open - full and above capacity`() = runTest {
        suspend fun spots(confirmed: Int, capacity: Int) =
            listOf(sampleGame().copy(confirmedCount = confirmed, capacity = capacity)).nextGroupCardGame(now)!!
        val open = spots(14, 18)
        assertEquals(4, open.openSpots)
        assertNull(open.overCapacityLabel)
        val full = spots(12, 12)
        assertEquals(0, full.openSpots)
        assertNull(full.overCapacityLabel)
        // Acima da lotação: nunca "13 de 12".
        val over = spots(13, 12)
        assertEquals("13 confirmados para 12 vagas", over.overCapacityLabel)
        assertEquals(0, over.openSpots)
    }

    @Test fun `place falls back to the group city without a game`() = runTest {
        val vm = viewModel(FakeGameGateway())
        assertEquals("São Paulo", vm.state.value.groups.single().place)
        assertNull(vm.state.value.groups.single().nextGame)
    }

    @Test fun `place is the venue of the shown game`() = runTest {
        val game = sampleGame().copy(venue = GameVenue(name = "CERET", address = "Rua", court = "Quadra 2"))
        val vm = viewModel(FakeGameGateway(SaqzResult.Success(listOf(game))))
        assertEquals("CERET — Quadra 2", vm.state.value.groups.single().place)
    }

    @Test fun `refresh after answering drops the awaiting count and shows the answer`() = runTest {
        val games = FakeGameGateway(SaqzResult.Success(listOf(sampleGame())))
        val vm = viewModel(games)
        assertEquals(1, vm.state.value.awaitingConfirmation)
        games.listResult = SaqzResult.Success(listOf(sampleGame().copy(ownAttendance = AttendanceStatus.Confirmed)))
        vm.onIntent(GroupListIntent.Appeared) // primeira aparição: a carga do init já cobre
        vm.onIntent(GroupListIntent.Appeared) // volta à aba depois de responder
        assertEquals(0, vm.state.value.awaitingConfirmation)
        assertEquals(GroupCardAttendance.Going, vm.state.value.groups.single().nextGame?.attendance)
    }

    @Test fun `refresh shows newly created game and removes cancelled game`() = runTest {
        val games = FakeGameGateway()
        val vm = viewModel(games)
        assertNull(vm.state.value.groups.single().nextGame)
        games.listResult = SaqzResult.Success(listOf(sampleGame()))
        vm.onIntent(GroupListIntent.Refresh)
        assertNotNull(vm.state.value.groups.single().nextGame)
        games.listResult = SaqzResult.Success(listOf(sampleGame().copy(status = GameStatus.Cancelled)))
        vm.onIntent(GroupListIntent.Refresh)
        assertNull(vm.state.value.groups.single().nextGame)
    }

    @Test fun `failed games request does not claim no upcoming game`() = runTest {
        val vm = viewModel(FakeGameGateway(SaqzResult.Failure(GameError.Data(DataError.Server))))
        assertTrue(vm.state.value.loadFailed)
    }

    private fun viewModel(games: FakeGameGateway) = GroupListViewModel(
        FakeAthleteGateway(ownProfileResult = SaqzResult.Success(OwnAthleteProfile(
            "me", "Bruno", null, listOf(OwnAthleteMembership(
                GroupId("group-1"), "Grupo", GroupRole.ADMIN, null, AthleteMembershipType.MENSALISTA, true,
            )),
        ))),
        FakeGroupGateway(), GroupCreationEntitlement { false }, games, FakeAttendanceGateway(), now = { now },
    )
}
