package br.com.saqz.groups.presentation.details

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupGameConfig
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.presentation.FakeAthleteFinanceGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeFinanceStatementGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleAttendanceDetail
import br.com.saqz.groups.presentation.sampleAttendanceRoster
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation
import br.com.saqz.groups.presentation.sampleVersionedGroup
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GROUP_ID = "group-1"

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success loads the group header and profile details`() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Vôlei do CERET", viewModel.state.value.header?.name)
        assertEquals("Misto · Intermediário", viewModel.state.value.header?.subtitle)
        assertEquals("CERET", viewModel.state.value.venue?.name)
        assertTrue(viewModel.state.value.header?.summaryChips?.isNotEmpty() == true)
        assertFalse(viewModel.state.value.isOwner)
    }

    @Test
    fun `admin details expose cashbox summary from finance gateways`() = runTest {
        val viewModel = viewModel(
            groupGateway = FakeGroupGateway(
                readResult = SaqzResult.Success(
                    sampleVersionedGroup(sampleGroup(timeZone = GroupTimeZone("UTC"))),
                ),
            ),
            statementGateway = FakeFinanceStatementGateway(
                result = SaqzResult.Success(
                    FinanceStatementPage(
                        month = "2026-08",
                        items = emptyList(),
                        summary = FinanceStatementSummary(0L, 0L, 0L, 38_000L),
                        limit = 20,
                        offset = 0,
                        hasMore = false,
                    ),
                ),
            ),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            Charge(
                                id = "monthly-aug-1",
                                groupId = GroupId(GROUP_ID),
                                memberId = "member-1",
                                kind = ChargeKind.Monthly,
                                month = "2026-08",
                                amountCents = 7_000L,
                                dueDate = "2026-08-10",
                                status = ChargeStatus.Pending,
                                version = 1,
                                audit = emptyList(),
                            ),
                            Charge(
                                id = "monthly-aug-2",
                                groupId = GroupId(GROUP_ID),
                                memberId = "member-2",
                                kind = ChargeKind.Monthly,
                                month = "2026-08",
                                amountCents = 7_000L,
                                dueDate = "2026-08-10",
                                status = ChargeStatus.Pending,
                                version = 1,
                                audit = emptyList(),
                            ),
                            Charge(
                                id = "monthly-jul",
                                groupId = GroupId(GROUP_ID),
                                memberId = "member-3",
                                kind = ChargeKind.Monthly,
                                month = "2026-07",
                                amountCents = 7_000L,
                                dueDate = "2026-07-10",
                                status = ChargeStatus.Pending,
                                version = 1,
                                audit = emptyList(),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(viewModel.state.value.isAdmin)
        assertEquals("Saldo R$\u00A0380,00 · 3 mensalidades em aberto", viewModel.state.value.cashbox?.summary)
    }

    @Test
    fun `admin cashbox counts pending monthly charges from previous months`() = runTest {
        val viewModel = viewModel(
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            Charge(
                                id = "monthly-jul",
                                groupId = GroupId(GROUP_ID),
                                memberId = "member-3",
                                kind = ChargeKind.Monthly,
                                month = "2026-07",
                                amountCents = 7_000L,
                                dueDate = "2026-07-10",
                                status = ChargeStatus.Pending,
                                version = 1,
                                audit = emptyList(),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Saldo R$\u00A00,00 · 1 mensalidades em aberto", viewModel.state.value.cashbox?.summary)
    }

    @Test
    fun `admin keeps the cashbox entry when finance summary fails`() = runTest {
        val viewModel = viewModel(
            statementGateway = FakeFinanceStatementGateway(
                result = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
            ),
        )

        assertTrue(viewModel.state.value.isAdmin)
        assertNotNull(viewModel.state.value.cashbox)
        assertNull(viewModel.state.value.cashbox?.summary)
    }

    @Test
    fun `details render before pending finance completes and degrade finance failure`() = runTest {
        val finance = CompletableDeferred<SaqzResult<FinanceStatementPage, FinanceError>>()
        val viewModel = viewModel(
            statementGateway = FakeFinanceStatementGateway(statementDeferred = finance),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Vôlei do CERET", viewModel.state.value.header?.name)
        assertNull(viewModel.state.value.cashbox)

        finance.complete(SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.cashbox)
        assertNull(viewModel.state.value.cashbox?.summary)
    }

    @Test
    fun `athlete reload clears a previously loaded admin cashbox`() = runTest {
        val groupGateway = FakeGroupGateway(
            readResult = SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = GroupRole.OWNER))),
        )
        val viewModel = viewModel(groupGateway = groupGateway)
        assertNotNull(viewModel.state.value.cashbox)

        groupGateway.readResult = SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = GroupRole.ATHLETE)))
        viewModel.onIntent(GroupDetailsIntent.Retry)

        assertFalse(viewModel.state.value.isAdmin)
        assertNull(viewModel.state.value.cashbox)
    }

    @Test
    fun `empty profile still renders a usable header`() = runTest {
        val empty = sampleVersionedGroup(sampleGroup(profile = null))
        val viewModel = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(empty)),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Vôlei do CERET", viewModel.state.value.header?.name)
        assertTrue(viewModel.state.value.header?.summaryChips.isNullOrEmpty())
        assertEquals(null, viewModel.state.value.venue)
    }

    @Test
    fun `detail read usa o snapshot uma unica vez e preserva owner`() = runTest {
        val gateway = FakeGroupGateway(
            readResult = SaqzResult.Success(
                sampleVersionedGroup(
                    sampleGroup(role = br.com.saqz.groups.domain.group.GroupRole.OWNER),
                ),
            ),
        )
        val viewModel = viewModel(groupGateway = gateway)

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isOwner)
        assertEquals(1, gateway.readCalls)
    }

    @Test
    fun `gateway failure is visible and typed`() = runTest {
        val viewModel = viewModel(
            groupGateway = FakeGroupGateway(
                readResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Forbidden)),
            ),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `navigation effects remain available`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupDetailsIntent.ManageMembers)
        assertEquals(GroupDetailsEffect.OpenMembers(GROUP_ID), viewModel.effects.first())

        viewModel.onIntent(GroupDetailsIntent.OpenSchedule)
        assertEquals(GroupDetailsEffect.OpenSchedule(GROUP_ID), viewModel.effects.first())
    }

    @Test
    fun `view game opens the loaded next game`() = runTest {
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
        )

        viewModel.onIntent(GroupDetailsIntent.ViewGame)

        assertEquals(GroupDetailsEffect.OpenGame(GROUP_ID, "game-1"), viewModel.effects.first())
    }

    @Test
    fun `retry loads a game published while the details screen was underneath`() = runTest {
        val gameGateway = FakeGameGateway()
        val viewModel = viewModel(gameGateway = gameGateway)

        assertEquals(null, viewModel.state.value.nextGame)
        gameGateway.listResult = SaqzResult.Success(listOf(sampleGame()))

        viewModel.onIntent(GroupDetailsIntent.Retry)

        assertEquals("game-1", viewModel.state.value.nextGame?.gameId)
    }

    @Test
    fun `next game loads response card and eligible auto confirmation`() = runTest {
        val group = sampleGroup(role = GroupRole.ATHLETE).copy(
            gameConfig = GroupGameConfig(autoConfirmEnabled = true),
        )
        val vm = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(group))),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = FakeAttendanceGateway(
                detailResult = SaqzResult.Success(
                    sampleAttendanceDetail().copy(
                        ownAttendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 1),
                        autoConfirmEnabled = true,
                    ),
                ),
            ),
            athleteGateway = monthlyAthleteGateway(),
        )

        assertEquals("game-1", vm.state.value.nextGame?.gameId)
        assertEquals(GroupDetailsResponseStatus.Confirmed, vm.state.value.memberResponse?.status)
        assertTrue(vm.state.value.autoConfirmationVisible, "switch should be visible")
        assertTrue(vm.state.value.autoConfirmationEnabled, "persisted switch value should load")
        assertEquals(8, vm.state.value.attendance?.going)
        assertEquals(4, vm.state.value.nextGame?.availableSpots)
    }

    @Test
    fun `next game exposes fee availability for day-member notice`() = runTest {
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame().copy(gameFeeCents = null)))),
            athleteGateway = monthlyAthleteGateway(),
        )

        assertFalse(vm.state.value.nextGame?.hasGameFee ?: true)
    }

    @Test
    fun `published games only in the past do not create a next game response card`() = runTest {
        val pastGame = sampleGame().copy(startsAt = "2020-08-04T19:30:00-03:00")
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(pastGame))),
            athleteGateway = monthlyAthleteGateway(),
        )

        assertEquals(null, vm.state.value.nextGame)
        assertEquals(null, vm.state.value.attendance)
        assertEquals(null, vm.state.value.memberResponse)
    }

    @Test
    fun `response reconciles group counters and next game vacancies`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(sampleAttendanceDetail()),
            respondResult = SaqzResult.Success(
                sampleVersionedAttendanceMutation().copy(
                    value = sampleVersionedAttendanceMutation().value.copy(
                        attendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 2),
                        detail = sampleAttendanceDetail().copy(
                            confirmedCount = 9,
                            availableSpots = 3,
                            waitlistCount = 1,
                            declinedCount = 3,
                            pendingCount = 0,
                        ),
                    ),
                ),
            ),
        )
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Success(AttendanceRoster(
                confirmed = listOf(AttendanceRosterMember("promoted", "Promovido")),
                waitlisted = listOf(AttendanceRosterMember("wait-2", "Duda", 1)),
            )),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(br.com.saqz.groups.domain.attendance.AttendanceIntent.Confirm))

        assertEquals(9, vm.state.value.attendance?.going)
        assertEquals(3, vm.state.value.attendance?.notGoing)
        assertEquals(0, vm.state.value.attendance?.pending)
        assertEquals(3, vm.state.value.attendance?.availableSpots)
        assertEquals(3, vm.state.value.nextGame?.availableSpots)
        assertEquals(listOf("Promovido"), vm.state.value.nextGame?.confirmedNames)
    }

    @Test
    fun `decline reconciles fifo promotion in group card`() = runTest {
        val promotedRoster = AttendanceRoster(
            confirmed = listOf(AttendanceRosterMember("wait-1", "Caio")),
            waitlisted = listOf(AttendanceRosterMember("wait-2", "Duda", 1)),
        )
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(
                sampleAttendanceDetail().copy(
                    ownAttendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 1),
                    confirmedCount = 9,
                    availableSpots = 3,
                    waitlistCount = 2,
                    declinedCount = 2,
                    pendingCount = 0,
                ),
            ),
            respondResult = SaqzResult.Success(
                sampleVersionedAttendanceMutation().copy(
                    value = sampleVersionedAttendanceMutation().value.copy(
                        attendance = AttendanceEntry("me", AttendanceStatus.Declined, version = 2),
                        detail = sampleAttendanceDetail().copy(
                            confirmedCount = 9,
                            availableSpots = 3,
                            waitlistCount = 1,
                            declinedCount = 3,
                            pendingCount = 0,
                        ),
                    ),
                ),
            ),
        )
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Success(promotedRoster),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(br.com.saqz.groups.domain.attendance.AttendanceIntent.Decline))

        assertEquals(GroupDetailsResponseStatus.Declined, vm.state.value.memberResponse?.status)
        assertEquals(9, vm.state.value.attendance?.going)
        assertEquals(3, vm.state.value.attendance?.notGoing)
        assertEquals(0, vm.state.value.attendance?.pending)
        assertEquals(3, vm.state.value.nextGame?.availableSpots)
        assertEquals(listOf("Caio"), vm.state.value.nextGame?.confirmedNames)
        assertEquals(2, attendance.rosterCalls)
    }

    @Test
    fun `response failure rolls back optimistic selection`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(
                sampleAttendanceDetail().copy(
                    ownAttendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 1),
                ),
            ),
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline))

        assertEquals(GroupDetailsResponseStatus.Confirmed, vm.state.value.memberResponse?.status)
        assertFalse(vm.state.value.responding)
        assertTrue(vm.state.value.responseFailed)
    }

    @Test
    fun `frozen response closes the response block without retry error`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Failure(AttendanceError.Frozen),
        )
        val vm = viewModel(groupGateway = athleteGroupGateway(), gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))), attendanceGateway = attendance, athleteGateway = monthlyAthleteGateway())

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertFalse(vm.state.value.nextGame?.confirmationOpen ?: true)
        assertFalse(vm.state.value.responseFailed)
    }

    @Test
    fun `auto confirmation failure rolls back optimistic switch`() = runTest {
        val group = sampleGroup(role = GroupRole.ATHLETE).copy(
            gameConfig = GroupGameConfig(autoConfirmEnabled = true),
        )
        val attendance = FakeAttendanceGateway(
            autoConfirmationResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(group))),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )
        attendance.detailResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity))

        vm.onIntent(GroupDetailsIntent.ToggleAutoConfirmation(true))

        assertFalse(vm.state.value.autoConfirmationEnabled)
        assertTrue(vm.state.value.autoConfirmationFailed)
    }

    @Test
    fun `auto confirmation transport failure reconciles a persisted toggle`() = runTest {
        val group = sampleGroup(role = GroupRole.ATHLETE).copy(
            gameConfig = GroupGameConfig(autoConfirmEnabled = true),
        )
        val attendance = FakeAttendanceGateway(
            autoConfirmationResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(group))),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )
        attendance.detailResult = SaqzResult.Success(sampleAttendanceDetail().copy(autoConfirmEnabled = true))

        vm.onIntent(GroupDetailsIntent.ToggleAutoConfirmation(true))

        assertTrue(vm.state.value.autoConfirmationEnabled)
        assertFalse(vm.state.value.autoConfirmationFailed)
        assertFalse(vm.state.value.autoConfirmationUpdating)
        assertEquals(2, attendance.readCalls)
    }

    @Test
    fun `roster failure after response keeps authoritative counts and offers retry`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Success(sampleVersionedAttendanceMutation()),
        )
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
            SaqzResult.Success(AttendanceRoster(confirmed = listOf(AttendanceRosterMember("promoted", "Promovido")), waitlisted = emptyList())),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(9, vm.state.value.attendance?.going)
        assertTrue(vm.state.value.rosterStale)
        assertFalse(vm.state.value.responseFailed)

        vm.onIntent(GroupDetailsIntent.RetryRoster)

        assertFalse(vm.state.value.rosterStale)
        assertEquals(listOf("Promovido"), vm.state.value.nextGame?.confirmedNames)
        assertEquals(3, attendance.rosterCalls)
    }

    @Test
    fun `roster retry refreshes attendance counts with the roster`() = runTest {
        val mutation = sampleVersionedAttendanceMutation().copy(
            value = sampleVersionedAttendanceMutation().value.copy(
                detail = sampleAttendanceDetail().copy(confirmedCount = 9, availableSpots = 3),
            ),
        )
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Success(mutation))
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
            SaqzResult.Success(AttendanceRoster(confirmed = listOf(AttendanceRosterMember("fresh", "Atualizado")), waitlisted = emptyList())),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        attendance.detailResult = SaqzResult.Success(sampleAttendanceDetail().copy(confirmedCount = 10, availableSpots = 2))

        vm.onIntent(GroupDetailsIntent.RetryRoster)

        assertFalse(vm.state.value.rosterStale)
        assertEquals(10, vm.state.value.attendance?.going)
        assertEquals(2, vm.state.value.attendance?.availableSpots)
        assertEquals(10, vm.state.value.nextGame?.confirmedCount)
        assertEquals(2, vm.state.value.nextGame?.availableSpots)
        assertEquals(listOf("Atualizado"), vm.state.value.nextGame?.confirmedNames)
    }

    @Test
    fun `roster retry is ignored while a response is in flight`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Success(sampleVersionedAttendanceMutation()),
        )
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertTrue(vm.state.value.rosterStale)
        attendance.respondDeferred = CompletableDeferred()

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline))
        vm.onIntent(GroupDetailsIntent.RetryRoster)

        assertTrue(vm.state.value.responding)
        assertFalse(vm.state.value.rosterRefreshing)
        assertEquals(2, attendance.rosterCalls)

        attendance.respondDeferred?.complete(SaqzResult.Success(sampleVersionedAttendanceMutation()))
    }

    @Test
    fun `roster retry promotes own waitlisted response to confirmed`() = runTest {
        val mutation = sampleVersionedAttendanceMutation().copy(
            value = sampleVersionedAttendanceMutation().value.copy(
                attendance = AttendanceEntry("me", AttendanceStatus.Waitlisted, waitlistPosition = 2, version = 2),
            ),
        )
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Success(mutation))
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
            SaqzResult.Success(AttendanceRoster(confirmed = listOf(AttendanceRosterMember("me", "Member")), waitlisted = emptyList())),
        )
        val vm = viewModel(groupGateway = athleteGroupGateway(), gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))), attendanceGateway = attendance, athleteGateway = monthlyAthleteGateway())

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        vm.onIntent(GroupDetailsIntent.RetryRoster)

        assertEquals(GroupDetailsResponseStatus.Confirmed, vm.state.value.memberResponse?.status)
        assertEquals(null, vm.state.value.memberResponse?.waitlistPosition)
    }

    @Test
    fun `roster retry reconciles an absent active response as declined`() = runTest {
        val mutation = sampleVersionedAttendanceMutation().copy(
            value = sampleVersionedAttendanceMutation().value.copy(
                attendance = AttendanceEntry("me", AttendanceStatus.Confirmed, version = 2),
            ),
        )
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Success(mutation))
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
            SaqzResult.Success(sampleAttendanceRoster()),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(GroupDetailsResponseStatus.Confirmed, vm.state.value.memberResponse?.status)

        vm.onIntent(GroupDetailsIntent.RetryRoster)

        assertFalse(vm.state.value.rosterStale)
        assertEquals(GroupDetailsResponseStatus.Declined, vm.state.value.memberResponse?.status)
    }

    @Test
    fun `successful roster read reconciles a waitlisted response promoted concurrently`() = runTest {
        val mutation = sampleVersionedAttendanceMutation().copy(
            value = sampleVersionedAttendanceMutation().value.copy(
                attendance = AttendanceEntry("me", AttendanceStatus.Waitlisted, waitlistPosition = 2, version = 2),
            ),
        )
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Success(mutation))
        attendance.rosterResults = mutableListOf(
            SaqzResult.Success(sampleAttendanceRoster()),
            SaqzResult.Success(AttendanceRoster(confirmed = listOf(AttendanceRosterMember("me", "Member")), waitlisted = emptyList())),
        )
        val vm = viewModel(groupGateway = athleteGroupGateway(), gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))), attendanceGateway = attendance, athleteGateway = monthlyAthleteGateway())

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(GroupDetailsResponseStatus.Confirmed, vm.state.value.memberResponse?.status)
        assertEquals(null, vm.state.value.memberResponse?.waitlistPosition)
    }

    @Test
    fun `own profile failure blocks response until retry loads membership`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            athleteGateway = athlete,
        )

        assertTrue(vm.state.value.loadFailed)
        assertEquals(GroupUiError.Network, vm.state.value.error)
        assertEquals(null, vm.state.value.memberResponse)

        athlete.ownProfileResult = monthlyAthleteGateway().ownProfileResult
        vm.onIntent(GroupDetailsIntent.Retry)

        assertFalse(vm.state.value.loadFailed)
        assertEquals(AthleteMembershipType.MENSALISTA, vm.state.value.membershipType)
    }

    @Test
    fun `sem cobranca no grupo a secao nao aparece`() = runTest {
        val viewModel = viewModel(groupGateway = athleteGroupGateway())

        assertNull(viewModel.state.value.ownCharges)
    }

    @Test
    fun `pendentes vem antes do historico com competencia valor e vencimento`() = runTest {
        val viewModel = viewModel(
            groupGateway = athleteGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            ownCharge("paga", month = "2026-07", dueDate = "2026-07-10", status = ChargeStatus.Paid),
                            ownCharge("avulso", kind = ChargeKind.Game, dueDate = "2026-08-28"),
                            ownCharge("mensal", month = "2026-08", dueDate = "2026-08-10"),
                        ),
                    ),
                ),
            ),
        )

        val ownCharges = assertNotNull(viewModel.state.value.ownCharges)
        assertEquals(listOf("mensal", "avulso"), ownCharges.pending.map { it.id })
        assertEquals("Mensalidade · Agosto", ownCharges.pending.first().title)
        assertEquals("R$ 70,00", ownCharges.pending.first().amountLabel)
        assertEquals(OwnChargeStatusUi.Pending, ownCharges.pending.first().status)
        assertEquals("Jogo avulso", ownCharges.pending.last().title)
        assertEquals(listOf("paga"), ownCharges.history.map { it.id })
        assertEquals("Vencimento 10/07", ownCharges.history.single().dueLabel)
        assertEquals(OwnChargeStatusUi.Paid, ownCharges.history.single().status)
    }

    // O fuso é o do grupo, não o do aparelho nem UTC: às 23h de São Paulo ainda é dia 10,
    // e uma cobrança que vence hoje não pode aparecer como vencida.
    @Test
    fun `vencimento usa o fuso de cobranca do grupo e nao UTC`() = runTest {
        val viewModel = viewModel(
            groupGateway = athleteGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            ownCharge("hoje", month = "2026-08", dueDate = "2026-08-10"),
                            ownCharge("ontem", month = "2026-07", dueDate = "2026-08-09"),
                        ),
                    ),
                ),
            ),
            now = GroupNowPort { kotlin.time.Instant.parse("2026-08-11T02:00:00Z") },
        )

        val pending = assertNotNull(viewModel.state.value.ownCharges).pending
        assertEquals("Vence em 10/08", pending.first { it.id == "hoje" }.dueLabel)
        assertEquals("Venceu em 09/08", pending.first { it.id == "ontem" }.dueLabel)
    }

    @Test
    fun `pix do grupo aparece so quando ha pendencia`() = runTest {
        val finance = FakeAthleteFinanceGateway(
            ownChargesResult = SaqzResult.Success(
                ChargeList(listOf(ownCharge("paga", month = "2026-07", status = ChargeStatus.Paid))),
            ),
        )
        val viewModel = viewModel(groupGateway = pixGroupGateway(), athleteFinanceGateway = finance)

        assertNull(viewModel.state.value.ownCharges?.pix)

        finance.ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("mensal", month = "2026-08"))))
        viewModel.onIntent(GroupDetailsIntent.RetryOwnCharges)

        assertEquals("ceret@volei.com.br", viewModel.state.value.ownCharges?.pix?.key)
        assertEquals("Lucas Prado", viewModel.state.value.ownCharges?.pix?.label)
    }

    @Test
    fun `copiar pix emite a chave do grupo`() = runTest {
        val viewModel = viewModel(
            groupGateway = pixGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("mensal", month = "2026-08")))),
            ),
        )

        viewModel.onIntent(GroupDetailsIntent.CopyPix)

        assertEquals(GroupDetailsEffect.CopyPix("ceret@volei.com.br"), viewModel.effects.first())
    }

    @Test
    fun `falha das cobrancas nao derruba a tela e o retry recarrega so a secao`() = runTest {
        val finance = FakeAthleteFinanceGateway(
            ownChargesResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
        )
        val viewModel = viewModel(groupGateway = athleteGroupGateway(), athleteFinanceGateway = finance)

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals("Vôlei do CERET", viewModel.state.value.header?.name)
        assertTrue(viewModel.state.value.ownCharges?.failed == true)

        finance.ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("mensal", month = "2026-08"))))
        viewModel.onIntent(GroupDetailsIntent.RetryOwnCharges)

        assertFalse(viewModel.state.value.ownCharges?.failed ?: true)
        assertEquals(listOf("mensal"), viewModel.state.value.ownCharges?.pending?.map { it.id })
        assertEquals(2, finance.ownChargesCalls)
    }

    @Test
    fun `resposta antiga das cobrancas nao sobrescreve a recarga`() = runTest {
        val stale = CompletableDeferred<SaqzResult<ChargeList, FinanceError>>()
        val finance = FakeAthleteFinanceGateway(ownChargesDeferred = stale)
        val viewModel = viewModel(groupGateway = athleteGroupGateway(), athleteFinanceGateway = finance)

        assertTrue(viewModel.state.value.ownCharges?.isLoading == true)
        finance.ownChargesDeferred = null
        finance.ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("novo", month = "2026-08"))))
        viewModel.onIntent(GroupDetailsIntent.Retry)

        stale.complete(SaqzResult.Success(ChargeList(listOf(ownCharge("velho", month = "2026-07")))))
        advanceUntilIdle()

        assertEquals(listOf("novo"), viewModel.state.value.ownCharges?.pending?.map { it.id })
    }

    private fun ownCharge(
        id: String,
        kind: ChargeKind = ChargeKind.Monthly,
        month: String? = null,
        dueDate: String = "2026-08-10",
        status: ChargeStatus = ChargeStatus.Pending,
    ) = Charge(
        id = id,
        groupId = GroupId(GROUP_ID),
        memberId = "me",
        kind = kind,
        month = month,
        amountCents = 7_000L,
        dueDate = dueDate,
        status = status,
        version = 1,
        audit = emptyList(),
    )

    private fun pixGroupGateway() = FakeGroupGateway(
        readResult = SaqzResult.Success(
            sampleVersionedGroup(
                sampleGroup(role = GroupRole.ATHLETE).let { group ->
                    group.copy(
                        profile = group.profile?.copy(
                            pixKey = "ceret@volei.com.br",
                            pixLabel = "Lucas Prado",
                        ),
                    )
                },
            ),
        ),
    )

    private fun viewModel(
        groupGateway: FakeGroupGateway = FakeGroupGateway(),
        gameGateway: FakeGameGateway = FakeGameGateway(),
        attendanceGateway: FakeAttendanceGateway = FakeAttendanceGateway(),
        athleteGateway: FakeAthleteGateway = FakeAthleteGateway(),
        statementGateway: FakeFinanceStatementGateway = FakeFinanceStatementGateway(),
        organizerFinanceGateway: FakeOrganizerFinanceGateway = FakeOrganizerFinanceGateway(),
        athleteFinanceGateway: FakeAthleteFinanceGateway = FakeAthleteFinanceGateway(),
        now: GroupNowPort = GroupNowPort { kotlin.time.Instant.parse("2026-08-01T00:00:00Z") },
    ) = GroupDetailsViewModel(
        GROUP_ID,
        groupGateway,
        gameGateway,
        attendanceGateway,
        athleteGateway,
        statementGateway,
        organizerFinanceGateway,
        athleteFinanceGateway,
        now,
    )

    private fun athleteGroupGateway() = FakeGroupGateway(
        readResult = SaqzResult.Success(
            sampleVersionedGroup(sampleGroup(role = GroupRole.ATHLETE)),
        ),
    )

    private fun monthlyAthleteGateway() = FakeAthleteGateway(
        ownProfileResult = SaqzResult.Success(
            OwnAthleteProfile(
                userId = "me",
                displayName = "Member",
                phone = null,
                memberships = listOf(
                    OwnAthleteMembership(
                        groupId = br.com.saqz.domain.GroupId(GROUP_ID),
                        groupName = "Vôlei do CERET",
                        role = GroupRole.ATHLETE,
                        position = null,
                        membershipType = AthleteMembershipType.MENSALISTA,
                        active = true,
                    ),
                ),
            ),
        ),
    )
}
