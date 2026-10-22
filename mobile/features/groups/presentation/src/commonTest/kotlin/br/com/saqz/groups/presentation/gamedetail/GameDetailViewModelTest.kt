package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.AddGuestCommand
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendancePromotionCommand
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceMutation
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupGameConfig
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.presentation.FakeNativeNotifications
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleCancelledGame
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleAttendanceDetail
import br.com.saqz.groups.presentation.sampleAttendanceRoster
import br.com.saqz.groups.presentation.sampleVersionedAttendanceCapacity
import br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.sampleVersionedGroup
import br.com.saqz.groups.presentation.sampleVersionedGame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads game details`() = runTest {
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame()))
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(sampleAttendanceDetail().copy(declinedCount = 2, pendingCount = 3)),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), attendance, FakeAthleteGateway())
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, gateway.readCalls)
        assertNotNull(viewModel.state.value.header)
        assertNotNull(viewModel.state.value.attendance)
        assertEquals(2, viewModel.state.value.attendance?.declined)
        assertEquals(3, viewModel.state.value.attendance?.pending)
    }

    @Test
    fun `completed game settlement intent emits only for organizer`() = runTest {
        val completed = sampleVersionedGame().copy(
            game = sampleVersionedGame().game.copy(status = GameStatus.Completed),
        )
        val organizer = GameDetailViewModel(
            "group-1",
            "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(completed)),
            FakeGroupGateway(),
            FakeAttendanceGateway(),
            FakeAthleteGateway(),
        )
        organizer.onIntent(GameDetailIntent.OpenSettlement)

        assertEquals(GameDetailEffect.OpenSettlement, organizer.effects.first())

        val athlete = GameDetailViewModel(
            "group-1",
            "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(completed)),
            FakeGroupGateway(
                SaqzResult.Success(
                    sampleVersionedGroup(sampleGroup(role = br.com.saqz.groups.domain.group.GroupRole.ATHLETE)),
                ),
            ),
            FakeAttendanceGateway(),
            FakeAthleteGateway(),
        )
        athlete.onIntent(GameDetailIntent.OpenSettlement)

        assertEquals(false, athlete.state.value.isAdmin)
    }

    @Test
    fun `cancelled game with charges to review opens the settlement for the organizer`() = runTest {
        val cancelled = sampleVersionedGame().copy(
            game = sampleVersionedGame().game.copy(status = GameStatus.Cancelled, financeReviewRequired = true),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(cancelled)),
            FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway(),
        )

        assertTrue(viewModel.state.value.header?.financeReviewRequired == true)
        assertTrue(viewModel.state.value.canReviewFinance)
        viewModel.onIntent(GameDetailIntent.OpenSettlement)

        assertEquals(GameDetailEffect.OpenSettlement, viewModel.effects.first())
    }

    @Test
    fun `cancelled game without the review mark has no warning and no settlement`() = runTest {
        val cancelled = sampleVersionedGame().copy(
            game = sampleVersionedGame().game.copy(status = GameStatus.Cancelled, financeReviewRequired = false),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(cancelled)),
            FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway(),
        )
        val effects = mutableListOf<GameDetailEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }

        viewModel.onIntent(GameDetailIntent.OpenSettlement)

        assertFalse(viewModel.state.value.canReviewFinance)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `athlete gets no finance review warning on a cancelled game`() = runTest {
        val cancelled = sampleVersionedGame().copy(
            game = sampleVersionedGame().game.copy(status = GameStatus.Cancelled, financeReviewRequired = true),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(cancelled)),
            FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = GroupRole.ATHLETE)))),
            FakeAttendanceGateway(), FakeAthleteGateway(),
        )
        val effects = mutableListOf<GameDetailEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }

        viewModel.onIntent(GameDetailIntent.OpenSettlement)

        assertFalse(viewModel.state.value.canReviewFinance)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `maps gateway failures`() = runTest {
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Failure(GameError.Data(DataError.Forbidden))),
            FakeGroupGateway(),
            FakeAttendanceGateway(),
            FakeAthleteGateway(),
        )
        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `group failure is visible instead of silently removing admin actions`() = runTest {
        val groupGateway = FakeGroupGateway(
            readResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Forbidden)),
        )
        val viewModel = GameDetailViewModel(
            "group-1",
            "game-1",
            FakeGameGateway(),
            groupGateway,
            FakeAttendanceGateway(),
            FakeAthleteGateway(),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isAdmin)

        groupGateway.readResult = SaqzResult.Success(sampleVersionedGroup())
        viewModel.onIntent(GameDetailIntent.Retry)

        assertFalse(viewModel.state.value.loadFailed)
        assertTrue(viewModel.state.value.isAdmin)
    }

    @Test
    fun `retry and generation guard work`() = runTest {
        val old = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val fresh = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(old, fresh)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        viewModel.onIntent(GameDetailIntent.Retry)
        gateway.completeRead(1, SaqzResult.Success(sampleVersionedGame()))
        old.complete(SaqzResult.Failure(GameError.Data(DataError.Forbidden)))
        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `refresh keeps the content on screen while reloading and swaps it when the server answers`() = runTest {
        val first = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val second = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(first, second)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        gateway.completeRead(0, SaqzResult.Success(sampleVersionedGame()))
        assertEquals(GameDetailStatusTone.Published, viewModel.state.value.header?.statusTone)

        viewModel.onIntent(GameDetailIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GameDetailIntent.Refresh)

        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.loadFailed)
        assertNotNull(viewModel.state.value.header)
        assertNotNull(viewModel.state.value.attendance)

        gateway.completeRead(1, SaqzResult.Success(VersionedGame(sampleCancelledGame(), GameVersionToken("etag-2"))))
        assertEquals(GameDetailStatusTone.Cancelled, viewModel.state.value.header?.statusTone)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `refresh failure keeps the loaded content and shows no error screen`() = runTest {
        val gateway = FakeGameGateway(
            readResults = ArrayDeque(listOf(
                SaqzResult.Success(sampleVersionedGame()),
                SaqzResult.Failure(GameError.Data(DataError.Forbidden)),
            )),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GameDetailIntent.Refresh)

        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.loadFailed)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.header)
    }

    @Test
    fun `pull refresh keeps the game and ends not refreshing`() = runTest {
        val first = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val second = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(first, second)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        gateway.completeRead(0, SaqzResult.Success(sampleVersionedGame()))

        viewModel.onIntent(GameDetailIntent.PullRefresh)
        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.header)

        gateway.completeRead(1, SaqzResult.Success(VersionedGame(sampleCancelledGame(), GameVersionToken("etag-2"))))
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(GameDetailStatusTone.Cancelled, viewModel.state.value.header?.statusTone)
    }

    @Test
    fun `pull refresh failure keeps the game and warns`() = runTest {
        val gateway = FakeGameGateway(
            readResults = ArrayDeque(listOf(
                SaqzResult.Success(sampleVersionedGame()),
                SaqzResult.Failure(GameError.Data(DataError.Connectivity)),
            )),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.PullRefresh)

        val state = viewModel.state.value
        assertFalse(state.isRefreshing)
        assertFalse(state.loadFailed)
        assertTrue(state.refreshFailed)
        assertNotNull(state.header)
        viewModel.onIntent(GameDetailIntent.DismissRefreshFailed)
        assertFalse(viewModel.state.value.refreshFailed)
    }

    @Test
    fun `pull during a load in flight rides it and warns when it fails`() = runTest {
        val first = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val second = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(first, second)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        gateway.completeRead(0, SaqzResult.Success(sampleVersionedGame()))
        viewModel.onIntent(GameDetailIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GameDetailIntent.Refresh)

        viewModel.onIntent(GameDetailIntent.PullRefresh)
        assertTrue(viewModel.state.value.isRefreshing)
        assertEquals(2, gateway.readCalls)

        gateway.completeRead(1, SaqzResult.Failure(GameError.Data(DataError.Connectivity)))
        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.isRefreshing)
        assertTrue(viewModel.state.value.refreshFailed)
        assertNotNull(viewModel.state.value.header)
    }

    @Test
    fun `two pulls in a row fetch once`() = runTest {
        val first = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val second = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(first, second)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        gateway.completeRead(0, SaqzResult.Success(sampleVersionedGame()))

        viewModel.onIntent(GameDetailIntent.PullRefresh)
        viewModel.onIntent(GameDetailIntent.PullRefresh)
        gateway.completeRead(1, SaqzResult.Success(sampleVersionedGame()))

        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `refresh is ignored while a load is in flight and runs again after it ends`() = runTest {
        val first = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(first)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        // Logo depois do `init { load() }`, o primeiro `resume` do Root não pode repetir a ida ao servidor.
        viewModel.onIntent(GameDetailIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GameDetailIntent.Refresh)
        assertEquals(1, gateway.readCalls)

        gateway.completeRead(0, SaqzResult.Success(sampleVersionedGame()))
        viewModel.onIntent(GameDetailIntent.Refresh)
        assertEquals(2, gateway.readCalls)
    }

    @Test
    fun `first refresh after init does not reload and the second does`() = runTest {
        val gateway = FakeGameGateway()
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        assertEquals(1, gateway.readCalls)

        viewModel.onIntent(GameDetailIntent.Refresh)
        assertEquals(1, gateway.readCalls)

        viewModel.onIntent(GameDetailIntent.Refresh)
        assertEquals(2, gateway.readCalls)
    }

    @Test
    fun `cancel uses optimistic version and emits effect`() = runTest {
        val gateway = FakeGameGateway(
            lifecycleResult = SaqzResult.Success(VersionedGame(sampleCancelledGame(), GameVersionToken("etag-2"))),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        viewModel.onIntent(GameDetailIntent.RequestCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)
        assertEquals(GameLifecycleAction.Cancel, gateway.lastLifecycleAction)
        assertEquals(GameDetailStatusTone.Cancelled, viewModel.state.value.header?.statusTone)
        assertFalse(viewModel.state.value.cancelDialogOpen)
        assertEquals(GameDetailEffect.Cancelled, viewModel.effects.first())
    }

    @Test
    fun `second confirm while cancellation is in flight is ignored`() = runTest {
        val lifecycle = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(
            lifecycleDeferreds = ArrayDeque(listOf(lifecycle)),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.RequestCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)

        assertTrue(viewModel.state.value.cancelling)
        assertEquals(1, gateway.lifecycleVersions.size)

        lifecycle.complete(SaqzResult.Success(VersionedGame(sampleCancelledGame(), GameVersionToken("etag-2"))))
        assertFalse(viewModel.state.value.cancelling)
    }

    @Test
    fun `publish promotes the draft and reloads with the new version`() = runTest {
        val draft = sampleVersionedGame().copy(game = sampleVersionedGame().game.copy(status = GameStatus.Draft))
        val published = VersionedGame(sampleVersionedGame().game, GameVersionToken("etag-2"))
        val gateway = FakeGameGateway(
            readResults = ArrayDeque(listOf(SaqzResult.Success(draft))),
            readResult = SaqzResult.Success(published),
            lifecycleResult = SaqzResult.Success(published),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        assertEquals(GameDetailStatusTone.Draft, viewModel.state.value.header?.statusTone)

        viewModel.onIntent(GameDetailIntent.Publish)

        assertEquals(GameLifecycleAction.Publish, gateway.lastLifecycleAction)
        assertEquals(listOf(GameVersionToken("etag-1")), gateway.lifecycleVersions)
        assertEquals(2, gateway.readCalls)
        assertEquals(GameDetailStatusTone.Published, viewModel.state.value.header?.statusTone)
        assertFalse(viewModel.state.value.publishing)
        assertFalse(viewModel.state.value.publishFailed)
    }

    @Test
    fun `publish is ignored for published games and for athletes`() = runTest {
        val gateway = FakeGameGateway()
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        viewModel.onIntent(GameDetailIntent.Publish)
        assertTrue(gateway.lifecycleVersions.isEmpty())

        val draft = sampleVersionedGame().copy(game = sampleVersionedGame().game.copy(status = GameStatus.Draft))
        val athleteGateway = FakeGameGateway(readResult = SaqzResult.Success(draft))
        val athlete = GameDetailViewModel(
            "group-1",
            "game-1",
            athleteGateway,
            FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = GroupRole.ATHLETE)))),
            FakeAttendanceGateway(),
            FakeAthleteGateway(),
        )
        athlete.onIntent(GameDetailIntent.Publish)
        assertTrue(athleteGateway.lifecycleVersions.isEmpty())
    }

    @Test
    fun `publish failure keeps the draft and surfaces the error`() = runTest {
        val draft = sampleVersionedGame().copy(game = sampleVersionedGame().game.copy(status = GameStatus.Draft))
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(draft),
            lifecycleResult = SaqzResult.Failure(GameError.Data(DataError.Server)),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.Publish)

        assertTrue(viewModel.state.value.publishFailed)
        assertFalse(viewModel.state.value.publishing)
        assertEquals(GameDetailStatusTone.Draft, viewModel.state.value.header?.statusTone)
        assertEquals(1, gateway.readCalls)
    }

    @Test
    fun `publishing a draft beyond the plan end shows its own text and not the retry`() = runTest {
        val draft = sampleVersionedGame().copy(game = sampleVersionedGame().game.copy(status = GameStatus.Draft))
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(draft),
            lifecycleResult = SaqzResult.Failure(GameError.AfterPlanEnd("2026-08-02T03:00:00Z")),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.Publish)

        assertTrue(viewModel.state.value.publishAfterPlanEnd)
        assertFalse(viewModel.state.value.publishFailed)
        assertFalse(viewModel.state.value.publishing)
        assertEquals(GameDetailStatusTone.Draft, viewModel.state.value.header?.statusTone)
    }

    @Test
    fun `publishing beyond the plan end reports game_after_plan_end with the publish action`() = runTest {
        val draft = sampleVersionedGame().copy(game = sampleVersionedGame().game.copy(status = GameStatus.Draft))
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(draft),
            lifecycleResult = SaqzResult.Failure(GameError.AfterPlanEnd("2026-08-02T03:00:00Z")),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        val recorded = mutableListOf<Pair<String, Map<String, String>>>()
        SaqzAnalytics.track = { name, params -> recorded += name to params }
        try {
            viewModel.onIntent(GameDetailIntent.Publish)
        } finally {
            SaqzAnalytics.reset()
        }

        assertEquals(
            listOf(
                mapOf(
                    "attempted_date" to "2026-08-04",
                    "limit_date" to "2026-08-02",
                    "error_code" to "GAME_AFTER_PLAN_END",
                    "action" to "publish",
                ),
            ),
            recorded.filter { it.first == "game_after_plan_end" }.map { it.second },
        )
    }

    @Test
    fun `the beyond plan end text stays while the start is the same and goes away once the date is corrected`() = runTest {
        val game = sampleVersionedGame().game
        val draft = sampleVersionedGame().copy(game = game.copy(status = GameStatus.Draft))
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(draft),
            lifecycleResult = SaqzResult.Failure(GameError.AfterPlanEnd("2026-08-02T03:00:00Z")),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        viewModel.onIntent(GameDetailIntent.Publish)
        assertTrue(viewModel.state.value.publishAfterPlanEnd)

        // Volta do editor sem mudar o início: segue valendo.
        viewModel.onIntent(GameDetailIntent.Retry)
        assertTrue(viewModel.state.value.publishAfterPlanEnd)

        // O organizador corrigiu a data: o texto some sem precisar tocar em Publicar.
        gateway.readResult = SaqzResult.Success(
            draft.copy(game = game.copy(status = GameStatus.Draft, localDate = "2026-07-30", startsAt = "2026-07-30T19:30:00-03:00")),
        )
        viewModel.onIntent(GameDetailIntent.Retry)
        assertFalse(viewModel.state.value.publishAfterPlanEnd)
        assertFalse(viewModel.state.value.publishFailed)
    }

    @Test
    fun `publish conflict reloads the game instead of failing`() = runTest {
        val draft = sampleVersionedGame().copy(game = sampleVersionedGame().game.copy(status = GameStatus.Draft))
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(draft),
            lifecycleResult = SaqzResult.Failure(GameError.Conflict()),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.Publish)

        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.publishFailed)
    }

    @Test
    fun `draft game does not open cancellation confirmation`() = runTest {
        val draft = sampleVersionedGame().copy(
            game = sampleVersionedGame().game.copy(status = GameStatus.Draft),
        )
        val viewModel = GameDetailViewModel(
            "group-1",
            "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(draft)),
            FakeGroupGateway(),
            FakeAttendanceGateway(),
            FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.RequestCancel)

        assertFalse(viewModel.state.value.cancelDialogOpen)
    }

    @Test
    fun `dismiss is ignored while cancellation is in flight`() = runTest {
        val lifecycle = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val viewModel = GameDetailViewModel(
            "group-1",
            "game-1",
            FakeGameGateway(lifecycleDeferreds = ArrayDeque(listOf(lifecycle))),
            FakeGroupGateway(),
            FakeAttendanceGateway(),
            FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.RequestCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)
        viewModel.onIntent(GameDetailIntent.DismissCancel)

        assertTrue(viewModel.state.value.cancelling)
        assertTrue(viewModel.state.value.cancelDialogOpen)

        lifecycle.complete(SaqzResult.Success(VersionedGame(sampleCancelledGame(), GameVersionToken("etag-2"))))

        assertFalse(viewModel.state.value.cancelling)
        assertFalse(viewModel.state.value.cancelDialogOpen)
    }

    @Test
    fun `deadline includes its date when it is before the game day`() = runTest {
        val game = sampleVersionedGame().game.copy(confirmationDeadline = "2026-08-03T22:30:00Z")
        val viewModel = GameDetailViewModel(
            "group-1",
            "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(VersionedGame(game, GameVersionToken("etag-1")))),
            FakeGroupGateway(),
            FakeAttendanceGateway(),
            FakeAthleteGateway(),
        )

        val header = viewModel.state.value.header
        assertEquals("03/08/2026 · 19h30", header?.confirmationDeadline)
        assertEquals(GroupWeekday.MONDAY, header?.confirmationDeadlineWeekday)
    }

    @Test
    fun `conflict reloads the game before allowing another cancel`() = runTest {
        val gateway = FakeGameGateway(
            readResults = ArrayDeque(
                listOf(
                    SaqzResult.Success(sampleVersionedGame()),
                    SaqzResult.Success(sampleVersionedGame().copy(version = GameVersionToken("etag-3"))),
                ),
            ),
            lifecycleResults = ArrayDeque(
                listOf(
                    SaqzResult.Failure(GameError.Conflict()),
                    SaqzResult.Success(VersionedGame(sampleCancelledGame(), GameVersionToken("etag-4"))),
                ),
            ),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.RequestCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)

        assertEquals(GameVersionToken("etag-1"), gateway.lifecycleVersions[0])
        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.cancelDialogOpen)
        assertEquals(GameDetailStatusTone.Published, viewModel.state.value.header?.statusTone)

        viewModel.onIntent(GameDetailIntent.RequestCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)

        assertEquals(GameVersionToken("etag-3"), gateway.lifecycleVersions[1])
        assertEquals(GameDetailStatusTone.Cancelled, viewModel.state.value.header?.statusTone)
        assertEquals(GameDetailEffect.Cancelled, viewModel.effects.first())
    }

    @Test
    fun `version conflict on cancel reloads the game and does not show the error`() = runTest {
        val gateway = FakeGameGateway(
            readResults = ArrayDeque(
                listOf(
                    SaqzResult.Success(sampleVersionedGame()),
                    SaqzResult.Success(sampleVersionedGame().copy(version = GameVersionToken("etag-3"))),
                ),
            ),
            lifecycleResults = ArrayDeque(
                listOf(
                    SaqzResult.Failure(GameError.VersionConflict),
                    SaqzResult.Success(VersionedGame(sampleCancelledGame(), GameVersionToken("etag-4"))),
                ),
            ),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.RequestCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)

        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.cancelFailed)
        assertFalse(viewModel.state.value.cancelling)
        assertFalse(viewModel.state.value.cancelDialogOpen)

        // A segunda tentativa já vai com a versão recarregada, não com a velha.
        viewModel.onIntent(GameDetailIntent.RequestCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)

        assertEquals(GameVersionToken("etag-3"), gateway.lifecycleVersions[1])
        assertEquals(GameDetailStatusTone.Cancelled, viewModel.state.value.header?.statusTone)
    }

    @Test
    fun `version conflict on publish reloads the game and does not show the error`() = runTest {
        val draft = sampleVersionedGame().copy(game = sampleVersionedGame().game.copy(status = GameStatus.Draft))
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(draft),
            lifecycleResult = SaqzResult.Failure(GameError.VersionConflict),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())

        viewModel.onIntent(GameDetailIntent.Publish)

        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.publishFailed)
        assertFalse(viewModel.state.value.publishing)
    }

    @Test
    fun `loads waitlist in backend order and exposes manual priority config`() = runTest {
        val group = sampleVersionedGroup().copy(
            group = sampleVersionedGroup().group.copy(
                gameConfig = GroupGameConfig(
                    mensalistaPriority = true,
                    promotionMode = br.com.saqz.groups.domain.group.PromotionMode.MANUAL,
                ),
            ),
        )
        val athletes = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(
                    br.com.saqz.groups.presentation.sampleRosterEntry("wait-1"),
                    br.com.saqz.groups.presentation.sampleRosterEntry("wait-2")
                        .copy(membershipType = AthleteMembershipType.AVULSO),
                ),
            ),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(), FakeGroupGateway(SaqzResult.Success(group)),
            FakeAttendanceGateway(), athletes,
        )

        assertEquals(listOf("wait-1", "wait-2"), viewModel.state.value.waitlist.map { it.id })
        assertTrue(viewModel.state.value.mensalistaPriority)
        assertEquals(
            br.com.saqz.groups.domain.group.PromotionMode.MANUAL,
            viewModel.state.value.promotionMode,
        )
        assertTrue(viewModel.state.value.waitlist.first().isMensalista)
    }

    @Test
    fun `fifo promotion intent is ignored`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(), FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.Promote("wait-1", "Escolha do organizador"))

        assertEquals(0, attendance.promoteCalls)
        assertEquals(2, viewModel.state.value.waitlist.size)
    }

    @Test
    fun `promotion rolls back waitlist on failure`() = runTest {
        val attendance = FakeAttendanceGateway()
        val deferred = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation, AttendanceError>>()
        attendance.promoteDeferred = deferred
        val group = sampleVersionedGroup().copy(
            group = sampleVersionedGroup().group.copy(
                gameConfig = GroupGameConfig(
                    promotionMode = br.com.saqz.groups.domain.group.PromotionMode.MANUAL,
                ),
            ),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))), FakeGroupGateway(SaqzResult.Success(group)),
            attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.Promote("wait-1", "Escolha do organizador"))
        assertEquals(listOf("wait-2"), viewModel.state.value.waitlist.map { it.id })

        deferred.complete(SaqzResult.Failure(AttendanceError.Data(DataError.Server)))

        assertEquals(listOf("wait-1", "wait-2"), viewModel.state.value.waitlist.map { it.id })
        assertTrue(viewModel.state.value.promotionFailed)
    }

    @Test
    fun `after the game started promote and the capacity sheet are ignored`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = manualWaitlist(attendance, game = openGame().copy(startsAt = "2020-01-01T20:00:00-03:00"))
        assertTrue(viewModel.state.value.header?.gameStarted == true)
        assertEquals(2, viewModel.state.value.waitlist.size)

        viewModel.onIntent(GameDetailIntent.Promote("wait-1", "Escolha do organizador"))
        viewModel.onIntent(GameDetailIntent.OpenCapacitySheet)

        assertEquals(0, attendance.promoteCalls)
        assertFalse(viewModel.state.value.capacitySheetOpen)
        assertEquals(2, viewModel.state.value.waitlist.size)
    }

    @Test
    fun `before the game starts promote and the capacity sheet still work`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = manualWaitlist(attendance, game = openGame())
        assertFalse(viewModel.state.value.header?.gameStarted == true)

        viewModel.onIntent(GameDetailIntent.OpenCapacitySheet)
        assertTrue(viewModel.state.value.capacitySheetOpen)
        viewModel.onIntent(GameDetailIntent.DismissCapacitySheet)
        viewModel.onIntent(GameDetailIntent.Promote("wait-1", "Escolha do organizador"))

        assertEquals(1, attendance.promoteCalls)
    }

    @Test
    fun `a cancelled or draft game is not reported as started`() = runTest {
        val draft = manualWaitlist(FakeAttendanceGateway(), game = openGame().copy(status = GameStatus.Draft, startsAt = "2020-01-01T20:00:00-03:00"))
        assertFalse(draft.state.value.header?.gameStarted == true)
    }

    private fun manualWaitlist(
        attendance: FakeAttendanceGateway,
        game: br.com.saqz.groups.domain.game.Game,
    ): GameDetailViewModel {
        val group = sampleVersionedGroup().copy(
            group = sampleVersionedGroup().group.copy(
                gameConfig = GroupGameConfig(
                    mensalistaPriority = true,
                    promotionMode = br.com.saqz.groups.domain.group.PromotionMode.MANUAL,
                ),
            ),
        )
        val athletes = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(
                    br.com.saqz.groups.presentation.sampleRosterEntry("wait-1"),
                    br.com.saqz.groups.presentation.sampleRosterEntry("wait-2").copy(membershipType = AthleteMembershipType.AVULSO),
                ),
            ),
        )
        return responder(attendance, game = game, athletes = athletes, groupGateway = FakeGroupGateway(SaqzResult.Success(group)))
    }

    @Test
    fun `capacity sheet never goes below the minimum of 4`() = runTest {
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))), FakeGroupGateway(),
            FakeAttendanceGateway(), FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.OpenCapacitySheet)
        listOf(0, 2, 3).forEach { value ->
            viewModel.onIntent(GameDetailIntent.UpdateCapacity(value))
            assertEquals(4, viewModel.state.value.capacityDraft)
        }
        viewModel.onIntent(GameDetailIntent.UpdateCapacity(100))
        assertEquals(100, viewModel.state.value.capacityDraft)
        viewModel.onIntent(GameDetailIntent.UpdateCapacity(101))
        assertEquals(100, viewModel.state.value.capacityDraft)
    }

    @Test
    fun `capacity rollback restores optimistic value`() = runTest {
        val attendance = FakeAttendanceGateway()
        val deferred = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity, AttendanceError>>()
        attendance.capacityDeferred = deferred
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(), FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.OpenCapacitySheet)
        viewModel.onIntent(GameDetailIntent.UpdateCapacity(14))
        viewModel.onIntent(GameDetailIntent.SaveCapacity)
        assertEquals(14, viewModel.state.value.attendance?.capacity)

        deferred.complete(SaqzResult.Failure(AttendanceError.Data(DataError.Server)))

        assertEquals(12, viewModel.state.value.attendance?.capacity)
        assertTrue(viewModel.state.value.capacityFailed)
    }

    @Test
    fun `capacity conflict closes sheet and reloads`() = runTest {
        val attendance = FakeAttendanceGateway(capacityResult = SaqzResult.Failure(AttendanceError.Conflict))
        val gateway = FakeGameGateway(
            readResults = ArrayDeque(
                listOf(SaqzResult.Success(sampleVersionedGame()), SaqzResult.Success(sampleVersionedGame())),
            ),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", gateway, FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.OpenCapacitySheet)
        viewModel.onIntent(GameDetailIntent.UpdateCapacity(14))
        viewModel.onIntent(GameDetailIntent.SaveCapacity)

        assertEquals(2, gateway.readCalls)
        assertFalse(viewModel.state.value.capacitySheetOpen)
        assertEquals(12, viewModel.state.value.attendance?.capacity)
    }

    @Test
    fun `promotion reconciles header while capacity is in flight`() = runTest {
        val attendance = FakeAttendanceGateway()
        val promotion = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation, AttendanceError>>()
        val capacity = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity, AttendanceError>>()
        attendance.promoteDeferred = promotion
        attendance.capacityDeferred = capacity
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))), manualGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.Promote("wait-1", "Escolha do organizador"))
        viewModel.onIntent(GameDetailIntent.OpenCapacitySheet)
        viewModel.onIntent(GameDetailIntent.UpdateCapacity(14))
        viewModel.onIntent(GameDetailIntent.SaveCapacity)

        promotion.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))

        assertEquals(3, viewModel.state.value.attendance?.availableSpots)
        assertEquals(3, viewModel.state.value.header?.availableSpots)
        assertNull(viewModel.state.value.promotingMemberId)

        capacity.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceCapacity()))
        assertFalse(viewModel.state.value.savingCapacity)
    }

    @Test
    fun `capacity reconciles while promotion is in flight`() = runTest {
        val attendance = FakeAttendanceGateway()
        val promotion = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation, AttendanceError>>()
        val capacity = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity, AttendanceError>>()
        attendance.promoteDeferred = promotion
        attendance.capacityDeferred = capacity
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))), manualGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.OpenCapacitySheet)
        viewModel.onIntent(GameDetailIntent.UpdateCapacity(14))
        viewModel.onIntent(GameDetailIntent.SaveCapacity)
        viewModel.onIntent(GameDetailIntent.Promote("wait-1", "Escolha do organizador"))

        capacity.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceCapacity()))

        assertFalse(viewModel.state.value.savingCapacity)
        assertTrue(viewModel.state.value.promotingMemberId == "wait-1")

        promotion.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))

        assertNull(viewModel.state.value.promotingMemberId)
        assertEquals(3, viewModel.state.value.header?.availableSpots)
    }

    private fun manualGroupGateway() = FakeGroupGateway(
        readResult = SaqzResult.Success(
            sampleVersionedGroup().copy(
                group = sampleVersionedGroup().group.copy(
                    gameConfig = GroupGameConfig(
                        promotionMode = br.com.saqz.groups.domain.group.PromotionMode.MANUAL,
                    ),
                ),
            ),
        ),
    )

    private fun athleteGroupGateway() = FakeGroupGateway(
        SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = br.com.saqz.groups.domain.group.GroupRole.ATHLETE))),
    )

    private fun openGame() = sampleGame().copy(confirmationDeadline = "2030-01-01T12:00:00-03:00", startsAt = "2030-01-01T20:00:00-03:00")

    private fun goingDetail(status: AttendanceStatus, memberId: String = "me") =
        sampleAttendanceDetail().copy(ownAttendance = AttendanceEntry(memberId, status, null, 1L))

    @Test
    fun guestButtonIsHiddenUntilTheGameIsPublished() = runTest {
        val draft = sampleVersionedGame(sampleGame().copy(status = GameStatus.Draft))
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(draft)),
            FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway(),
        )

        assertFalse(viewModel.state.value.guest.visible)
    }

    @Test
    fun guestButtonNeedsTheViewersOwnAnswer() = runTest {
        val noAnswer = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway(),
        )
        assertEquals(GameGuestHint.NeedAnswer, noAnswer.state.value.guest.hint)
        assertFalse(noAnswer.state.value.guest.enabled)

        val declined = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(),
            FakeAttendanceGateway(readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Declined))),
            FakeAthleteGateway(),
        )
        assertEquals(GameGuestHint.NeedAnswer, declined.state.value.guest.hint)
        assertFalse(declined.state.value.guest.enabled)
    }

    @Test
    fun guestButtonClosesWithTheDeadline() = runTest {
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame())),
            FakeGroupGateway(),
            FakeAttendanceGateway(readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed))),
            FakeAthleteGateway(),
        )

        assertEquals(GameGuestHint.Closed, viewModel.state.value.guest.hint)
        assertFalse(viewModel.state.value.guest.enabled)
    }

    @Test
    fun waitlistedHostCanBringAGuest() = runTest {
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(),
            FakeAttendanceGateway(readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Waitlisted))),
            FakeAthleteGateway(),
        )

        assertTrue(viewModel.state.value.guest.enabled)
        assertEquals(GameGuestHint.Default, viewModel.state.value.guest.hint)
    }

    @Test
    fun submitGuestCallsTheGatewayReloadsAndAnnounces() = runTest {
        val attendance = FakeAttendanceGateway(readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed)))
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.OpenGuestSheet)
        viewModel.onIntent(GameDetailIntent.UpdateGuestName("  Rafa Moreira  "))
        viewModel.onIntent(GameDetailIntent.SubmitGuest)

        assertEquals(1, attendance.addGuestCalls)
        assertEquals("Rafa Moreira", attendance.lastAddGuestCommand?.displayName)
        assertEquals(2, attendance.rosterCalls)
        assertFalse(viewModel.state.value.guest.sheetOpen)
        assertFalse(viewModel.state.value.guest.adding)
        assertEquals("", viewModel.state.value.guest.name)
        assertEquals("Rafa Moreira", viewModel.state.value.guest.noticeName)
        assertTrue(viewModel.state.value.guest.noticeJoined)
    }

    @Test
    fun submitGuestFailureKeepsTheSheetAndTheName() = runTest {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed)),
            addGuestResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server)),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.OpenGuestSheet)
        viewModel.onIntent(GameDetailIntent.UpdateGuestName("Rafa Moreira"))
        viewModel.onIntent(GameDetailIntent.SubmitGuest)

        assertTrue(viewModel.state.value.guest.sheetOpen)
        assertEquals("Rafa Moreira", viewModel.state.value.guest.name)
        assertFalse(viewModel.state.value.guest.adding)
        assertTrue(viewModel.state.value.guest.addFailed)
        assertEquals(1, attendance.rosterCalls)
    }

    @Test
    fun nameShorterThanTwoLettersCannotBeSubmitted() = runTest {
        val attendance = FakeAttendanceGateway(readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed)))
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.OpenGuestSheet)
        viewModel.onIntent(GameDetailIntent.UpdateGuestName("A"))
        viewModel.onIntent(GameDetailIntent.SubmitGuest)

        assertEquals(0, attendance.addGuestCalls)
    }

    @Test
    fun guestRowsCarryHostAndOwnership() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(
                AttendanceRosterMember("host-1", "Convidado A", guestSeq = 1, hostDisplayName = "Host Um"),
                AttendanceRosterMember("host-1", "Convidado B", guestSeq = 2, hostDisplayName = "Host Um"),
            ),
            waitlisted = emptyList(),
        )
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed, memberId = "host-1")),
            rosterResult = SaqzResult.Success(roster),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        val rows = viewModel.state.value.confirmedRoster
        assertEquals(listOf("host-1#1", "host-1#2"), rows.map { it.id })
        assertTrue(rows.all { it.guest?.isYours == true })
        assertTrue(rows.all { it.guest?.hostName == "Host Um" })
    }

    @Test
    fun onlyHostAndOrganizerCanRemove() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("host-1", "Convidado A", guestSeq = 1, hostDisplayName = "Host Um")),
            waitlisted = emptyList(),
        )
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed, memberId = "someone-else")),
            rosterResult = SaqzResult.Success(roster),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            athleteGroupGateway(), attendance, FakeAthleteGateway(),
        )

        val row = viewModel.state.value.confirmedRoster.first()
        assertFalse(row.guest?.canRemove == true)

        viewModel.onIntent(GameDetailIntent.RequestRemoveGuest(row.id))

        assertNull(viewModel.state.value.guest.removal)
    }

    @Test
    fun hostCannotRemoveAfterTheDeadlineButOrganizerCan() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("host-1", "Convidado A", guestSeq = 1, hostDisplayName = "Host Um")),
            waitlisted = emptyList(),
        )
        val hostAttendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed, memberId = "host-1")),
            rosterResult = SaqzResult.Success(roster),
        )
        val hostViewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame())),
            athleteGroupGateway(), hostAttendance, FakeAthleteGateway(),
        )
        assertFalse(hostViewModel.state.value.confirmedRoster.first().guest?.canRemove == true)

        val organizerAttendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed, memberId = "someone-else")),
            rosterResult = SaqzResult.Success(roster),
        )
        val organizerViewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame())),
            FakeGroupGateway(), organizerAttendance, FakeAthleteGateway(),
        )
        assertTrue(organizerViewModel.state.value.confirmedRoster.first().guest?.canRemove == true)
    }

    @Test
    fun organizerRemovingAGuestNamesTheHostWhoWillBeNotified() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("host-1", "Convidado A", guestSeq = 1, hostDisplayName = "Host Um")),
            waitlisted = emptyList(),
        )
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed, memberId = "someone-else")),
            rosterResult = SaqzResult.Success(roster),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.RequestRemoveGuest(viewModel.state.value.confirmedRoster.first().id))

        assertEquals("Host Um", viewModel.state.value.guest.removal?.notifiedHostName)
    }

    @Test
    fun hostRemovingOwnGuestHasNoNoticeToTheHost() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("host-1", "Convidado A", guestSeq = 1, hostDisplayName = "Host Um")),
            waitlisted = emptyList(),
        )
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed, memberId = "host-1")),
            rosterResult = SaqzResult.Success(roster),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            athleteGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.RequestRemoveGuest(viewModel.state.value.confirmedRoster.first().id))

        assertNull(viewModel.state.value.guest.removal?.notifiedHostName)
    }

    @Test
    fun confirmRemoveCallsTheGatewayWithHostAndSeq() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("host-1", "Convidado A", guestSeq = 1, hostDisplayName = "Host Um")),
            waitlisted = emptyList(),
        )
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed, memberId = "host-1")),
            rosterResult = SaqzResult.Success(roster),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            athleteGroupGateway(), attendance, FakeAthleteGateway(),
        )

        val rowId = viewModel.state.value.confirmedRoster.first().id
        viewModel.onIntent(GameDetailIntent.RequestRemoveGuest(rowId))
        viewModel.onIntent(GameDetailIntent.ConfirmRemoveGuest)

        assertEquals(1, attendance.removeGuestCalls)
        assertEquals("host-1", attendance.lastRemoveGuestHostId)
        assertEquals(1, attendance.lastRemoveGuestSeq)
        assertNull(viewModel.state.value.guest.removal)
        assertEquals("Convidado A", viewModel.state.value.guest.noticeName)
        assertFalse(viewModel.state.value.guest.noticeJoined)
        assertEquals(2, attendance.rosterCalls)
    }

    @Test
    fun removeFailureKeepsTheConfirmationOpen() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("host-1", "Convidado A", guestSeq = 1, hostDisplayName = "Host Um")),
            waitlisted = emptyList(),
        )
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed, memberId = "host-1")),
            rosterResult = SaqzResult.Success(roster),
            removeGuestResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server)),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            athleteGroupGateway(), attendance, FakeAthleteGateway(),
        )

        val rowId = viewModel.state.value.confirmedRoster.first().id
        viewModel.onIntent(GameDetailIntent.RequestRemoveGuest(rowId))
        viewModel.onIntent(GameDetailIntent.ConfirmRemoveGuest)

        assertNotNull(viewModel.state.value.guest.removal)
        assertFalse(viewModel.state.value.guest.removing)
        assertTrue(viewModel.state.value.guest.removeFailed)
    }

    @Test
    fun promoteGuestSendsGuestSeqAndUsesTheRowId() = runTest {
        val roster = AttendanceRoster(
            confirmed = emptyList(),
            waitlisted = listOf(
                AttendanceRosterMember("host-1", "Convidado A", waitlistPosition = 1, guestSeq = 1, hostDisplayName = "Host Um"),
            ),
        )
        val attendance = FakeAttendanceGateway(rosterResult = SaqzResult.Success(roster))
        val promotion = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        attendance.promoteDeferred = promotion
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))), manualGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.Promote("host-1", "Escolha do organizador", 1))

        assertEquals("host-1#1", viewModel.state.value.promotingMemberId)
        assertEquals(1, attendance.lastPromotionCommand?.guestSeq)
        assertEquals("host-1", attendance.lastPromotionCommand?.memberId)
        assertTrue(viewModel.state.value.waitlist.none { it.id == "host-1#1" })

        promotion.complete(SaqzResult.Success(sampleVersionedAttendanceMutation()))

        assertNull(viewModel.state.value.promotingMemberId)
    }

    // --- Resposta de presença na tela do jogo (jogos depois do próximo só se respondem aqui) ---

    private fun mutation(
        status: AttendanceStatus,
        position: Long? = null,
        confirmed: Int = 9,
    ) = SaqzResult.Success(
        VersionedAttendanceMutation(
            value = AttendanceMutation(
                attendance = AttendanceEntry("me", status, position, 2),
                promotedCount = 0,
                detail = AttendanceDetail(null, confirmed, 12 - confirmed, 0, 12),
            ),
            version = AttendanceVersionToken("etag-2"),
        ),
    )

    private fun responder(
        attendance: FakeAttendanceGateway = FakeAttendanceGateway(),
        game: br.com.saqz.groups.domain.game.Game = openGame(),
        athletes: FakeAthleteGateway = FakeAthleteGateway(),
        groupGateway: FakeGroupGateway = FakeGroupGateway(),
        notifications: FakeNativeNotifications? = null,
    ) = GameDetailViewModel(
        "group-1", "game-1",
        FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(game))),
        groupGateway, attendance, athletes, notifications,
    )

    private fun avulsoProfile(type: AthleteMembershipType) = FakeAthleteGateway(
        ownProfileResult = SaqzResult.Success(
            OwnAthleteProfile(
                "me", "Bruno", null,
                listOf(
                    OwnAthleteMembership(
                        groupId = GroupId("group-1"),
                        groupName = "Vôlei do CERET",
                        role = GroupRole.ATHLETE,
                        position = null,
                        membershipType = type,
                        active = true,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun responseBlockAsksPublishedGamesOnlyToAnyRole() = runTest {
        val athlete = responder(groupGateway = athleteGroupGateway())
        val admin = responder()
        val draft = responder(game = openGame().copy(status = GameStatus.Draft))
        val cancelled = responder(game = openGame().copy(status = GameStatus.Cancelled))
        val completed = responder(game = openGame().copy(status = GameStatus.Completed))

        assertTrue(athlete.state.value.response.visible)
        assertTrue(admin.state.value.response.visible)
        assertNull(athlete.state.value.response.status)
        assertFalse(draft.state.value.response.visible)
        assertFalse(cancelled.state.value.response.visible)
        assertFalse(completed.state.value.response.visible)
    }

    @Test
    fun goingWithRoomConfirmsAndEnablesTheGuestButton() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = mutation(AttendanceStatus.Confirmed))
        val notifications = FakeNativeNotifications()
        val recorded = mutableListOf<Pair<String, Map<String, String>>>()
        SaqzAnalytics.track = { name, params -> recorded += name to params }
        val viewModel = try {
            responder(attendance, notifications = notifications).also {
                assertEquals(GameGuestHint.NeedAnswer, it.state.value.guest.hint)
                assertFalse(it.state.value.guest.enabled)
                it.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
            }
        } finally {
            SaqzAnalytics.reset()
        }

        val state = viewModel.state.value
        assertEquals(AttendanceIntent.Confirm, attendance.lastResponse?.intent)
        assertEquals(AttendanceStatus.Confirmed, state.response.status)
        assertNull(state.response.waitlistKind)
        assertFalse(state.response.responding)
        assertFalse(state.response.failed)
        assertTrue(state.guest.enabled)
        assertEquals(GameGuestHint.Default, state.guest.hint)
        assertEquals(9, state.attendance?.confirmed)
        assertEquals(3, state.header?.availableSpots)
        assertEquals(2, attendance.rosterCalls)
        assertEquals(listOf("game-1"), notifications.dismissed)
        assertEquals(
            listOf("attendance_answered" to mapOf("surface" to "app", "answer" to "confirm")),
            recorded.filter { it.first == "attendance_answered" },
        )
    }

    @Test
    fun goingWithAFullGameLandsInTheWaitlistAndStillEnablesTheGuestButton() = runTest {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(sampleAttendanceDetail().copy(confirmedCount = 12, availableSpots = 0)),
            respondResult = mutation(AttendanceStatus.Waitlisted, position = 3, confirmed = 12),
        )
        val viewModel = responder(attendance, athletes = avulsoProfile(AthleteMembershipType.MENSALISTA))

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        val state = viewModel.state.value
        assertEquals(AttendanceStatus.Waitlisted, state.response.status)
        assertEquals(HomeWaitlistKind.Reserva, state.response.waitlistKind)
        assertEquals(3L, state.response.waitlistPosition)
        assertTrue(state.guest.enabled)
        assertEquals(GameGuestHint.Default, state.guest.hint)
    }

    @Test
    fun anAvulsoUnderMensalistaPriorityWaitsInTheAvulsoList() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = mutation(AttendanceStatus.Waitlisted, position = 1))
        val viewModel = responder(attendance, athletes = avulsoProfile(AthleteMembershipType.AVULSO))

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.response.status)
        assertEquals(HomeWaitlistKind.AvulsoList, viewModel.state.value.response.waitlistKind)
    }

    @Test
    fun theOptimisticStatusFollowsTheSameForecastAsTheGroupHero() = runTest {
        val deferred = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(sampleAttendanceDetail().copy(confirmedCount = 12, availableSpots = 0)),
        ).also { it.respondDeferred = deferred }
        val full = responder(attendance)
        full.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Waitlisted, full.state.value.response.status)
        assertTrue(full.state.value.response.responding)

        val avulsoAttendance = FakeAttendanceGateway().also { it.respondDeferred = deferred }
        val avulso = responder(avulsoAttendance, athletes = avulsoProfile(AthleteMembershipType.AVULSO))
        avulso.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Waitlisted, avulso.state.value.response.status)
        assertEquals(HomeWaitlistKind.AvulsoList, avulso.state.value.response.waitlistKind)

        val roomAttendance = FakeAttendanceGateway().also { it.respondDeferred = deferred }
        val room = responder(roomAttendance)
        room.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Confirmed, room.state.value.response.status)

        val declining = responder(FakeAttendanceGateway().also { it.respondDeferred = deferred })
        declining.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))
        assertEquals(AttendanceStatus.Declined, declining.state.value.response.status)
    }

    @Test
    fun secondVouOrNaoVouWhileTheFirstIsInFlightIsIgnoredAndAnswersAgainOnceItEnds() = runTest {
        val deferred = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway().also { it.respondDeferred = deferred }
        val viewModel = responder(attendance)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))
        assertTrue(viewModel.state.value.response.responding)
        assertEquals(1, attendance.respondCalls)

        deferred.complete(mutation(AttendanceStatus.Confirmed))
        assertFalse(viewModel.state.value.response.responding)
        assertEquals(1, attendance.respondCalls)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))
        assertEquals(2, attendance.respondCalls)
    }

    @Test
    fun notGoingMarksDeclinedAndKeepsTheGuestButtonOffWithTheHint() = runTest {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed)),
            respondResult = mutation(AttendanceStatus.Declined, confirmed = 7),
        )
        val viewModel = responder(attendance)
        assertTrue(viewModel.state.value.guest.enabled)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))

        val state = viewModel.state.value
        assertEquals(AttendanceIntent.Decline, attendance.lastResponse?.intent)
        assertEquals(AttendanceStatus.Declined, state.response.status)
        assertNull(state.response.waitlistKind)
        assertFalse(state.guest.enabled)
        assertEquals(GameGuestHint.NeedAnswer, state.guest.hint)
        assertEquals(7, state.attendance?.confirmed)
    }

    @Test
    fun leavingTheWaitlistIsADeclineAndDropsTheQueueData() = runTest {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(
                sampleAttendanceDetail().copy(
                    ownAttendance = AttendanceEntry("me", AttendanceStatus.Waitlisted, 3L, 1),
                ),
            ),
            respondResult = mutation(AttendanceStatus.Declined),
        )
        val viewModel = responder(attendance)
        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.response.status)
        assertEquals(3L, viewModel.state.value.response.waitlistPosition)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))

        assertEquals(AttendanceStatus.Declined, viewModel.state.value.response.status)
        assertNull(viewModel.state.value.response.waitlistKind)
        assertNull(viewModel.state.value.response.waitlistPosition)
    }

    @Test
    fun theQueuePositionComesFromTheRefreshedRoster() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("confirmed-1", "Ana")),
            waitlisted = listOf(
                AttendanceRosterMember("wait-1", "Caio", 1),
                AttendanceRosterMember("me", "Bruno", 2),
            ),
        )
        val attendance = FakeAttendanceGateway(respondResult = mutation(AttendanceStatus.Waitlisted, position = 9))
        val viewModel = responder(attendance)
        attendance.rosterResult = SaqzResult.Success(roster)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(2L, viewModel.state.value.response.waitlistPosition)
        assertEquals(listOf("wait-1", "me"), viewModel.state.value.waitlist.map { it.id })
        assertEquals("Bruno", viewModel.state.value.waitlist.last().name)
    }

    @Test
    fun aPromotedRosterTurnsTheWaitlistIntoConfirmed() = runTest {
        val roster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("me", "Bruno")),
            waitlisted = emptyList(),
        )
        val attendance = FakeAttendanceGateway(respondResult = mutation(AttendanceStatus.Waitlisted, position = 1))
        val viewModel = responder(attendance)
        attendance.rosterResult = SaqzResult.Success(roster)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.response.status)
        assertNull(viewModel.state.value.response.waitlistPosition)
        assertTrue(viewModel.state.value.confirmedRoster.single().isYou)
    }

    @Test
    fun aFailedRosterAfterTheAnswerKeepsTheScreenAndTheAnswer() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = mutation(AttendanceStatus.Confirmed))
        val viewModel = responder(attendance)
        attendance.rosterResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server))

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.response.status)
        assertTrue(viewModel.state.value.guest.enabled)
    }

    @Test
    fun aFailedAnswerRevertsTheStatusAndFlagsTheFailure() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server)))
        val notifications = FakeNativeNotifications()
        val viewModel = responder(attendance, notifications = notifications)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        val state = viewModel.state.value
        assertNull(state.response.status)
        assertFalse(state.response.responding)
        assertTrue(state.response.failed)
        assertTrue(state.header?.confirmationOpen == true)
        assertFalse(state.guest.enabled)
        assertTrue(notifications.dismissed.isEmpty())
        assertEquals(1, attendance.rosterCalls)

        attendance.respondResult = mutation(AttendanceStatus.Confirmed)
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        assertFalse(viewModel.state.value.response.failed)
        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.response.status)
    }

    @Test
    fun aDeadlineRefusalClosesTheConfirmationAndTheGuestButton() = runTest {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed)),
            respondResult = SaqzResult.Failure(AttendanceError.DeadlinePassed),
        )
        val viewModel = responder(attendance)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))

        val state = viewModel.state.value
        assertEquals(AttendanceStatus.Confirmed, state.response.status)
        assertFalse(state.response.failed, "o prazo fecha as respostas com a frase do prazo, sem o erro genérico")
        assertFalse(state.header?.confirmationOpen == true)
        assertTrue(state.header?.closedByDeadline == true)
        assertTrue(state.header?.responseOpen == true, "o jogo não começou: o selo não vira \"Em andamento\"")
        assertFalse(state.guest.enabled)
        assertEquals(GameGuestHint.Closed, state.guest.hint)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))
        assertEquals(1, attendance.respondCalls)
    }

    @Test
    fun aFrozenGameClosesTheConfirmationWithoutAFailureWarning() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.Frozen))
        val viewModel = responder(attendance)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        assertFalse(viewModel.state.value.response.failed)
        assertNull(viewModel.state.value.response.status)
        assertFalse(viewModel.state.value.header?.confirmationOpen == true)
        assertFalse(viewModel.state.value.header?.responseOpen == true, "jogo começado é \"Em andamento\"")
        assertFalse(viewModel.state.value.header?.closedByDeadline == true)
        assertEquals(GameGuestHint.Closed, viewModel.state.value.guest.hint)
    }

    // ---- Backend v.0.0.17: depois do prazo, "Vou"/"Não vou" voltam 409 ATTENDANCE_DEADLINE_PASSED ----

    /** O prazo venceu e o jogo, amanhã, não começou: no backend novo o "Vou" ainda vale e entra pela espera. */
    private fun lateGame() = sampleGame().copy(
        confirmationDeadline = "2020-01-01T12:00:00-03:00",
        startsAt = "2030-01-01T20:00:00-03:00",
    )

    @Test
    fun aLateAnswerRefusedForTheDeadlineClosesTheAnswersWithoutSayingTheGameStarted() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.DeadlinePassed))
        val viewModel = responder(attendance, game = lateGame())
        val before = assertNotNull(viewModel.state.value.header)
        assertFalse(before.confirmationOpen)
        assertTrue(before.canRespond, "backend novo: depois do prazo o Vou entra pela espera")
        assertFalse(before.closedByDeadline)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        val state = viewModel.state.value
        val header = assertNotNull(state.header)
        assertTrue(header.closedByDeadline)
        assertTrue(header.responseOpen, "sem \"Em andamento\": o jogo não começou")
        assertFalse(header.confirmationOpen)
        assertFalse(header.canRespond)
        assertNull(state.response.status, "a previsão otimista volta atrás")
        assertFalse(state.response.failed, "sem o erro genérico")
        assertFalse(state.response.responding)
        assertEquals(GameGuestHint.Closed, state.guest.hint)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))
        assertEquals(1, attendance.respondCalls, "fechado, o toque seguinte nem sai do aparelho")
    }

    @Test
    fun theDeadlineClosureSurvivesAReloadOfTheGame() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.DeadlinePassed))
        val viewModel = responder(attendance, game = lateGame())
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        viewModel.onIntent(GameDetailIntent.Retry)

        val header = assertNotNull(viewModel.state.value.header)
        assertTrue(header.closedByDeadline, "a carga reconstrói o cabeçalho; o servidor já disse que o prazo fechou")
        assertFalse(header.canRespond)
        assertFalse(header.confirmationOpen)
    }

    @Test
    fun theCurrentBackendTakesTheLateAnswerIntoTheWaitlistAndNothingCloses() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = mutation(AttendanceStatus.Waitlisted, position = 1))
        val viewModel = responder(attendance, game = lateGame())

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        val state = viewModel.state.value
        assertEquals(AttendanceStatus.Waitlisted, state.response.status)
        assertFalse(state.header?.closedByDeadline == true)
        assertTrue(state.header?.responseOpen == true)
        assertFalse(state.response.failed)
    }

    @Test
    fun respondIsIgnoredOnDraftCancelledAndClosedGames() = runTest {
        listOf(GameStatus.Draft, GameStatus.Cancelled, GameStatus.Completed).forEach { status ->
            val attendance = FakeAttendanceGateway()
            val viewModel = responder(attendance, game = openGame().copy(status = status))

            viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

            assertEquals(0, attendance.respondCalls)
            assertNull(viewModel.state.value.response.status)
        }
        val closedGame = FakeAttendanceGateway()
        val closed = responder(closedGame, game = sampleGame())

        closed.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(0, closedGame.respondCalls)
        assertNull(closed.state.value.response.status)
        assertFalse(closed.state.value.response.responding)
    }

    @Test
    fun respondChecksTheClockAgainBeforeCalling() = runTest {
        val attendance = FakeAttendanceGateway(readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed)))
        val soon = (Clock.System.now() + 2.seconds).toString()
        val viewModel = responder(attendance, game = openGame().copy(confirmationDeadline = soon, startsAt = soon))
        assertTrue(viewModel.state.value.header?.confirmationOpen == true)
        assertTrue(viewModel.state.value.guest.enabled)

        withContext(Dispatchers.Default) { delay(2.5.seconds) }
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))

        assertEquals(0, attendance.respondCalls)
        assertFalse(viewModel.state.value.header?.confirmationOpen == true)
        assertFalse(viewModel.state.value.guest.enabled)
        assertEquals(GameGuestHint.Closed, viewModel.state.value.guest.hint)
        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.response.status)
        assertFalse(viewModel.state.value.response.failed)
    }

    @Test
    fun aSecondAnswerWhileOneIsInFlightIsIgnored() = runTest {
        val deferred = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway().also { it.respondDeferred = deferred }
        val viewModel = responder(attendance)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        assertTrue(viewModel.state.value.response.responding)
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))

        assertEquals(1, attendance.respondCalls)
        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.response.status)

        deferred.complete(mutation(AttendanceStatus.Confirmed))

        assertFalse(viewModel.state.value.response.responding)
        assertEquals(1, attendance.respondCalls)
    }

    @Test
    fun someoneAlreadyInTheQueueCannotSendGoingAgainButCanLeave() = runTest {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(
                sampleAttendanceDetail().copy(ownAttendance = AttendanceEntry("me", AttendanceStatus.Waitlisted, 1L, 1)),
            ),
            respondResult = mutation(AttendanceStatus.Declined),
        )
        val viewModel = responder(attendance)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(0, attendance.respondCalls)
        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.response.status)

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))

        assertEquals(1, attendance.respondCalls)
        assertEquals(AttendanceStatus.Declined, viewModel.state.value.response.status)
    }

    @Test
    fun refreshWaitsWhileAnAnswerIsInFlight() = runTest {
        val deferred = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway().also { it.respondDeferred = deferred }
        val viewModel = responder(attendance)
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))
        val readsBefore = attendance.readCalls

        viewModel.onIntent(GameDetailIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GameDetailIntent.Refresh)

        assertEquals(readsBefore, attendance.readCalls)
        assertTrue(viewModel.state.value.response.responding)
    }

    @Test
    fun anAnswerThatArrivesAfterAFullReloadIsIgnored() = runTest {
        val deferred = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway().also { it.respondDeferred = deferred }
        val viewModel = responder(attendance)
        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Confirm))

        viewModel.onIntent(GameDetailIntent.Retry)
        deferred.complete(mutation(AttendanceStatus.Confirmed))

        // A recarga trouxe o servidor como ele estava (sem resposta) e a resposta velha não a atropela.
        assertNull(viewModel.state.value.response.status)
        assertFalse(viewModel.state.value.response.responding)
        assertEquals(2, attendance.rosterCalls)
    }

    @Test
    fun staleGuestResultAfterAReloadIsIgnored() = runTest {
        val attendance = FakeAttendanceGateway(readResult = SaqzResult.Success(goingDetail(AttendanceStatus.Confirmed)))
        val addGuestDeferred = CompletableDeferred<SaqzResult<AttendanceRosterMember, AttendanceError>>()
        attendance.addGuestDeferred = addGuestDeferred
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(openGame()))),
            FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.OpenGuestSheet)
        viewModel.onIntent(GameDetailIntent.UpdateGuestName("Rafa Moreira"))
        viewModel.onIntent(GameDetailIntent.SubmitGuest)
        assertTrue(viewModel.state.value.guest.adding)

        viewModel.onIntent(GameDetailIntent.Retry)

        addGuestDeferred.complete(SaqzResult.Success(AttendanceRosterMember("host-1", "Rafa Moreira", null, 1, null)))

        assertNull(viewModel.state.value.guest.noticeName)
    }
}

internal class FakeAttendanceGateway(
    var readResult: SaqzResult<AttendanceDetail, AttendanceError> = SaqzResult.Success(sampleAttendanceDetail()),
    var rosterResult: SaqzResult<AttendanceRoster, AttendanceError> = SaqzResult.Success(sampleAttendanceRoster()),
    var respondResult: SaqzResult<VersionedAttendanceMutation, AttendanceError> = SaqzResult.Success(
        VersionedAttendanceMutation(
            value = AttendanceMutation(
                attendance = AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1),
                promotedCount = 0,
                detail = AttendanceDetail(null, 5, 7, 0, 12),
            ),
            version = AttendanceVersionToken("etag-1"),
        ),
    ),
    var promoteResult: SaqzResult<VersionedAttendanceMutation, AttendanceError> =
        SaqzResult.Success(sampleVersionedAttendanceMutation()),
    var capacityResult: SaqzResult<VersionedAttendanceCapacity, AttendanceError> =
        SaqzResult.Success(sampleVersionedAttendanceCapacity()),
    var addGuestResult: SaqzResult<AttendanceRosterMember, AttendanceError> =
        SaqzResult.Success(AttendanceRosterMember("host-1", "Convidado", null, 1, null)),
    var removeGuestResult: SaqzResult<Unit, AttendanceError> = SaqzResult.Success(Unit),
) : AttendanceGateway {
    var readCalls = 0
    var rosterCalls = 0
    var respondCalls = 0
    var promoteCalls = 0
    var capacityCalls = 0
    var overrideCalls = 0
    var lastOverride: OverrideAttendanceCommand? = null
    var overrideResult: SaqzResult<VersionedAttendanceMutation, AttendanceError> =
        SaqzResult.Success(sampleVersionedAttendanceMutation())
    var overrideDeferred: CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>? = null
    var addGuestCalls = 0
    var removeGuestCalls = 0
    var lastResponse: SelfAttendanceCommand? = null
    var lastPromotionCommand: AttendancePromotionCommand? = null
    var lastAddGuestCommand: AddGuestCommand? = null
    var lastRemoveGuestHostId: String? = null
    var lastRemoveGuestSeq: Int? = null
    var promoteDeferred: CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>? = null
    var respondDeferred: CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>? = null
    var capacityDeferred: CompletableDeferred<SaqzResult<VersionedAttendanceCapacity, AttendanceError>>? = null
    var addGuestDeferred: CompletableDeferred<SaqzResult<AttendanceRosterMember, AttendanceError>>? = null
    var removeGuestDeferred: CompletableDeferred<SaqzResult<Unit, AttendanceError>>? = null

    override suspend fun read(groupId: br.com.saqz.domain.GroupId, gameId: String): SaqzResult<AttendanceDetail, AttendanceError> {
        readCalls++
        return readResult
    }

    override suspend fun respond(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        command: SelfAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> {
        respondCalls++
        lastResponse = command
        return respondDeferred?.await() ?: respondResult
    }

    override suspend fun roster(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
    ): SaqzResult<AttendanceRoster, AttendanceError> {
        rosterCalls++
        return rosterResult
    }

    override suspend fun promote(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        command: AttendancePromotionCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> {
        promoteCalls++
        lastPromotionCommand = command
        return promoteDeferred?.await() ?: promoteResult
    }

    override suspend fun override(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        command: OverrideAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> {
        overrideCalls++
        lastOverride = command
        return overrideDeferred?.await() ?: overrideResult
    }

    override suspend fun capacity(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        version: AttendanceVersionToken,
        command: AttendanceCapacityCommand,
    ): SaqzResult<VersionedAttendanceCapacity, AttendanceError> =
        capacityDeferred?.await() ?: capacityResult

    override suspend fun addGuest(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        command: AddGuestCommand,
    ): SaqzResult<AttendanceRosterMember, AttendanceError> {
        addGuestCalls++
        lastAddGuestCommand = command
        return addGuestDeferred?.await() ?: addGuestResult
    }

    override suspend fun removeGuest(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        hostId: String,
        guestSeq: Int,
    ): SaqzResult<Unit, AttendanceError> {
        removeGuestCalls++
        lastRemoveGuestHostId = hostId
        lastRemoveGuestSeq = guestSeq
        return removeGuestDeferred?.await() ?: removeGuestResult
    }
}
