package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.AttendanceMutation
import br.com.saqz.groups.domain.attendance.AutoConfirmationCommand
import br.com.saqz.groups.domain.attendance.AutoConfirmationUpdate
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
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
    fun `uses ordered roster to show member waitlist position`() = runTest {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(
                AttendanceDetail(
                    ownAttendance = AttendanceEntry("me", AttendanceStatus.Waitlisted, 99, 1),
                    confirmedCount = 12,
                    availableSpots = 0,
                    waitlistCount = 4,
                    capacity = 12,
                ),
            ),
            rosterResult = SaqzResult.Success(
                AttendanceRoster(
                    confirmed = emptyList(),
                    waitlisted = listOf(
                        AttendanceRosterMember("other", "Outra pessoa", 1),
                        AttendanceRosterMember("me", "Bruno", 2),
                    ),
                ),
            ),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(), FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        assertEquals(GameDetailResponseStatus.Waitlisted, viewModel.state.value.memberResponse?.status)
        assertEquals(2L, viewModel.state.value.memberResponse?.waitlistPosition)
    }

    @Test
    fun `shows auto confirmation only for mensalista when group enables it`() = runTest {
        val group = sampleVersionedGroup().group.copy(
            gameConfig = sampleVersionedGroup().group.gameConfig.copy(autoConfirmEnabled = true),
        )
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                OwnAthleteProfile(
                    userId = "me",
                    displayName = "Bruno",
                    phone = null,
                    memberships = listOf(
                        OwnAthleteMembership(
                            groupId = br.com.saqz.domain.GroupId("group-1"),
                            groupName = "Vôlei do CERET",
                            role = br.com.saqz.groups.domain.group.GroupRole.ATHLETE,
                            position = null,
                            membershipType = AthleteMembershipType.MENSALISTA,
                            active = true,
                        ),
                    ),
                ),
            ),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(), FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(group))),
            FakeAttendanceGateway(), athlete,
        )

        assertTrue(viewModel.state.value.autoConfirmationVisible)
    }

    @Test
    fun `response failure rolls back optimistic selection`() = runTest {
        val attendance = FakeAttendanceGateway(
            readResult = SaqzResult.Success(
                AttendanceDetail(
                    ownAttendance = AttendanceEntry("me", AttendanceStatus.Confirmed, null, 1),
                    confirmedCount = 1,
                    availableSpots = 11,
                    waitlistCount = 0,
                    capacity = 12,
                ),
            ),
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(), FakeGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.Respond(AttendanceIntent.Decline))

        assertEquals(GameDetailResponseStatus.Confirmed, viewModel.state.value.memberResponse?.status)
        assertTrue(viewModel.state.value.responseFailed)
        assertFalse(viewModel.state.value.responding)
    }

    @Test
    fun `auto confirmation failure rolls back optimistic switch`() = runTest {
        val group = sampleVersionedGroup().group.copy(
            gameConfig = sampleVersionedGroup().group.gameConfig.copy(autoConfirmEnabled = true),
        )
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                OwnAthleteProfile("me", "Bruno", null, listOf(
                    OwnAthleteMembership(
                        br.com.saqz.domain.GroupId("group-1"), "Grupo",
                        br.com.saqz.groups.domain.group.GroupRole.ATHLETE, null,
                        AthleteMembershipType.MENSALISTA, true,
                    ),
                )),
            ),
        )
        val attendance = FakeAttendanceGateway(
            autoConfirmationResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val viewModel = GameDetailViewModel(
            "group-1", "game-1", FakeGameGateway(),
            FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(group))), attendance, athlete,
        )

        viewModel.onIntent(GameDetailIntent.ToggleAutoConfirmation(true))

        assertFalse(viewModel.state.value.autoConfirmationEnabled)
        assertTrue(viewModel.state.value.autoConfirmationFailed)
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

}

private class FakeAttendanceGateway(
    var readResult: SaqzResult<AttendanceDetail, AttendanceError> = SaqzResult.Success(
        AttendanceDetail(
            ownAttendance = null,
            confirmedCount = 4,
            availableSpots = 8,
            waitlistCount = 0,
            capacity = 12,
        ),
    ),
    var rosterResult: SaqzResult<AttendanceRoster, AttendanceError> = SaqzResult.Success(
        AttendanceRoster(emptyList(), emptyList()),
    ),
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
    var autoConfirmationResult: SaqzResult<AutoConfirmationUpdate, AttendanceError> =
        SaqzResult.Success(AutoConfirmationUpdate(false)),
) : AttendanceGateway {
    var respondCalls = 0
    var autoConfirmationCalls = 0
    var lastResponse: SelfAttendanceCommand? = null
    var lastAutoConfirmation: AutoConfirmationCommand? = null

    override suspend fun read(groupId: br.com.saqz.domain.GroupId, gameId: String) = readResult

    override suspend fun respond(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        command: SelfAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> {
        respondCalls++
        lastResponse = command
        return respondResult
    }

    override suspend fun roster(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
    ) = rosterResult

    override suspend fun override(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        command: OverrideAttendanceCommand,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> = error("not used in this screen")

    override suspend fun capacity(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        version: AttendanceVersionToken,
        command: AttendanceCapacityCommand,
    ): SaqzResult<br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity, AttendanceError> =
        error("not used in this screen")

    override suspend fun updateAutoConfirmation(
        groupId: br.com.saqz.domain.GroupId,
        command: AutoConfirmationCommand,
    ): SaqzResult<AutoConfirmationUpdate, AttendanceError> {
        autoConfirmationCalls++
        lastAutoConfirmation = command
        return autoConfirmationResult
    }
}
