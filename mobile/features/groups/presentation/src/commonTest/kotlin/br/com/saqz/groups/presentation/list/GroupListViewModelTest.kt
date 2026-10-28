package br.com.saqz.groups.presentation.list

import br.com.saqz.domain.DataError
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.PositionSummary
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleAttendanceDetail
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GroupListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    // Sem plano entitulador: o "+" cai no Fluxo 8, como antes da porta existir.
    private val noPlan = GroupCreationEntitlement { false }

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success loads one card from own memberships and group read`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                br.com.saqz.groups.domain.athlete.OwnAthleteProfile(
                    userId = "me",
                    displayName = "Bruno",
                    phone = null,
                    memberships = listOf(
                        OwnAthleteMembership(
                            groupId = br.com.saqz.domain.GroupId("group-1"),
                            groupName = "Old name",
                            role = GroupRole.ADMIN,
                            position = null,
                            membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA,
                            active = true,
                        ),
                    ),
                ),
            ),
        )
        val viewModel = GroupListViewModel(
            athlete, FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup())), noPlan,
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("group-1"), viewModel.state.value.groups.map { it.id })
        assertEquals("Vôlei do CERET", viewModel.state.value.groups.single().name)
        assertEquals("São Paulo", viewModel.state.value.groups.single().place)
        assertEquals("/api/groups/group-1/photo?v=2", viewModel.state.value.groups.single().photoUrl)
        assertNull(viewModel.state.value.groups.single().nextGame)
    }

    @Test
    fun `submitting a pasted invite link closes the sheet and hands the code over`() = runTest {
        val code = "a".repeat(42) + "A"
        val viewModel = GroupListViewModel(FakeAthleteGateway(), FakeGroupGateway(), noPlan, gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway())

        viewModel.onIntent(GroupListIntent.OpenInviteSheet)
        viewModel.onIntent(GroupListIntent.InviteLinkChanged("https://links.saqz.app/?saqz_invite=$code"))
        viewModel.onIntent(GroupListIntent.SubmitInviteLink)

        assertEquals(GroupListEffect.AcceptInviteCode(code), viewModel.effects.first())
        assertFalse(viewModel.state.value.inviteSheetOpen)
        assertEquals("", viewModel.state.value.inviteLink)
    }

    @Test
    fun `a pasted text without an invite code flags the field and keeps the sheet open`() = runTest {
        val viewModel = GroupListViewModel(FakeAthleteGateway(), FakeGroupGateway(), noPlan, gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway())

        viewModel.onIntent(GroupListIntent.OpenInviteSheet)
        viewModel.onIntent(GroupListIntent.InviteLinkChanged("https://saqz.app/"))
        viewModel.onIntent(GroupListIntent.SubmitInviteLink)

        assertTrue(viewModel.state.value.inviteLinkInvalid)
        assertTrue(viewModel.state.value.inviteSheetOpen)
        viewModel.onIntent(GroupListIntent.CloseInviteSheet)
        assertFalse(viewModel.state.value.inviteSheetOpen)
        assertEquals("", viewModel.state.value.inviteLink)
    }

    @Test
    fun `empty own profile becomes the first access state`() = runTest {
        val viewModel = GroupListViewModel(FakeAthleteGateway(), FakeGroupGateway(), noPlan,
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )

        assertTrue(viewModel.state.value.isEmpty)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `gateway failure is visible and maps forbidden`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Failure(
                br.com.saqz.groups.domain.athlete.AthleteError.DataFailure(DataError.Forbidden),
            ),
        )
        val viewModel = GroupListViewModel(athlete, FakeGroupGateway(), noPlan,
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `group read failure is visible and does not leak the domain error`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                br.com.saqz.groups.domain.athlete.OwnAthleteProfile(
                    "me",
                    "Bruno",
                    null,
                    listOf(
                        OwnAthleteMembership(
                            br.com.saqz.domain.GroupId("group-1"),
                            "Group",
                            GroupRole.ATHLETE,
                            null,
                            br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO,
                            true,
                        ),
                    ),
                ),
            ),
        )
        val gateway = FakeGroupGateway(
            readResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.NotFound)),
        )
        val viewModel = GroupListViewModel(athlete, gateway, noPlan,
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.NotFound, viewModel.state.value.error)
    }

    @Test
    fun `navigation effects remain available after loading`() = runTest {
        val viewModel = GroupListViewModel(FakeAthleteGateway(), FakeGroupGateway(), noPlan,
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )

        viewModel.onIntent(GroupListIntent.OpenGroup("group-1"))
        assertEquals(GroupListEffect.OpenGroup("group-1"), viewModel.effects.first())

        viewModel.onIntent(GroupListIntent.CreateGroup)
        assertEquals(GroupListEffect.OpenPlans, viewModel.effects.first())
    }

    // Com plano ativo e vaga, o "+" de 2n atalha para o formulário 2a.
    @Test
    fun `entitled member creating a group goes straight to the form`() = runTest {
        val viewModel = GroupListViewModel(
            FakeAthleteGateway(), FakeGroupGateway(), GroupCreationEntitlement { true },
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )

        viewModel.onIntent(GroupListIntent.CreateGroup)

        assertEquals(GroupListEffect.OpenCreateGroup, viewModel.effects.first())
    }

    @Test
    fun `creation entry can handle retry without granting group permission`() = runTest {
        val entitlement = object : GroupCreationEntitlement {
            override suspend fun canCreateGroup() = false
            override suspend fun canOpenCreationFlow() = true
        }
        val viewModel = GroupListViewModel(FakeAthleteGateway(), FakeGroupGateway(), entitlement,
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )

        viewModel.onIntent(GroupListIntent.CreateGroup)

        assertEquals(GroupListEffect.OpenCreateGroup, viewModel.effects.first())
    }

    // O segundo toque enquanto a checagem está em voo é descartado — uma consulta, um efeito.
    @Test
    fun `tapping create again while the check is in flight emits exactly one effect`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        var calls = 0
        val viewModel = GroupListViewModel(
            FakeAthleteGateway(),
            FakeGroupGateway(),
            GroupCreationEntitlement { calls++; gate.await() },
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )

        viewModel.onIntent(GroupListIntent.CreateGroup)
        viewModel.onIntent(GroupListIntent.CreateGroup)
        gate.complete(true)

        assertEquals(1, calls)
        assertEquals(GroupListEffect.OpenCreateGroup, viewModel.effects.first())
    }

    @Test
    fun `refresh reloads memberships without returning to the skeleton`() = runTest {
        val athlete = FakeAthleteGateway()
        val viewModel = GroupListViewModel(
            athlete,
            FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup())),
            noPlan,
            gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
        )
        assertTrue(viewModel.state.value.isEmpty)
        assertEquals(1, athlete.ownProfileCalls)

        athlete.ownProfileResult = SaqzResult.Success(
            br.com.saqz.groups.domain.athlete.OwnAthleteProfile(
                userId = "me",
                displayName = "Bruno",
                phone = null,
                memberships = listOf(
                    OwnAthleteMembership(
                        groupId = br.com.saqz.domain.GroupId("group-1"),
                        groupName = "Old name",
                        role = GroupRole.ADMIN,
                        position = null,
                        membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA,
                        active = true,
                    ),
                ),
            ),
        )
        viewModel.onIntent(GroupListIntent.Refresh)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("group-1"), viewModel.state.value.groups.map { it.id })
        assertEquals(2, athlete.ownProfileCalls)
    }

    private fun membershipProfile() = SaqzResult.Success(
        br.com.saqz.groups.domain.athlete.OwnAthleteProfile(
            userId = "me",
            displayName = "Bruno",
            phone = null,
            memberships = listOf(
                OwnAthleteMembership(
                    groupId = br.com.saqz.domain.GroupId("group-1"),
                    groupName = "Old name",
                    role = GroupRole.ADMIN,
                    position = null,
                    membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA,
                    active = true,
                ),
            ),
        ),
    )

    private fun loadedList(athlete: FakeAthleteGateway) = GroupListViewModel(
        athlete,
        FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup())),
        noPlan,
        gameGateway = FakeGameGateway(), attendanceGateway = FakeAttendanceGateway(),
    )

    @Test
    fun `pull refresh keeps the groups on screen and ends not refreshing`() = runTest {
        val athlete = FakeAthleteGateway(ownProfileResult = membershipProfile())
        val viewModel = loadedList(athlete)
        val gate = CompletableDeferred<Unit>()
        athlete.ownProfileGate = gate

        viewModel.onIntent(GroupListIntent.PullRefresh)
        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("group-1"), viewModel.state.value.groups.map { it.id })

        gate.complete(Unit)
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(listOf("group-1"), viewModel.state.value.groups.map { it.id })
    }

    @Test
    fun `pull refresh failure keeps the groups and warns`() = runTest {
        val athlete = FakeAthleteGateway(ownProfileResult = membershipProfile())
        val viewModel = loadedList(athlete)
        athlete.ownProfileResult = SaqzResult.Failure(
            br.com.saqz.groups.domain.athlete.AthleteError.DataFailure(DataError.Connectivity),
        )

        viewModel.onIntent(GroupListIntent.PullRefresh)

        val state = viewModel.state.value
        assertFalse(state.isRefreshing)
        assertFalse(state.loadFailed)
        assertTrue(state.refreshFailed)
        assertEquals(listOf("group-1"), state.groups.map { it.id })
        viewModel.onIntent(GroupListIntent.DismissRefreshFailed)
        assertFalse(viewModel.state.value.refreshFailed)
    }

    @Test
    fun `two pulls in a row fetch once`() = runTest {
        val athlete = FakeAthleteGateway(ownProfileResult = membershipProfile())
        val viewModel = loadedList(athlete)
        val gate = CompletableDeferred<Unit>()
        athlete.ownProfileGate = gate

        viewModel.onIntent(GroupListIntent.PullRefresh)
        viewModel.onIntent(GroupListIntent.PullRefresh)
        gate.complete(Unit)

        assertEquals(2, athlete.ownProfileCalls)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    // ---- GER-003 · ordem, resumo e a folha do "Confirmar"

    private fun card(name: String, attendance: GroupCardAttendance?, startsAt: Long = 0) = GroupCardUi(
        id = name, name = name, isAdmin = false,
        nextGame = attendance?.let {
            GroupCardGameUi(
                gameId = "g-$name", weekday = "QUI", day = "29", time = "19h30", live = false, confirmedCount = 1,
                capacity = 12, attendance = it, startsAtEpochSeconds = startsAt,
            )
        },
    )

    @Test
    fun `order puts confirm first, then the nearest game, no game last, ties by name`() {
        val sorted = listOf(
            card("Zeta sem jogo", null),
            card("Beta vai depois", GroupCardAttendance.Going, startsAt = 300),
            card("Alfa sem jogo", null),
            card("Gama confirma depois", GroupCardAttendance.Pending, startsAt = 500),
            card("Delta vai antes", GroupCardAttendance.Going, startsAt = 100),
            card("Ômega confirma antes", GroupCardAttendance.Pending, startsAt = 200),
            card("Epsilon vai antes", GroupCardAttendance.Waitlisted, startsAt = 100),
        ).sortedForList()
        assertEquals(
            listOf(
                "Ômega confirma antes", "Gama confirma depois", "Delta vai antes", "Epsilon vai antes",
                "Beta vai depois", "Alfa sem jogo", "Zeta sem jogo",
            ),
            sorted.map { it.name },
        )
    }

    @Test
    fun `header summary counts only groups waiting for an answer`() {
        val state = GroupListState(
            isLoading = false,
            groups = listOf(card("a", GroupCardAttendance.Pending), card("b", GroupCardAttendance.Going), card("c", null)),
        )
        assertEquals(1, state.awaitingConfirmation)
        assertEquals(0, state.copy(groups = state.groups.drop(1)).awaitingConfirmation)
    }

    private val confirmNow = Instant.parse("2026-08-01T12:00:00Z")

    private fun confirmList(
        attendance: FakeAttendanceGateway,
        games: FakeGameGateway = FakeGameGateway(SaqzResult.Success(listOf(sampleGame()))),
    ) = GroupListViewModel(
        FakeAthleteGateway(ownProfileResult = membershipProfile()),
        FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup())),
        noPlan,
        gameGateway = games,
        attendanceGateway = attendance,
        now = { confirmNow },
    )

    @Test
    fun `confirm opens the sheet and closing it answers nothing`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = confirmList(attendance)

        viewModel.onIntent(GroupListIntent.OpenConfirm("group-1"))
        assertEquals("group-1", viewModel.state.value.confirmGroup?.id)
        viewModel.onIntent(GroupListIntent.CloseConfirm)

        assertNull(viewModel.state.value.confirmGroupId)
        assertEquals(0, attendance.respondCalls)
        assertEquals(GroupCardAttendance.Pending, viewModel.state.value.groups.single().nextGame?.attendance)
    }

    @Test
    fun `a group already answered does not open the sheet`() = runTest {
        val games = FakeGameGateway(SaqzResult.Success(listOf(sampleGame().copy(ownAttendance = AttendanceStatus.Confirmed))))
        val viewModel = confirmList(FakeAttendanceGateway(), games)

        viewModel.onIntent(GroupListIntent.OpenConfirm("group-1"))

        assertNull(viewModel.state.value.confirmGroupId)
    }

    @Test
    fun `going closes the sheet, swaps the tag and reloads under it`() = runTest {
        val attendance = FakeAttendanceGateway()
        val games = FakeGameGateway(SaqzResult.Success(listOf(sampleGame())))
        val viewModel = confirmList(attendance, games)
        viewModel.onIntent(GroupListIntent.OpenConfirm("group-1"))
        games.listResult = SaqzResult.Success(listOf(sampleGame().copy(ownAttendance = AttendanceStatus.Confirmed)))

        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(AttendanceIntent.Confirm, attendance.lastAttendanceCommand?.intent)
        assertNull(viewModel.state.value.confirmGroupId)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(GroupCardAttendance.Going, viewModel.state.value.groups.single().nextGame?.attendance)
        assertEquals(0, viewModel.state.value.awaitingConfirmation)
    }

    @Test
    fun `going into the waitlist shows waiting and not going shows not going`() = runTest {
        val attendance = FakeAttendanceGateway()
        val waitlisted = sampleVersionedAttendanceMutation().let {
            it.copy(value = it.value.copy(attendance = it.value.attendance.copy(status = AttendanceStatus.Waitlisted)))
        }
        attendance.respondResult = SaqzResult.Success(waitlisted)
        val games = FakeGameGateway(SaqzResult.Success(listOf(sampleGame())))
        val viewModel = confirmList(attendance, games)
        viewModel.onIntent(GroupListIntent.OpenConfirm("group-1"))
        games.listResult = SaqzResult.Success(listOf(sampleGame().copy(ownAttendance = AttendanceStatus.Waitlisted)))
        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(GroupCardAttendance.Waitlisted, viewModel.state.value.groups.single().nextGame?.attendance)

        val declined = sampleVersionedAttendanceMutation().let {
            it.copy(value = it.value.copy(attendance = it.value.attendance.copy(status = AttendanceStatus.Declined)))
        }
        val second = FakeAttendanceGateway(respondResult = SaqzResult.Success(declined))
        val other = confirmList(second, FakeGameGateway(SaqzResult.Success(listOf(sampleGame()))))
        other.onIntent(GroupListIntent.OpenConfirm("group-1"))
        other.onIntent(GroupListIntent.Respond(AttendanceIntent.Decline))
        assertEquals(AttendanceIntent.Decline, second.lastAttendanceCommand?.intent)
        // Não vou não lê a presença: só quem entra pode precisar escolher posição.
        assertEquals(0, second.readCalls)
    }

    @Test
    fun `going in a game with positions opens the position picker and sends nothing`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(
                sampleAttendanceDetail().copy(positions = listOf(PositionSummary(AthletePosition.PONTA, 2, 1, 1, 0))),
            ),
        )
        val viewModel = confirmList(attendance)
        viewModel.onIntent(GroupListIntent.OpenConfirm("group-1"))

        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Confirm))

        assertEquals(GroupListEffect.PickPosition("group-1", "game-1"), viewModel.effects.first())
        assertEquals(0, attendance.respondCalls)
        assertNull(viewModel.state.value.confirmGroupId)
        assertFalse(viewModel.state.value.responding)
    }

    @Test
    fun `offline keeps the sheet and the confirm tag with the home warning`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val viewModel = confirmList(attendance)
        viewModel.onIntent(GroupListIntent.OpenConfirm("group-1"))

        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Decline))

        val state = viewModel.state.value
        assertTrue(state.responseOffline)
        assertEquals("group-1", state.confirmGroupId)
        assertFalse(state.responding)
        assertEquals(GroupCardAttendance.Pending, state.groups.single().nextGame?.attendance)
    }

    @Test
    fun `frozen group shows the plan warning and a general error the generic one`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.SubscriptionRequired)),
        )
        val viewModel = confirmList(attendance)
        viewModel.onIntent(GroupListIntent.OpenConfirm("group-1"))
        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Decline))
        assertTrue(viewModel.state.value.responsePlanPaused)
        assertFalse(viewModel.state.value.responseFailed)

        attendance.respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server))
        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Decline))
        assertTrue(viewModel.state.value.responseFailed)
        assertFalse(viewModel.state.value.responsePlanPaused)
        assertEquals(GroupCardAttendance.Pending, viewModel.state.value.groups.single().nextGame?.attendance)

        // A leitura de antes do "Vou" falhando também não envia nada.
        attendance.detailResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server))
        val calls = attendance.respondCalls
        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(calls, attendance.respondCalls)
        assertTrue(viewModel.state.value.responseFailed)
    }

    @Test
    fun `double tap sends one answer`() = runTest {
        val attendance = FakeAttendanceGateway()
        val pending = CompletableDeferred<SaqzResult<br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation, AttendanceError>>()
        attendance.respondDeferred = pending
        val viewModel = confirmList(attendance)
        viewModel.onIntent(GroupListIntent.OpenConfirm("group-1"))

        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Confirm))
        viewModel.onIntent(GroupListIntent.Respond(AttendanceIntent.Confirm))
        // Com a resposta em voo a folha não fecha.
        viewModel.onIntent(GroupListIntent.CloseConfirm)
        assertEquals("group-1", viewModel.state.value.confirmGroupId)
        pending.complete(SaqzResult.Success(sampleVersionedAttendanceMutation()))

        assertEquals(1, attendance.respondCalls)
        assertEquals(1, attendance.readCalls)
    }
}
