package br.com.saqz.groups.presentation.home

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.home.HomeError
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.domain.home.HomeMemberGroup
import br.com.saqz.groups.domain.home.HomeMemberReadModel
import br.com.saqz.groups.domain.home.HomeNextGame
import br.com.saqz.groups.domain.home.HomeOwnAttendance
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.domain.home.HomeRosterPreview
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = GroupNowPort { Instant.parse("2026-07-28T12:00:00Z") }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success keeps the home aggregate first name and formatted member copy`() = runTest {
        val home = sampleHome(nextGame = sampleNextGame())
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(home)))

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Bruna", viewModel.state.value.displayName)
        assertEquals(home, viewModel.state.value.home)
        assertEquals("Terça", viewModel.state.value.member?.subtitle?.substringBefore(" tem"))
        assertEquals("9 de 12 confirmados", viewModel.state.value.member?.nextGame?.confirmedSummary)
        assertTrue(viewModel.state.value.member?.nextGame?.deadline?.contains("hoje") == true)
        assertFalse(viewModel.state.value.loadFailed)
    }

    @Test
    fun `home without next game formats the empty week subtitle`() = runTest {
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

        assertEquals("Semana sem jogo por aqui.", viewModel.state.value.member?.subtitle)
        assertNull(viewModel.state.value.member?.nextGame)
    }

    @Test
    fun `home failure exposes the standard retry error`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Failure(HomeError.Data(DataError.Server)),
            ),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(br.com.saqz.groups.presentation.GroupUiError.Network, viewModel.state.value.error)
    }

    @Test
    fun `retry ignores a slower response from the previous load generation`() = runTest {
        val first = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val second = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val viewModel = HomeViewModel(
            homeGateway = DeferredHomeGateway(first, second),
            athleteGateway = FakeAthleteGateway(),
            attendanceGateway = FakeAttendanceGateway(),
            now = now,
        )

        viewModel.onIntent(HomeIntent.Retry)
        second.complete(SaqzResult.Success(sampleHome(id = "second")))
        first.complete(SaqzResult.Success(sampleHome(id = "first")))

        assertEquals("second", viewModel.state.value.home?.member?.groups?.singleOrNull()?.id?.value)
    }

    @Test
    fun `response is optimistic and successful confirmation refreshes home with a toast`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val refreshed = sampleHome(
            nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Confirmed, null)),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(nextGame = sampleNextGame())),
                SaqzResult.Success(refreshed),
            ),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertTrue(viewModel.state.value.responding)

        response.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))
        advanceUntilIdle()

        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(HomeToast.Confirmed, viewModel.state.value.toast)
        assertFalse(viewModel.state.value.responding)
        assertEquals(refreshed, viewModel.state.value.home)
    }

    @Test
    fun `failed response restores the previous attendance and exposes the standard response error`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server)),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertNull(viewModel.state.value.member?.nextGame?.ownAttendance)
        assertTrue(viewModel.state.value.responseFailed)
        assertFalse(viewModel.state.value.responding)
    }

    @Test
    fun `older response cannot overwrite a newer retry generation`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(nextGame = sampleNextGame())),
                SaqzResult.Success(sampleHome(id = "newer")),
            ),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        viewModel.onIntent(HomeIntent.Retry)
        response.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))
        advanceUntilIdle()

        assertEquals("newer", viewModel.state.value.home?.member?.groups?.single()?.id?.value)
        assertNull(viewModel.state.value.toast)
    }

    private fun viewModel(
        homeGateway: HomeGateway,
        attendanceGateway: FakeAttendanceGateway = FakeAttendanceGateway(),
    ) = HomeViewModel(
        homeGateway = homeGateway,
        athleteGateway = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(OwnAthleteProfile("me", "Bruna Silva", null, emptyList())),
        ),
        attendanceGateway = attendanceGateway,
        now = now,
    )
}

private class SequenceHomeGateway(
    private vararg val results: SaqzResult<HomeReadModel, HomeError>,
) : HomeGateway {
    private var reads = 0

    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> =
        results.getOrElse(reads++) { results.last() }
}

private class DeferredHomeGateway(
    private val first: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
    private val second: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
) : HomeGateway {
    private var reads = 0

    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> =
        if (reads++ == 0) first.await() else second.await()
}

private fun sampleHome(
    id: String = "group-1",
    nextGame: HomeNextGame? = null,
) = HomeReadModel(
    member = HomeMemberReadModel(
        nextGame = nextGame,
        lastCompletedGame = null,
        groups = listOf(
            HomeMemberGroup(
                id = GroupId(id),
                name = "Vôlei do CERET",
                role = GroupRole.ATHLETE,
                memberCount = 12,
                gamesPlayed = 3,
            ),
        ),
    ),
    admin = null,
)

private fun sampleNextGame(
    attendance: HomeOwnAttendance? = null,
) = HomeNextGame(
    groupId = GroupId("group-1"),
    groupName = "Vôlei do CERET",
    gameId = "game-1",
    local = "CERET — Quadra 2 · Tatuapé",
    startsAt = "2026-07-28T19:30:00-03:00",
    confirmationDeadline = "2026-07-28T18:00:00-03:00",
    capacity = 12,
    confirmedCount = 9,
    declinedCount = 1,
    pendingCount = 2,
    waitlistCount = 0,
    ownAttendance = attendance,
    membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA,
    mensalistaPriority = false,
    rosterPreview = HomeRosterPreview(confirmed = listOf(), waitlisted = listOf()),
)
