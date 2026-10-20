package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceMutation
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.PositionSummary
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleRosterEntry
import br.com.saqz.groups.presentation.sampleVersionedGame
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-102 (b) a (e): seletor do "Vou", lista agrupada, convidado e entrada forçada no detalhe do jogo. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailPositionsTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun summary(position: AthletePosition, slots: Int, confirmed: Int, waitlisted: Int = 0) =
        PositionSummary(position, slots, confirmed, (slots - confirmed).coerceAtLeast(0), waitlisted)

    // PONTA cheia (2/2, 1 na espera), CENTRAL com 1 vaga, LEVANTADOR com 1 vaga livre.
    private val positions = listOf(
        summary(AthletePosition.PONTA, 2, 2, waitlisted = 1),
        summary(AthletePosition.CENTRAL, 2, 1),
        summary(AthletePosition.LEVANTADOR, 1, 0),
    )

    private fun detail(own: AttendanceEntry? = null, withPositions: Boolean = true) = AttendanceDetail(
        ownAttendance = own, confirmedCount = 3, availableSpots = 2, waitlistCount = 1, capacity = 5,
        positions = if (withPositions) positions else emptyList(),
    )

    private fun roster() = AttendanceRoster(
        confirmed = listOf(
            AttendanceRosterMember("p1", "Ana", position = AthletePosition.PONTA),
            AttendanceRosterMember("p2", "Bia", position = AthletePosition.PONTA, forced = true),
            AttendanceRosterMember("c1", "Caio", position = AthletePosition.CENTRAL),
        ),
        waitlisted = listOf(AttendanceRosterMember("w1", "Duda", waitlistPosition = 1, position = AthletePosition.PONTA)),
        positions = positions,
    )

    private fun mutation(status: AttendanceStatus, position: AthletePosition?) = SaqzResult.Success(
        VersionedAttendanceMutation(
            AttendanceMutation(
                attendance = AttendanceEntry("me", status, null, 1, position = position),
                promotedCount = 0,
                detail = detail(),
            ),
            AttendanceVersionToken("\"2\""),
        ),
    )

    private fun viewModel(
        attendance: FakeAttendanceGateway,
        role: GroupRole = GroupRole.ADMIN,
        athletes: FakeAthleteGateway = FakeAthleteGateway(),
    ) = GameDetailViewModel(
        "group-1", "game-1",
        FakeGameGateway(
            readResult = SaqzResult.Success(
                sampleVersionedGame(sampleGame().copy(confirmationDeadline = "2030-01-01T12:00:00-03:00", startsAt = "2030-01-01T20:00:00-03:00")),
            ),
        ),
        FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = role)))),
        attendance, athletes,
    )

    private fun gateway(withPositions: Boolean = true, own: AttendanceEntry? = null) = FakeAttendanceGateway(
        readResult = SaqzResult.Success(detail(own, withPositions)),
        rosterResult = SaqzResult.Success(if (withPositions) roster() else AttendanceRoster(emptyList(), emptyList())),
        respondResult = mutation(AttendanceStatus.Confirmed, AthletePosition.LEVANTADOR),
    )

    @Test
    fun `positions come from the attendance detail and the roster rows carry the game position`() = runTest {
        val state = viewModel(gateway()).state.value
        assertTrue(state.hasPositions)
        assertEquals(3, state.positions.size)
        assertTrue(state.positions.first { it.position == AthletePosition.PONTA }.full)
        assertEquals(AthletePosition.PONTA, state.confirmedRoster.first { it.id == "p1" }.position)
        assertTrue(state.confirmedRoster.first { it.id == "p2" }.forced)
        assertEquals(AthletePosition.PONTA, state.waitlist.single().athletePosition)
        // O total do jogo segue sendo o do detalhe (soma das posições).
        assertEquals(5, state.attendance?.capacity)
    }

    @Test
    fun `the most free position is the one the server would pick`() = runTest {
        // CENTRAL e LEVANTADOR têm 1 vaga livre; empate pela ordem do enum (CENTRAL antes de LEVANTADOR).
        assertEquals(AthletePosition.CENTRAL, viewModel(gateway()).state.value.mostFreePosition)
    }

    @Test
    fun `going in a game with positions opens the position screen and sends nothing`() = runTest {
        val attendance = gateway()
        val viewModel = viewModel(attendance)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(GameDetailEffect.PickPosition, viewModel.effects.first())
        assertNull(viewModel.state.value.positionPicker)
        assertEquals(0, attendance.respondCalls)
    }

    @Test
    fun `declining and games without positions answer straight away without a position`() = runTest {
        val withPositions = gateway()
        val declining = viewModel(withPositions)
        declining.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))
        assertNull(declining.state.value.positionPicker)
        assertEquals(1, withPositions.respondCalls)
        assertNull(withPositions.lastResponse?.position)

        val legacy = gateway(withPositions = false)
        val going = viewModel(legacy)
        going.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        assertNull(going.state.value.positionPicker)
        assertEquals(1, legacy.respondCalls)
        assertNull(legacy.lastResponse?.position)
    }

    @Test
    fun `someone already in the queue does not pick again`() = runTest {
        val queued = AttendanceEntry("me", AttendanceStatus.Waitlisted, 1, 1, position = AthletePosition.PONTA)
        val viewModel = viewModel(gateway(own = queued))
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        assertNull(viewModel.state.value.positionPicker)
    }

    @Test
    fun `guest sheet opens on the most free position and sends the chosen one`() = runTest {
        val attendance = gateway(own = AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1, position = AthletePosition.CENTRAL))
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.OpenGuestSheet)
        assertEquals(AthletePosition.CENTRAL, viewModel.state.value.guest.position)

        viewModel.onIntent(GameDetailIntent.UpdateGuestName("Rafa"))
        viewModel.onIntent(GameDetailIntent.UpdateGuestPosition(AthletePosition.LEVANTADOR))
        viewModel.onIntent(GameDetailIntent.SubmitGuest)

        assertEquals(1, attendance.addGuestCalls)
        assertEquals(AthletePosition.LEVANTADOR, attendance.lastAddGuestCommand?.position)
    }

    @Test
    fun `a guest in a game without positions goes without a position`() = runTest {
        val attendance = gateway(withPositions = false, own = AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1))
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.OpenGuestSheet)
        viewModel.onIntent(GameDetailIntent.UpdateGuestName("Rafa"))
        viewModel.onIntent(GameDetailIntent.SubmitGuest)
        assertNull(attendance.lastAddGuestCommand?.position)
    }

    @Test
    fun `the organizer forces a waiting person into a full position with the position and force flag`() = runTest {
        val attendance = gateway()
        val viewModel = viewModel(attendance)

        viewModel.onIntent(GameDetailIntent.RequestForceEntry("w1"))
        val picker = assertNotNull(viewModel.state.value.positionPicker)
        assertEquals(GamePositionPurpose.Force("w1", "Duda", AthletePosition.PONTA), picker.purpose)

        viewModel.onIntent(GameDetailIntent.ConfirmForceEntry("Entrada forçada pelo gestor"))

        assertEquals(1, attendance.overrideCalls)
        val sent = assertNotNull(attendance.lastOverride)
        assertEquals("w1", sent.memberId)
        assertEquals(AttendanceIntent.Confirm, sent.intent)
        assertEquals(AthletePosition.PONTA, sent.position)
        assertTrue(sent.force)
        assertNull(viewModel.state.value.positionPicker)
    }

    @Test
    fun `forcing is only for the organizer and only into a full position`() = runTest {
        val athleteSide = viewModel(gateway(), role = GroupRole.ATHLETE)
        athleteSide.onIntent(GameDetailIntent.RequestForceEntry("w1"))
        assertNull(athleteSide.state.value.positionPicker)

        // Duda espera na PONTA, mas se a PONTA tiver vaga livre não há o que forçar.
        val free = FakeAttendanceGateway(
            readResult = SaqzResult.Success(detail().copy(positions = listOf(summary(AthletePosition.PONTA, 2, 1)))),
            rosterResult = SaqzResult.Success(roster()),
        )
        val organizer = viewModel(free)
        organizer.onIntent(GameDetailIntent.RequestForceEntry("w1"))
        assertNull(organizer.state.value.positionPicker)
    }

    @Test
    fun `a failed forced entry keeps the sheet open with the error`() = runTest {
        val attendance = gateway()
        attendance.overrideResult = SaqzResult.Failure(br.com.saqz.groups.domain.attendance.AttendanceError.Data(br.com.saqz.domain.DataError.Server))
        val viewModel = viewModel(attendance)
        viewModel.onIntent(GameDetailIntent.RequestForceEntry("w1"))
        viewModel.onIntent(GameDetailIntent.ConfirmForceEntry("Entrada forçada pelo gestor"))
        val picker = assertNotNull(viewModel.state.value.positionPicker)
        assertTrue(picker.failed)
        assertFalse(picker.sending)
    }

    @Test
    fun `placing someone who has not answered carries the position and forces only when it is full`() = runTest {
        val athletes = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("m1").copy(position = AthletePosition.PONTA))),
        )
        val attendance = gateway()
        val viewModel = viewModel(attendance, athletes = athletes)
        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("m1"))
        assertEquals(AthletePosition.PONTA, viewModel.state.value.organizer.placePosition)

        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember("Escolha do organizador"))
        assertEquals(AthletePosition.PONTA, attendance.lastOverride?.position)
        assertTrue(attendance.lastOverride?.force == true)

        viewModel.onIntent(GameDetailIntent.OpenPlaceSheet)
        viewModel.onIntent(GameDetailIntent.SelectPlaceCandidate("m1"))
        viewModel.onIntent(GameDetailIntent.SelectPlacePosition(AthletePosition.LEVANTADOR))
        viewModel.onIntent(GameDetailIntent.ConfirmPlaceMember("Escolha do organizador"))
        assertEquals(AthletePosition.LEVANTADOR, attendance.lastOverride?.position)
        assertFalse(attendance.lastOverride?.force == true)
    }
}
