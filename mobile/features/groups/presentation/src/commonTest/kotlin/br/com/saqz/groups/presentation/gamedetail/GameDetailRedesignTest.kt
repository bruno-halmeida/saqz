package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.PositionSummary
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeCommunicationGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.sampleCommunicationMessage
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGame
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-116: o que a tela do jogo redesenhada lê do estado (quem não vai, cabeçalho, linha verde-limão, Avisar, mapa). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailRedesignTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** Terça, 01/01/2030, 20h, 2h de jogo: por começar e com prazo aberto. */
    private fun openGame(address: String = "R. Canuto Abreu, s/n"): Game = sampleGame().copy(
        venue = GameVenue(name = "CERET", address = address, court = "Quadra 2"),
        confirmationDeadline = "2030-01-01T12:00:00-03:00",
        startsAt = "2030-01-01T20:00:00-03:00",
    )

    private fun viewModel(
        roster: AttendanceRoster = AttendanceRoster(emptyList(), emptyList()),
        detail: AttendanceDetail = AttendanceDetail(null, 8, 4, 0, 12, declinedCount = 2, pendingCount = 3),
        role: GroupRole = GroupRole.ADMIN,
        game: Game = openGame(),
        communications: FakeCommunicationGateway? = FakeCommunicationGateway(),
    ) = GameDetailViewModel(
        "group-1",
        "game-1",
        FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(game))),
        FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = role)))),
        FakeAttendanceGateway(readResult = SaqzResult.Success(detail), rosterResult = SaqzResult.Success(roster)),
        FakeAthleteGateway(),
        communications = communications,
    )

    // ---- quem não vai

    @Test
    fun `declined from a new server becomes the list and its size is the tab number`() = runTest {
        val roster = AttendanceRoster(
            emptyList(),
            emptyList(),
            declined = listOf(AttendanceRosterMember("ana", "Ana Reis"), AttendanceRosterMember("me", "Bruno")),
        )
        val state = viewModel(roster).state.value

        assertEquals(listOf("Ana Reis", "Bruno"), state.declinedRoster?.map { it.name })
        assertTrue(state.declinedRoster!!.last().isYou)
        assertEquals(2, state.declinedCount)
    }

    @Test
    fun `an old server without declined keeps the list null and the tab uses the count`() = runTest {
        val state = viewModel(detail = AttendanceDetail(null, 8, 4, 0, 12, declinedCount = 5)).state.value

        assertNull(state.declinedRoster)
        assertEquals(5, state.declinedCount)
    }

    // ---- cabeçalho

    @Test
    fun `the top bar title is the group name and the header carries the day end time address and menu title`() = runTest {
        val state = viewModel().state.value

        assertEquals("Vôlei do CERET", state.groupName)
        assertEquals("Terça, 20h00", state.hero?.title)
        assertEquals("1 de janeiro", state.hero?.date)
        assertEquals("22h00", state.hero?.endTime)
        assertEquals("R. Canuto Abreu, s/n", state.hero?.address)
        assertEquals("Jogo de terça, 01/01", state.hero?.menuTitle)
    }

    @Test
    fun `the tab opens on the waitlist for who is waiting and on going for everyone else`() = runTest {
        val waiting = viewModel(
            detail = AttendanceDetail(AttendanceEntry("me", AttendanceStatus.Waitlisted, 1, 1), 12, 0, 1, 12),
            roster = AttendanceRoster(emptyList(), listOf(AttendanceRosterMember("me", "Bruno", 1))),
        ).state.value
        assertEquals(GameDetailTab.Waiting, waiting.initialTab)
        assertTrue(waiting.waitlist.single().isYou)

        assertEquals(GameDetailTab.Going, viewModel().state.value.initialTab)
    }

    // ---- linha verde-limão

    @Test
    fun `without positions who has not answered sees the spots left from one to three`() = runTest {
        val few = viewModel(detail = AttendanceDetail(null, 9, 3, 0, 12)).state.value
        assertEquals(GameLimeLine.SpotsLeft(3), few.limeLine)

        assertNull(viewModel(detail = AttendanceDetail(null, 8, 4, 0, 12)).state.value.limeLine)
        val answered = viewModel(detail = AttendanceDetail(AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1), 9, 3, 0, 12))
        assertNull(answered.state.value.limeLine)
    }

    @Test
    fun `with positions the athlete without answer sees which positions have room`() = runTest {
        val state = viewModel(detail = positionsDetail(), role = GroupRole.ATHLETE).state.value

        assertEquals(GameLimeLine.OpenPositions(listOf(AthletePosition.CENTRAL, AthletePosition.OPOSTO)), state.limeLine)
    }

    @Test
    fun `with positions the organizer sees what is missing per position`() = runTest {
        val state = viewModel(detail = positionsDetail(own = AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1))).state.value

        assertEquals(
            GameLimeLine.Missing(listOf(AthletePosition.CENTRAL to 1, AthletePosition.OPOSTO to 2)),
            state.limeLine,
        )
    }

    @Test
    fun `the lime line goes away after the game starts`() = runTest {
        val started = viewModel(
            detail = AttendanceDetail(null, 9, 3, 0, 12),
            game = openGame().copy(startsAt = "2020-01-01T20:00:00-03:00", confirmationDeadline = "2020-01-01T12:00:00-03:00"),
        )
        assertNull(started.state.value.limeLine)
    }

    // ---- Avisar

    @Test
    fun `notify sends the reminder and shows how many were told`() = runTest {
        val communications = FakeCommunicationGateway().apply {
            reminderResult = SaqzResult.Success(sampleCommunicationMessage().copy(recipientCount = 3))
        }
        val viewModel = viewModel(communications = communications)
        assertTrue(viewModel.state.value.canNotify)

        viewModel.onIntent(GameDetailIntent.NotifyPending)

        assertEquals("game-1", communications.reminders.single().second)
        assertEquals(3, viewModel.state.value.reminder.notifiedCount)
        // O painel fica para dizer o retorno, mesmo que ninguém mais esteja pendente.
        assertTrue(viewModel.state.value.canNotify)
    }

    @Test
    fun `a failed notify reuses the request id on retry and a success then drops it`() = runTest {
        val communications = FakeCommunicationGateway().apply {
            reminderResult = SaqzResult.Failure(CommunicationError(DataError.Connectivity))
        }
        val viewModel = viewModel(communications = communications)

        viewModel.onIntent(GameDetailIntent.NotifyPending)
        assertTrue(viewModel.state.value.reminder.failed)
        viewModel.onIntent(GameDetailIntent.NotifyPending)

        val ids = communications.reminders.map { it.third }
        assertEquals(2, ids.size)
        assertEquals(ids[0], ids[1])
    }

    @Test
    fun `a refused notify shows the refusal and not a generic failure`() = runTest {
        val communications = FakeCommunicationGateway().apply {
            reminderResult = SaqzResult.Failure(CommunicationError(DataError.Validation(br.com.saqz.domain.ValidationDetails(emptyList(), emptyMap()))))
        }
        val viewModel = viewModel(communications = communications)

        viewModel.onIntent(GameDetailIntent.NotifyPending)

        assertTrue(viewModel.state.value.reminder.refused)
        assertFalse(viewModel.state.value.reminder.failed)
    }

    @Test
    fun `a double tap sends a single reminder`() = runTest {
        val gate = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.communication.CommunicationMessage, CommunicationError>>()
        val communications = FakeCommunicationGateway().apply { remindBlock = { gate.await() } }
        val viewModel = viewModel(communications = communications)

        viewModel.onIntent(GameDetailIntent.NotifyPending)
        viewModel.onIntent(GameDetailIntent.NotifyPending)
        assertTrue(viewModel.state.value.reminder.sending)
        gate.complete(SaqzResult.Success(sampleCommunicationMessage().copy(recipientCount = 3)))

        assertEquals(1, communications.reminders.size)
    }

    @Test
    fun `athletes cannot notify`() = runTest {
        val communications = FakeCommunicationGateway()
        val viewModel = viewModel(role = GroupRole.ATHLETE, communications = communications)

        assertFalse(viewModel.state.value.canNotify)
        assertFalse(viewModel.state.value.hasGameMenu)
        viewModel.onIntent(GameDetailIntent.NotifyPending)
        viewModel.onIntent(GameDetailIntent.OpenGameMenu)

        assertTrue(communications.reminders.isEmpty())
        assertFalse(viewModel.state.value.gameMenuOpen)
    }

    // ---- mapa e menus

    @Test
    fun `open map emits the game address`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GameDetailIntent.OpenVenueMap)

        assertEquals(GameDetailEffect.OpenMap("R. Canuto Abreu, s/n"), viewModel.effects.first())
        assertFalse(viewModel.state.value.mapFailed)
    }

    @Test
    fun `open map without an address fails on the venue line`() = runTest {
        val viewModel = viewModel(game = openGame(address = " "))

        viewModel.onIntent(GameDetailIntent.OpenVenueMap)

        assertTrue(viewModel.state.value.mapFailed)
        assertEquals("", viewModel.state.value.hero?.address)
    }

    @Test
    fun `menu items close the menu as they open their own sheet`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GameDetailIntent.OpenGameMenu)
        assertTrue(viewModel.state.value.gameMenuOpen)
        viewModel.onIntent(GameDetailIntent.RequestCancel)

        assertFalse(viewModel.state.value.gameMenuOpen)
        assertTrue(viewModel.state.value.cancelDialogOpen)
    }

    @Test
    fun `person actions open only on a row the organizer can act on`() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("me", "Bruno"), AttendanceRosterMember("ana", "Ana")),
            waitlisted = emptyList(),
        )
        val viewModel = viewModel(roster = roster, detail = AttendanceDetail(AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1), 2, 10, 0, 12))

        viewModel.onIntent(GameDetailIntent.OpenPersonActions("me"))
        assertNull(viewModel.state.value.personActionsId)
        viewModel.onIntent(GameDetailIntent.OpenPersonActions("ana"))
        assertEquals("ana", viewModel.state.value.personActionsId)
        viewModel.onIntent(GameDetailIntent.RequestRemoveMember("ana"))

        assertNull(viewModel.state.value.personActionsId)
        assertEquals("ana", viewModel.state.value.organizer.removal?.memberId)
    }

    // ---- espera por posição

    @Test
    fun `waitlist rows carry the game position so the screen splits them by section`() = runTest {
        val roster = AttendanceRoster(
            confirmed = emptyList(),
            waitlisted = listOf(
                AttendanceRosterMember("caio", "Caio", 1, position = AthletePosition.CENTRAL),
                AttendanceRosterMember("duda", "Duda", 1, position = AthletePosition.PONTA),
                AttendanceRosterMember("edu", "Edu", 2, position = AthletePosition.PONTA),
            ),
        )
        val state = viewModel(roster = roster, detail = positionsDetail()).state.value

        val byPosition = state.waitlist.groupBy { it.athletePosition }
        assertEquals(listOf("Duda", "Edu"), byPosition[AthletePosition.PONTA]?.map { it.name })
        assertEquals(listOf(1L, 2L), byPosition[AthletePosition.PONTA]?.map { it.queuePosition })
        assertEquals(listOf("Caio"), byPosition[AthletePosition.CENTRAL]?.map { it.name })
    }

    /** Ponta cheia (2/2), central 1/2, oposto 0/2, levantador cheio. */
    private fun positionsDetail(own: AttendanceEntry? = null) = AttendanceDetail(
        ownAttendance = own,
        confirmedCount = 4,
        availableSpots = 3,
        waitlistCount = 0,
        capacity = 7,
        positions = listOf(
            PositionSummary(AthletePosition.PONTA, 2, 2, 0, 0),
            PositionSummary(AthletePosition.CENTRAL, 2, 1, 1, 0),
            PositionSummary(AthletePosition.OPOSTO, 2, 0, 2, 0),
            PositionSummary(AthletePosition.LEVANTADOR, 1, 1, 0, 0),
        ),
    )
}
