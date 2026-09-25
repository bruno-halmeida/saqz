package br.com.saqz.groups.presentation.details

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceDetail
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
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.domain.membership.GroupDepartureGateway
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupMembershipError
import kotlinx.coroutines.launch
import br.com.saqz.groups.domain.group.GroupGameConfig
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.presentation.FakeAthleteFinanceGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeFinanceStatementGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.presentation.FakeGroupEntryRequestGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleAttendanceDetail
import br.com.saqz.groups.presentation.sampleAttendanceRoster
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleRosterEntry
import br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation
import br.com.saqz.groups.presentation.sampleVersionedGroup
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
    fun `athlete introduction waits for server success and can be dismissed without changing attendance`() = runTest {
        val attendance = FakeAttendanceGateway().apply { respondDeferred = CompletableDeferred() }
        val vm = viewModel(groupGateway = athleteGroupGateway(), attendanceGateway = attendance,
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))))
        assertFalse(vm.state.value.athleteIntroVisible)
        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertTrue(vm.state.value.responding)
        assertFalse(vm.state.value.athleteIntroVisible)
        attendance.respondDeferred!!.complete(SaqzResult.Success(sampleVersionedAttendanceMutation()))
        advanceUntilIdle()
        assertTrue(vm.state.value.athleteIntroVisible)
        vm.onIntent(GroupDetailsIntent.ShareSaqz)
        advanceUntilIdle()
        assertEquals(GroupDetailsEffect.ShareSaqz(
            "Nosso grupo usa o Saqz para organizar jogos, presenças e o caixa. Que tal levar para o seu grupo também? https://saqz.app/"
        ), vm.effects.first())
        val savedResponse = vm.state.value.memberResponse
        vm.onIntent(GroupDetailsIntent.DismissAthleteIntro)
        assertFalse(vm.state.value.athleteIntroVisible)
        assertEquals(savedResponse, vm.state.value.memberResponse)
        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()
        assertFalse(vm.state.value.athleteIntroVisible)
    }

    @Test
    fun `recorded response reports attendance_answered from the app surface`() = runTest {
        val recorded = mutableListOf<Pair<String, Map<String, String>>>()
        SaqzAnalytics.track = { name, params -> recorded += name to params }
        try {
            val vm = viewModel(groupGateway = athleteGroupGateway(), attendanceGateway = FakeAttendanceGateway(),
                gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))))
            vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
            advanceUntilIdle()
        } finally {
            SaqzAnalytics.reset()
        }

        assertEquals(
            listOf("attendance_answered" to mapOf("surface" to "app", "answer" to "confirm")),
            recorded.filter { it.first == "attendance_answered" },
        )
    }

    @Test
    fun `reload hides athlete introduction and does not reset its once per screen guard`() = runTest {
        val vm = viewModel(groupGateway = athleteGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))))
        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertTrue(vm.state.value.athleteIntroVisible)
        vm.onIntent(GroupDetailsIntent.Retry)
        advanceUntilIdle()
        assertFalse(vm.state.value.loadFailed)
        assertFalse(vm.state.value.athleteIntroVisible)
        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()
        assertFalse(vm.state.value.responseFailed)
        assertFalse(vm.state.value.athleteIntroVisible)
    }

    @Test
    fun `a failed later response cannot keep the saved response introduction visible`() = runTest {
        val attendance = FakeAttendanceGateway()
        val vm = viewModel(groupGateway = athleteGroupGateway(), attendanceGateway = attendance,
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))))
        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertTrue(vm.state.value.athleteIntroVisible)
        attendance.respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity))
        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline))
        assertTrue(vm.state.value.responseFailed)
        assertFalse(vm.state.value.athleteIntroVisible)
    }

    @Test
    fun `failed response and organizer response never show athlete introduction`() = runTest {
        val games = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame())))
        val vm = viewModel(groupGateway = athleteGroupGateway(), gameGateway = games,
            attendanceGateway = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.Frozen)))
        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertFalse(vm.state.value.athleteIntroVisible)
        val organizer = viewModel(gameGateway = games)
        organizer.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertFalse(organizer.state.value.athleteIntroVisible)
    }

    @Test
    fun `starter guide advances from creating to sharing and to the completed game settlement`() = runTest {
        val games = FakeGameGateway(listResult = SaqzResult.Success(emptyList()))
        val vm = viewModel(gameGateway = games)
        assertEquals(GroupOnboarding.CreateGame, vm.state.value.onboarding)
        vm.onIntent(GroupDetailsIntent.OnboardingAction)
        assertEquals(GroupDetailsEffect.OpenCreateGame(GROUP_ID), vm.effects.first())
        games.listResult = SaqzResult.Success(listOf(sampleGame()))
        vm.onIntent(GroupDetailsIntent.Retry)
        assertEquals(GroupOnboarding.InviteAthletes(sampleGame().id), vm.state.value.onboarding)
        vm.onIntent(GroupDetailsIntent.OnboardingAction)
        assertEquals(GroupDetailsEffect.OpenInviteLink(GROUP_ID), vm.effects.first())
        vm.onIntent(GroupDetailsIntent.ViewGame)
        assertEquals(GroupDetailsEffect.OpenGame(GROUP_ID, sampleGame().id), vm.effects.first())
        games.listResult = SaqzResult.Success(listOf(sampleGame().copy(status = br.com.saqz.groups.domain.game.GameStatus.Completed)))
        vm.onIntent(GroupDetailsIntent.Retry)
        assertEquals(GroupOnboarding.ReviewFinances(sampleGame().id), vm.state.value.onboarding)
        vm.onIntent(GroupDetailsIntent.OnboardingAction)
        assertEquals(GroupDetailsEffect.OpenSettlement(GROUP_ID, sampleGame().id), vm.effects.first())
    }

    @Test
    fun `pending reload clears the prior organizer step until games arrive`() = runTest {
        val games = FakeGameGateway(listResult = SaqzResult.Success(emptyList()))
        val vm = viewModel(gameGateway = games)
        assertEquals(GroupOnboarding.CreateGame, vm.state.value.onboarding)
        games.listDeferred = CompletableDeferred()
        vm.onIntent(GroupDetailsIntent.Retry)
        assertTrue(vm.state.value.isLoading)
        assertNull(vm.state.value.onboarding)
        games.listDeferred!!.complete(SaqzResult.Success(listOf(sampleGame())))
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
        assertEquals(GroupOnboarding.InviteAthletes(sampleGame().id), vm.state.value.onboarding)
    }

    @Test
    fun `failed reload clears guide and athletes never receive organizer guidance`() = runTest {
        val games = FakeGameGateway(listResult = SaqzResult.Success(emptyList()))
        val vm = viewModel(gameGateway = games)
        games.listResult = SaqzResult.Failure(br.com.saqz.groups.domain.game.GameError.HiddenResource)
        vm.onIntent(GroupDetailsIntent.Retry)
        assertTrue(vm.state.value.loadFailed)
        assertNull(vm.state.value.onboarding)
        assertNull(viewModel(groupGateway = athleteGroupGateway()).state.value.onboarding)
    }

    @Test
    fun `late reminder response cannot describe a different game`() = runTest {
        val response = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.communication.CommunicationMessage, br.com.saqz.groups.domain.communication.CommunicationError>>()
        val gateway = br.com.saqz.groups.presentation.FakeCommunicationGateway().apply { remindBlock = { response.await() } }
        val games = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame())))
        val vm = viewModel(gameGateway = games, communications = gateway)
        vm.onIntent(GroupDetailsIntent.NotifyPending)
        assertTrue(vm.state.value.notifying)
        games.listResult = SaqzResult.Success(listOf(sampleGame().copy(id = "next-game")))
        vm.onIntent(GroupDetailsIntent.Retry)
        assertEquals("next-game", vm.state.value.nextGame?.gameId)
        response.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleCommunicationMessage().copy(recipientCount = 3)))
        advanceUntilIdle()
        assertNull(vm.state.value.notifiedCount)
        assertFalse(vm.state.value.notifying)
    }

    @Test
    fun `completed reminder feedback clears when the next game changes`() = runTest {
        val gateway = br.com.saqz.groups.presentation.FakeCommunicationGateway().apply {
            reminderResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleCommunicationMessage().copy(recipientCount = 3))
        }
        val games = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame())))
        val vm = viewModel(gameGateway = games, communications = gateway)
        vm.onIntent(GroupDetailsIntent.NotifyPending)
        assertEquals("3", vm.state.value.notifiedCount)
        games.listResult = SaqzResult.Success(listOf(sampleGame().copy(id = "next-game")))
        vm.onIntent(GroupDetailsIntent.Retry)
        assertEquals("next-game", vm.state.value.nextGame?.gameId)
        assertNull(vm.state.value.notifiedCount)
    }

    @Test
    fun `pending reminders retry same request and never claim delivery on failure`() = runTest {
        val gateway = br.com.saqz.groups.presentation.FakeCommunicationGateway().apply {
            reminderResult = SaqzResult.Failure(br.com.saqz.groups.domain.communication.CommunicationError(DataError.Timeout))
        }
        val vm = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            communications = gateway,
        )
        vm.onIntent(GroupDetailsIntent.NotifyPending)
        assertTrue(vm.state.value.notificationFailed)
        assertNull(vm.state.value.notifiedCount)
        gateway.reminderResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleCommunicationMessage().copy(recipientCount = 3))
        vm.onIntent(GroupDetailsIntent.NotifyPending)
        assertEquals(2, gateway.reminders.size)
        assertEquals(gateway.reminders[0], gateway.reminders[1])
        assertEquals(GroupId(GROUP_ID), gateway.reminders[0].first)
        assertEquals(sampleGame().id, gateway.reminders[0].second)
        assertEquals("3", vm.state.value.notifiedCount)
        assertFalse(vm.state.value.notificationFailed)
    }

    @Test
    fun `communication shortcuts route to the same group with distinct channels`() = runTest {
        val vm = viewModel()
        vm.onIntent(GroupDetailsIntent.OpenNotices)
        assertEquals(GroupDetailsEffect.OpenThread(GROUP_ID, true), vm.effects.first())
        vm.onIntent(GroupDetailsIntent.OpenChat)
        assertEquals(GroupDetailsEffect.OpenThread(GROUP_ID, false), vm.effects.first())
    }

    @Test
    fun `map effect carries actual group address and native failure is visible`() = runTest {
        val vm = viewModel()
        vm.onIntent(GroupDetailsIntent.OpenVenueMap)
        assertEquals(GroupDetailsEffect.OpenMap(checkNotNull(vm.state.value.venue).address), vm.effects.first())
        vm.onIntent(GroupDetailsIntent.MapOpenFailed)
        assertTrue(vm.state.value.mapFailed)
    }

    @Test
    fun `leave requires confirmation and emits success only after server removes membership`() = runTest {
        val pending = CompletableDeferred<SaqzResult<Unit, GroupMembershipError>>()
        val calls = mutableListOf<GroupId>()
        val effects = mutableListOf<GroupDetailsEffect>()
        val vm = viewModel(departureGateway = GroupDepartureGateway { calls += it; pending.await() })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }

        vm.onIntent(GroupDetailsIntent.ConfirmLeave)
        assertTrue(calls.isEmpty())
        vm.onIntent(GroupDetailsIntent.Leave)
        assertTrue(vm.state.value.confirmingLeave)
        vm.onIntent(GroupDetailsIntent.CancelLeave)
        assertFalse(vm.state.value.confirmingLeave)
        assertTrue(calls.isEmpty())
        vm.onIntent(GroupDetailsIntent.Leave)
        vm.onIntent(GroupDetailsIntent.ConfirmLeave)
        vm.onIntent(GroupDetailsIntent.ConfirmLeave)
        vm.onIntent(GroupDetailsIntent.CancelLeave)
        assertTrue(vm.state.value.leaving)
        assertTrue(vm.state.value.confirmingLeave)
        assertTrue(effects.isEmpty())
        assertEquals(listOf(GroupId(GROUP_ID)), calls)

        pending.complete(SaqzResult.Success(Unit))
        advanceUntilIdle()
        assertEquals(listOf<GroupDetailsEffect>(GroupDetailsEffect.Left), effects)
        assertFalse(vm.state.value.confirmingLeave)
    }

    @Test
    fun `failed leave retains confirmation for retry and owner cannot request it`() = runTest {
        var calls = 0
        val effects = mutableListOf<GroupDetailsEffect>()
        val gateway = GroupDepartureGateway {
            calls++
            SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Forbidden))
        }
        val vm = viewModel(departureGateway = gateway)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        vm.onIntent(GroupDetailsIntent.Leave)
        vm.onIntent(GroupDetailsIntent.ConfirmLeave)
        assertTrue(vm.state.value.leaveFailed)
        assertTrue(vm.state.value.confirmingLeave)
        assertFalse(vm.state.value.leaving)
        vm.onIntent(GroupDetailsIntent.ConfirmLeave)
        assertEquals(2, calls)
        assertTrue(effects.isEmpty(), "Failed departure must never navigate away")

        val owner = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = GroupRole.OWNER)))),
            departureGateway = gateway,
        )
        owner.onIntent(GroupDetailsIntent.Leave)
        owner.onIntent(GroupDetailsIntent.ConfirmLeave)
        assertFalse(owner.state.value.confirmingLeave)
        assertEquals(2, calls)
    }

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
        assertEquals("Saldo R$\u00A0380,00", viewModel.state.value.cashbox?.summary)
        // A contagem saiu da frase do caixa: mora em "Esperando você", só com o mês corrente
        // (grupo em UTC + `now` de 01/08 ⇒ competência 2026-08; a de julho fica de fora).
        assertEquals("2 mensalidades a receber", viewModel.state.value.waiting?.monthly?.title)
    }

    @Test
    fun `admin cashbox summary carries only the balance`() = runTest {
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

        assertEquals("Saldo R$\u00A00,00", viewModel.state.value.cashbox?.summary)
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

        viewModel.onIntent(GroupDetailsIntent.ViewAllMembers)
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
    fun `owner responds attendance like any athlete`() = runTest {
        val group = sampleGroup(role = GroupRole.OWNER).copy(
            gameConfig = GroupGameConfig(autoConfirmEnabled = true),
        )
        val attendance = FakeAttendanceGateway()
        val vm = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(group))),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = monthlyAthleteGateway(),
        )

        assertTrue(vm.state.value.isAdmin, "owner still administers the group")
        assertTrue(vm.state.value.autoConfirmationVisible, "mensalista owner keeps the auto confirmation switch")

        vm.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(1, attendance.respondCalls)
        assertEquals(AttendanceIntent.Confirm, attendance.lastAttendanceCommand?.intent)
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

    @Test
    fun `ownGuestChargeIsTitledWithTheGuestName`() = runTest {
        val viewModel = viewModel(
            groupGateway = athleteGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            ownCharge(
                                "convidado",
                                kind = ChargeKind.Game,
                                dueDate = "2026-08-28",
                                guestDisplayName = "Rafa Moreira",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val ownCharges = assertNotNull(viewModel.state.value.ownCharges)
        assertEquals("Convidado: Rafa Moreira", ownCharges.pending.single().title)
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

        val settled = assertNotNull(viewModel.state.value.ownCharges)
        assertEquals(1, settled.history.size)
        assertNull(settled.pix)

        finance.ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("mensal", month = "2026-08"))))
        viewModel.onIntent(GroupDetailsIntent.RetryOwnCharges)

        val pending = assertNotNull(viewModel.state.value.ownCharges).pix
        assertEquals("ceret@volei.com.br", assertNotNull(pending).key)
        assertEquals("Lucas Prado", pending.label)
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

        val reloaded = assertNotNull(viewModel.state.value.ownCharges)
        assertFalse(reloaded.failed)
        assertEquals(listOf("mensal"), reloaded.pending.map { it.id })
        assertEquals(2, finance.ownChargesCalls)
    }

    // A lista não pagina e o card não é lazy: o histórico longo para nas 6 mais recentes.
    @Test
    fun `historico longo para nas seis cobrancas mais recentes`() = runTest {
        val viewModel = viewModel(
            groupGateway = athleteGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(
                    ChargeList(
                        (1..24).map { index ->
                            val year = 2025 + (index - 1) / 12
                            val key = "$year-${(((index - 1) % 12) + 1).toString().padStart(2, '0')}"
                            ownCharge(
                                id = "paga-$index",
                                month = key,
                                dueDate = "$key-10",
                                status = ChargeStatus.Paid,
                            )
                        },
                    ),
                ),
            ),
        )

        val history = assertNotNull(viewModel.state.value.ownCharges).history
        assertEquals(6, history.size)
        assertEquals("Vencimento 10/12", history.first().dueLabel)
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

    @Test
    fun `next game exposes the hero labels in the game time zone`() = runTest {
        val viewModel = viewModel(gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))))

        val game = assertNotNull(viewModel.state.value.nextGame)
        assertEquals("Terça, 19h30", game.display)
        assertEquals("4 de agosto · CERET", game.meta)
        assertEquals("Rua Canuto Abreu", game.address)
        assertEquals("As confirmações encerram em 04/08 às 12h00.", game.deadlineLine)
        assertEquals("Encerra 04/08 · 12h00", game.deadlineShort)
        assertEquals("Avisamos você se abrir vaga até 12h00 de 04/08.", game.bellLabel)
    }

    @Test
    fun `deadline sentence turns relative on the day of the game`() = runTest {
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            now = GroupNowPort { kotlin.time.Instant.parse("2026-08-04T13:00:00Z") },
        )

        assertEquals("As confirmações encerram hoje às 12h00.", viewModel.state.value.nextGame?.deadlineLine)
    }

    @Test
    fun `map prefers the game address over the group default venue`() = runTest {
        val elsewhere = sampleGame().copy(venue = GameVenue(name = "Arena Mooca", address = "Av. Paes de Barros, 1000"))
        val viewModel = viewModel(gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(elsewhere))))

        viewModel.onIntent(GroupDetailsIntent.OpenVenueMap)

        assertEquals(GroupDetailsEffect.OpenMap("Av. Paes de Barros, 1000"), viewModel.effects.first())
    }

    @Test
    fun `waitlist rows mark the own line by member id and default to the reserve kind`() = runTest {
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = FakeAttendanceGateway(detailResult = SaqzResult.Success(waitlistedDetail("wait-2", 2))),
        )

        assertEquals(
            GroupWaitlistUi(
                kind = HomeWaitlistKind.Reserva,
                rows = listOf(HomeWaitlistRowUi("Caio", 1, false), HomeWaitlistRowUi("Duda", 2, true)),
            ),
            viewModel.state.value.waitlist,
        )
    }

    @Test
    fun `myGuestInTheQueueIsNotMarkedAsSelf`() = runTest {
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = FakeAttendanceGateway(
                detailResult = SaqzResult.Success(waitlistedDetail("wait-2", 1)),
                rosterResult = SaqzResult.Success(
                    AttendanceRoster(
                        confirmed = emptyList(),
                        waitlisted = listOf(
                            AttendanceRosterMember("wait-2", "Duda", 1),
                            AttendanceRosterMember("wait-2", "Rafa Moreira", 2, guestSeq = 1, hostDisplayName = "Duda"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(HomeWaitlistRowUi("Duda", 1, true), HomeWaitlistRowUi("Rafa Moreira", 2, false)),
            viewModel.state.value.waitlist?.rows,
        )
    }

    @Test
    fun `a waitlisted member cannot confirm again but can leave the queue`() = runTest {
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Success(waitlistedDetail("wait-1", 1)))
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(0, attendance.respondCalls)

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline))
        assertEquals(1, attendance.respondCalls)
    }

    @Test
    fun `optimistic response predicts the queue when the game is already full`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(sampleAttendanceDetail().copy(confirmedCount = 12, availableSpots = 0)),
        )
        attendance.respondDeferred = CompletableDeferred()
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(GroupDetailsResponseStatus.Waitlisted, viewModel.state.value.memberResponse?.status)
        assertEquals(HomeWaitlistKind.Reserva, viewModel.state.value.waitlist?.kind)
    }

    @Test
    fun `day member under monthly priority is predicted into the day member list`() = runTest {
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = CompletableDeferred()
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
            athleteGateway = dayMemberAthleteGateway(),
        )

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(GroupDetailsResponseStatus.Waitlisted, viewModel.state.value.memberResponse?.status)
        assertEquals(HomeWaitlistKind.AvulsoList, viewModel.state.value.waitlist?.kind)
    }

    @Test
    fun `failed response restores the previous waitlist`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(waitlistedDetail("wait-1", 1)),
            respondResult = SaqzResult.Failure(AttendanceError.Conflict),
        )
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
        )
        val before = assertNotNull(viewModel.state.value.waitlist)

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline))

        assertEquals(before, viewModel.state.value.waitlist)
        assertTrue(viewModel.state.value.responseFailed)
    }

    @Test
    fun `successful response raises the matching toast and dismiss clears it`() = runTest {
        // O roster padrão do fake deixa "wait-1" na fila; aqui ele precisa estar confirmado para
        // a reconciliação não rebaixar a resposta e o toast ser o de presença confirmada.
        val attendance = FakeAttendanceGateway(
            rosterResult = SaqzResult.Success(
                AttendanceRoster(confirmed = listOf(AttendanceRosterMember("wait-1", "Caio")), waitlisted = emptyList()),
            ),
        )
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(sampleGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(GroupDetailsToast.Confirmed, viewModel.state.value.toast)

        viewModel.onIntent(GroupDetailsIntent.DismissToast)
        assertNull(viewModel.state.value.toast)
    }

    @Test
    fun `copied pix flips the ticket for two seconds and toasts`() = runTest {
        val viewModel = viewModel(
            groupGateway = pixGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("mensal", month = "2026-08")))),
            ),
        )

        viewModel.onIntent(GroupDetailsIntent.CopyPix)

        assertTrue(viewModel.state.value.pixCopied)
        assertEquals(GroupDetailsToast.PixCopied, viewModel.state.value.toast)
        advanceTimeBy(2_001)
        assertFalse(viewModel.state.value.pixCopied)
    }

    @Test
    fun `second copy inside the dwell keeps the copied state until its own dwell ends`() = runTest {
        val viewModel = viewModel(
            groupGateway = pixGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("mensal", month = "2026-08")))),
            ),
        )

        viewModel.onIntent(GroupDetailsIntent.CopyPix)
        advanceTimeBy(1_500)
        viewModel.onIntent(GroupDetailsIntent.CopyPix)
        advanceTimeBy(1_000)

        assertTrue(viewModel.state.value.pixCopied)
        advanceTimeBy(1_001)
        assertFalse(viewModel.state.value.pixCopied)
    }

    @Test
    fun `own charges carry the debt summary of the oldest pending charge`() = runTest {
        val viewModel = viewModel(
            groupGateway = pixGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            ownCharge("avulso", kind = ChargeKind.Game, dueDate = "2026-08-28"),
                            ownCharge("mensal", month = "2026-07", dueDate = "2026-07-10"),
                        ),
                    ),
                ),
            ),
        )

        val debt = assertNotNull(viewModel.state.value.ownCharges?.debt)
        assertEquals("Mensalidade · Julho", debt.eyebrow)
        assertEquals("R$ 140,00", debt.totalLabel)
        assertEquals("Venceu em 10/07", debt.dueLabel)
        assertTrue(debt.overdue)
        assertEquals("2 cobranças em aberto", debt.countLabel)
        assertEquals("Pix de Lucas Prado", debt.receiverLabel)
    }

    @Test
    fun `settled charges expose no debt`() = runTest {
        val viewModel = viewModel(
            groupGateway = athleteGroupGateway(),
            athleteFinanceGateway = FakeAthleteFinanceGateway(
                ownChargesResult = SaqzResult.Success(
                    ChargeList(listOf(ownCharge("paga", month = "2026-07", status = ChargeStatus.Paid))),
                ),
            ),
        )

        assertNull(viewModel.state.value.ownCharges?.debt)
    }

    @Test
    fun `agenda lists the upcoming games after the hero and keeps the drafts the backend sent`() = runTest {
        val games = listOf(
            sampleGame(),
            sampleGame().copy(id = "draft-1", status = GameStatus.Draft, startsAt = "2026-08-11T19:30:00-03:00"),
            sampleGame().copy(
                id = "game-2",
                startsAt = "2026-08-06T19:30:00-03:00",
                ownAttendance = AttendanceStatus.Confirmed,
            ),
        )
        val viewModel = viewModel(gameGateway = FakeGameGateway(listResult = SaqzResult.Success(games)))

        assertEquals("game-1", viewModel.state.value.nextGame?.gameId)
        val agenda = viewModel.state.value.agenda
        assertEquals(listOf("game-2", "draft-1"), agenda.map { it.gameId })
        assertEquals(
            GroupAgendaRowUi(
                gameId = "game-2",
                day = "6",
                month = "AGO",
                title = "Quinta · 19h30",
                meta = "8 de 12 confirmados",
                status = GroupAgendaStatus.Going,
                statusLabel = "Você vai",
                contentDescription = "Quinta, 06/08 às 19h30, Você vai",
            ),
            agenda.first(),
        )
        assertEquals(GroupAgendaStatus.Draft, agenda.last().status)
    }

    @Test
    fun `without a hero every upcoming game stays in the agenda`() = runTest {
        val draft = sampleGame().copy(id = "draft-1", status = GameStatus.Draft)
        val viewModel = viewModel(gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(draft))))

        assertNull(viewModel.state.value.nextGame)
        assertEquals(listOf("draft-1"), viewModel.state.value.agenda.map { it.gameId })
    }

    @Test
    fun `organizer waiting block carries entry requests monthly charges and the game to settle`() = runTest {
        val games = listOf(
            sampleGame().copy(id = "old", status = GameStatus.Completed, startsAt = "2026-07-21T19:30:00-03:00"),
            sampleGame().copy(id = "done", status = GameStatus.Completed, startsAt = "2026-07-28T19:30:00-03:00"),
        )
        val viewModel = viewModel(
            groupGateway = approvalGroupGateway(),
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(games)),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(
                    ChargeList(
                        listOf(
                            ownCharge("m1", month = "2026-08"),
                            ownCharge("m2", month = "2026-08"),
                            dayCharge("g1", "done"),
                            dayCharge("g2", "done"),
                            dayCharge("g3", "old"),
                        ),
                    ),
                ),
            ),
            now = midAugust,
            entryRequests = FakeGroupEntryRequestGateway(
                listResult = SaqzResult.Success(listOf(entryRequest("Ana"), entryRequest("Bia"), entryRequest("Caio"))),
            ),
        )

        val waiting = assertNotNull(viewModel.state.value.waiting)
        assertEquals("3 pedidos para entrar", waiting.entryRequests?.title)
        assertEquals("Ana, Bia e mais 1", waiting.entryRequests?.meta)
        assertEquals(3, waiting.entryRequests?.count)
        assertEquals("2 mensalidades a receber", waiting.monthly?.title)
        assertEquals("R$ 140,00 · AGO", waiting.monthly?.meta)
        assertEquals(
            GroupSettleRowUi(
                gameId = "done",
                title = "Acertar o jogo de 28/07",
                meta = "2 avulsos · R$ 50,00 a receber",
                contentDescription = "Acertar o jogo de 28/07. 2 avulsos · R$ 50,00 a receber",
            ),
            waiting.settle,
        )
    }

    @Test
    fun `monthly row ignores pending charges from other months`() = runTest {
        val viewModel = viewModel(
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(
                    ChargeList(listOf(ownCharge("julho", month = "2026-07", dueDate = "2026-07-10"))),
                ),
            ),
            now = midAugust,
        )

        assertNull(viewModel.state.value.waiting)
    }

    @Test
    fun `settle row steps aside while the onboarding guide reviews the same game`() = runTest {
        val done = sampleGame().copy(id = "done", status = GameStatus.Completed, startsAt = "2026-07-28T19:30:00-03:00")
        val viewModel = viewModel(
            gameGateway = FakeGameGateway(listResult = SaqzResult.Success(listOf(done))),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(ChargeList(listOf(dayCharge("g1", "done")))),
            ),
            now = midAugust,
        )

        assertEquals(GroupOnboarding.ReviewFinances("done"), viewModel.state.value.onboarding)
        assertNull(viewModel.state.value.waiting)
    }

    @Test
    fun `finance failure keeps only the entry requests and never breaks the screen`() = runTest {
        val viewModel = viewModel(
            groupGateway = approvalGroupGateway(),
            statementGateway = FakeFinanceStatementGateway(
                result = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
            ),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
            ),
            now = midAugust,
            entryRequests = FakeGroupEntryRequestGateway(listResult = SaqzResult.Success(listOf(entryRequest("Ana")))),
        )

        assertFalse(viewModel.state.value.loadFailed)
        assertNull(viewModel.state.value.cashbox?.summary)
        val waiting = assertNotNull(viewModel.state.value.waiting)
        assertEquals("Ana", waiting.entryRequests?.meta)
        assertNull(waiting.monthly)
        assertNull(waiting.settle)
    }

    @Test
    fun `entry request failure degrades to the finance rows`() = runTest {
        val viewModel = viewModel(
            groupGateway = approvalGroupGateway(),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("m1", month = "2026-08")))),
            ),
            now = midAugust,
            entryRequests = FakeGroupEntryRequestGateway(
                listResult = SaqzResult.Failure(EntryRequestError.DataFailure(DataError.Connectivity)),
            ),
        )

        assertFalse(viewModel.state.value.loadFailed)
        val waiting = assertNotNull(viewModel.state.value.waiting)
        assertNull(waiting.entryRequests)
        assertEquals(1, waiting.monthly?.count)
    }

    @Test
    fun `entry requests are only fetched when the group requires approval`() = runTest {
        val open = FakeGroupEntryRequestGateway()
        viewModel(entryRequests = open)
        assertEquals(0, open.listCalls)

        val gated = FakeGroupEntryRequestGateway()
        viewModel(groupGateway = approvalGroupGateway(), entryRequests = gated)
        assertEquals(1, gated.listCalls)
    }

    @Test
    fun `athlete never receives the waiting block nor asks for entry requests`() = runTest {
        val entry = FakeGroupEntryRequestGateway(listResult = SaqzResult.Success(listOf(entryRequest("Ana"))))
        val viewModel = viewModel(
            groupGateway = approvalGroupGateway(GroupRole.ATHLETE),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(ChargeList(listOf(ownCharge("m1", month = "2026-08")))),
            ),
            now = midAugust,
            entryRequests = entry,
        )

        assertFalse(viewModel.state.value.isAdmin)
        assertNull(viewModel.state.value.waiting)
        assertEquals(0, entry.listCalls)
    }

    @Test
    fun `stale entry requests from a superseded load are discarded`() = runTest {
        val stale = CompletableDeferred<SaqzResult<List<GroupEntryRequest>, EntryRequestError>>()
        val entry = FakeGroupEntryRequestGateway(listResult = SaqzResult.Success(listOf(entryRequest("Ana"))))
            .apply { listDeferred = stale }
        val viewModel = viewModel(groupGateway = approvalGroupGateway(), entryRequests = entry)
        assertNull(viewModel.state.value.waiting)

        entry.listDeferred = null
        viewModel.onIntent(GroupDetailsIntent.Retry)
        assertEquals(1, viewModel.state.value.waiting?.entryRequests?.count)

        stale.complete(SaqzResult.Success(listOf(entryRequest("Ana"), entryRequest("Bia"), entryRequest("Caio"))))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.waiting?.entryRequests?.count)
    }

    @Test
    fun `people block gets the roster count the first four members and the schedule summary`() = runTest {
        val athletes = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf("Ana", "Bia", "Caio", "Duda", "Edu").mapIndexed { index, name ->
                    sampleRosterEntry(userId = "member-$index").copy(displayName = name)
                },
            ),
        )
        val viewModel = viewModel(groupGateway = athleteGroupGateway(), athleteGateway = athletes)

        assertEquals(5, viewModel.state.value.memberCount)
        assertEquals(
            listOf(
                MemberPreviewUi("member-0", "Ana", ""),
                MemberPreviewUi("member-1", "Bia", ""),
                MemberPreviewUi("member-2", "Caio", ""),
                MemberPreviewUi("member-3", "Duda", ""),
            ),
            viewModel.state.value.memberPreview,
        )
        assertEquals("Terça · 19h30", viewModel.state.value.scheduleSummary)
        assertEquals(AthleteRosterFilter(), athletes.lastRosterFilter)
    }

    @Test
    fun `athlete roster failure keeps the screen up with an empty people block`() = runTest {
        val viewModel = viewModel(
            groupGateway = athleteGroupGateway(),
            athleteGateway = FakeAthleteGateway(
                rosterResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Connectivity)),
            ),
        )

        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(0, viewModel.state.value.memberCount)
        assertTrue(viewModel.state.value.memberPreview.isEmpty())
    }

    @Test
    fun `agenda and settlement rows emit the existing game effects`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupDetailsIntent.OpenAgendaGame("game-2"))
        assertEquals(GroupDetailsEffect.OpenGame(GROUP_ID, "game-2"), viewModel.effects.first())

        viewModel.onIntent(GroupDetailsIntent.OpenSettlement("done"))
        assertEquals(GroupDetailsEffect.OpenSettlement(GROUP_ID, "done"), viewModel.effects.first())
    }

    // 10/08 às 12h em São Paulo: competência 2026-08 sem ambiguidade de fuso.
    private val midAugust = GroupNowPort { kotlin.time.Instant.parse("2026-08-10T15:00:00Z") }

    private fun approvalGroupGateway(role: GroupRole = GroupRole.ADMIN) = FakeGroupGateway(
        readResult = SaqzResult.Success(
            sampleVersionedGroup(sampleGroup(role = role).copy(entryRequiresApproval = true)),
        ),
    )

    private fun entryRequest(name: String) =
        GroupEntryRequest(userId = name.lowercase(), displayName = name, requestedAt = "2026-08-01T12:00:00Z")

    private fun dayCharge(id: String, gameId: String) =
        ownCharge(id, kind = ChargeKind.Game).copy(gameId = gameId, amountCents = 2_500L)

    private fun waitlistedDetail(memberId: String, position: Long): AttendanceDetail = sampleAttendanceDetail().copy(
        ownAttendance = AttendanceEntry(memberId, AttendanceStatus.Waitlisted, position, version = 1),
    )

    private fun dayMemberAthleteGateway() = FakeAthleteGateway(
        ownProfileResult = SaqzResult.Success(
            OwnAthleteProfile(
                userId = "me",
                displayName = "Member",
                phone = null,
                memberships = listOf(
                    OwnAthleteMembership(
                        groupId = GroupId(GROUP_ID),
                        groupName = "Vôlei do CERET",
                        role = GroupRole.ATHLETE,
                        position = null,
                        membershipType = AthleteMembershipType.AVULSO,
                        active = true,
                    ),
                ),
            ),
        ),
    )

    private fun ownCharge(
        id: String,
        kind: ChargeKind = ChargeKind.Monthly,
        month: String? = null,
        dueDate: String = "2026-08-10",
        status: ChargeStatus = ChargeStatus.Pending,
        guestDisplayName: String? = null,
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
        guestDisplayName = guestDisplayName,
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
        departureGateway: GroupDepartureGateway = GroupDepartureGateway { SaqzResult.Success(Unit) },
        communications: br.com.saqz.groups.presentation.FakeCommunicationGateway = br.com.saqz.groups.presentation.FakeCommunicationGateway(),
        entryRequests: FakeGroupEntryRequestGateway = FakeGroupEntryRequestGateway(),
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
        departureGateway,
        communications,
        entryRequests,
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
