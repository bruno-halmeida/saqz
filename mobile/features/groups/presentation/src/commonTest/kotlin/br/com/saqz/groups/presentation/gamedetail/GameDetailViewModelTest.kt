package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupGameConfig
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleCancelledGame
import br.com.saqz.groups.presentation.sampleVersionedGroup
import br.com.saqz.groups.presentation.sampleVersionedGame
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
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway(), FakeAttendanceGateway(), FakeAthleteGateway())
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, gateway.readCalls)
        assertNotNull(viewModel.state.value.header)
        assertNotNull(viewModel.state.value.attendance)
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
        assertEquals("03/08/2026 · 19:30", header?.confirmationDeadline)
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
            "group-1", "game-1", FakeGameGateway(), FakeGroupGateway(SaqzResult.Success(group)),
            attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.Promote("wait-1", "Escolha do organizador"))
        assertEquals(listOf("wait-2"), viewModel.state.value.waitlist.map { it.id })

        deferred.complete(SaqzResult.Failure(AttendanceError.Data(DataError.Server)))

        assertEquals(listOf("wait-1", "wait-2"), viewModel.state.value.waitlist.map { it.id })
        assertTrue(viewModel.state.value.promotionFailed)
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
            "group-1", "game-1", FakeGameGateway(), manualGroupGateway(), attendance, FakeAthleteGateway(),
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
            "group-1", "game-1", FakeGameGateway(), manualGroupGateway(), attendance, FakeAthleteGateway(),
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

}
