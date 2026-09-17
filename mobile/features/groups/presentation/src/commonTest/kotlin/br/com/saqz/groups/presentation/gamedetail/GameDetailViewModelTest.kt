package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.AddGuestCommand
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AutoConfirmationCommand
import br.com.saqz.groups.domain.attendance.AutoConfirmationUpdate
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

    private fun athleteGroupGateway() = FakeGroupGateway(
        SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = br.com.saqz.groups.domain.group.GroupRole.ATHLETE))),
    )

    private fun openGame() = sampleGame().copy(confirmationDeadline = "2030-01-01T12:00:00-03:00")

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
            "group-1", "game-1", FakeGameGateway(), manualGroupGateway(), attendance, FakeAthleteGateway(),
        )

        viewModel.onIntent(GameDetailIntent.Promote("host-1", "Escolha do organizador", 1))

        assertEquals("host-1#1", viewModel.state.value.promotingMemberId)
        assertEquals(1, attendance.lastPromotionCommand?.guestSeq)
        assertEquals("host-1", attendance.lastPromotionCommand?.memberId)
        assertTrue(viewModel.state.value.waitlist.none { it.id == "host-1#1" })

        promotion.complete(SaqzResult.Success(sampleVersionedAttendanceMutation()))

        assertNull(viewModel.state.value.promotingMemberId)
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

private class FakeAttendanceGateway(
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
    var autoConfirmationResult: SaqzResult<AutoConfirmationUpdate, AttendanceError> =
        SaqzResult.Success(AutoConfirmationUpdate(false)),
    var addGuestResult: SaqzResult<AttendanceRosterMember, AttendanceError> =
        SaqzResult.Success(AttendanceRosterMember("host-1", "Convidado", null, 1, null)),
    var removeGuestResult: SaqzResult<Unit, AttendanceError> = SaqzResult.Success(Unit),
) : AttendanceGateway {
    var readCalls = 0
    var rosterCalls = 0
    var respondCalls = 0
    var promoteCalls = 0
    var capacityCalls = 0
    var autoConfirmationCalls = 0
    var addGuestCalls = 0
    var removeGuestCalls = 0
    var lastResponse: SelfAttendanceCommand? = null
    var lastPromotionCommand: AttendancePromotionCommand? = null
    var lastAutoConfirmation: AutoConfirmationCommand? = null
    var lastAddGuestCommand: AddGuestCommand? = null
    var lastRemoveGuestHostId: String? = null
    var lastRemoveGuestSeq: Int? = null
    var promoteDeferred: CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>? = null
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
        return respondResult
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
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> = error("not used in this screen")

    override suspend fun capacity(
        groupId: br.com.saqz.domain.GroupId,
        gameId: String,
        version: AttendanceVersionToken,
        command: AttendanceCapacityCommand,
    ): SaqzResult<VersionedAttendanceCapacity, AttendanceError> =
        capacityDeferred?.await() ?: capacityResult

    override suspend fun updateAutoConfirmation(
        groupId: br.com.saqz.domain.GroupId,
        command: AutoConfirmationCommand,
    ): SaqzResult<AutoConfirmationUpdate, AttendanceError> {
        autoConfirmationCalls++
        lastAutoConfirmation = command
        return autoConfirmationResult
    }

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
