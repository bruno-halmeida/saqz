package br.com.saqz.groups.presentation.schedule

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.*
import br.com.saqz.domain.GroupId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import br.com.saqz.groups.domain.group.GroupWeekday as DomainGroupWeekday
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupScheduleViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success loads upcoming games`() = runTest {
        val viewModel = GroupScheduleViewModel(
            "group-1",
            FakeGameGateway(SaqzResult.Success(listOf(sampleGame()))),
            FakeScheduleGateway(),
            todayInZone = { "2026-08-02" },
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("game-1"), viewModel.state.value.upcoming.map { it.id })
        assertEquals("19h30 · Jogo de terça", viewModel.state.value.upcoming.single().label)
        assertEquals("04", viewModel.state.value.upcoming.single().day)
        assertEquals("AGO", viewModel.state.value.upcoming.single().month)
    }

    @Test
    fun `success hydrates schedule configuration from the group snapshot`() = runTest {
        val schedule = defaultSchedule.copy(
            slots = listOf(GroupScheduleSlot(DomainGroupWeekday.TUESDAY, "19:30")),
            durationMinutes = 90,
            confirmationLeadMinutes = 180,
        )
        val viewModel = GroupScheduleViewModel(
            "group-1",
            FakeGameGateway(),
            FakeScheduleGateway(schedule),
        )

        assertTrue(viewModel.state.value.recurring)
        assertEquals(listOf(90), viewModel.state.value.slots.map { it.durationMinutes })
        assertEquals(90, viewModel.state.value.durationMinutes)
        assertEquals(180, viewModel.state.value.confirmationLeadMinutes)
    }

    @Test
    fun `completed e jogos passados ficam fora de proximos`() = runTest {
        val past = sampleGame().copy(id = "past", localDate = "2026-08-01")
        val completed = sampleGame().copy(id = "completed", status = GameStatus.Completed)
        val viewModel = GroupScheduleViewModel(
            "group-1",
            FakeGameGateway(SaqzResult.Success(listOf(past, completed, sampleGame()))),
            FakeScheduleGateway(),
            todayInZone = { "2026-08-02" },
        )

        assertEquals(listOf("game-1"), viewModel.state.value.upcoming.map { it.id })
    }

    @Test
    fun `empty game list leaves the schedule empty`() = runTest {
        val viewModel = GroupScheduleViewModel("group-1", FakeGameGateway(), FakeScheduleGateway())

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.upcoming.isEmpty())
    }

    @Test
    fun `refresh reloads only the upcoming list and never touches the form`() = runTest {
        val gateway = FakeGameGateway(SaqzResult.Success(listOf(sampleGame())))
        val schedules = FakeScheduleGateway()
        val viewModel = GroupScheduleViewModel("group-1", gateway, schedules, todayInZone = { "2026-08-02" })
        val formBefore = viewModel.state.value.copy(upcoming = emptyList())

        gateway.listResult = SaqzResult.Success(emptyList())
        schedules.schedule = defaultSchedule.copy(durationMinutes = 45, confirmationLeadMinutes = 30)
        viewModel.onIntent(GroupScheduleIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GroupScheduleIntent.Refresh)

        assertTrue(viewModel.state.value.upcoming.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(formBefore, viewModel.state.value)
    }

    @Test
    fun `refresh with a pending edit or an open slot sheet is ignored`() = runTest {
        val gateway = FakeGameGateway(SaqzResult.Success(listOf(sampleGame())))
        val viewModel = GroupScheduleViewModel("group-1", gateway, FakeScheduleGateway(), todayInZone = { "2026-08-02" })
        gateway.listResult = SaqzResult.Success(emptyList())

        viewModel.onIntent(GroupScheduleIntent.SelectDuration(60))
        viewModel.onIntent(GroupScheduleIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GroupScheduleIntent.Refresh)
        assertEquals(listOf("game-1"), viewModel.state.value.upcoming.map { it.id })
        assertEquals(60, viewModel.state.value.durationMinutes)

        // De volta ao valor do servidor mas com a folha de horário aberta: continua edição.
        viewModel.onIntent(GroupScheduleIntent.SelectDuration(defaultSchedule.durationMinutes))
        viewModel.onIntent(GroupScheduleIntent.AddSlot)
        viewModel.onIntent(GroupScheduleIntent.Refresh)
        assertEquals(listOf("game-1"), viewModel.state.value.upcoming.map { it.id })
    }

    @Test
    fun `refresh failure keeps the upcoming list and shows no error`() = runTest {
        val gateway = FakeGameGateway(SaqzResult.Success(listOf(sampleGame())))
        val viewModel = GroupScheduleViewModel("group-1", gateway, FakeScheduleGateway(), todayInZone = { "2026-08-02" })
        gateway.listResult = SaqzResult.Failure(GameError.Data(DataError.Forbidden))

        viewModel.onIntent(GroupScheduleIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GroupScheduleIntent.Refresh)

        assertEquals(listOf("game-1"), viewModel.state.value.upcoming.map { it.id })
        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `refresh is ignored while a load is in flight`() = runTest {
        val gateway = FakeGameGateway(SaqzResult.Success(listOf(sampleGame())))
        val pending = CompletableDeferred<SaqzResult<List<br.com.saqz.groups.domain.game.Game>, GameError>>()
        gateway.listDeferred = pending
        val viewModel = GroupScheduleViewModel("group-1", gateway, FakeScheduleGateway(), todayInZone = { "2026-08-02" })

        viewModel.onIntent(GroupScheduleIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GroupScheduleIntent.Refresh)
        assertTrue(viewModel.state.value.isLoading)

        pending.complete(SaqzResult.Success(listOf(sampleGame())))
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("game-1"), viewModel.state.value.upcoming.map { it.id })
    }

    @Test
    fun `retry refreshes the retained schedule after a game is cancelled`() = runTest {
        val gateway = FakeGameGateway(SaqzResult.Success(listOf(sampleGame())))
        val viewModel = GroupScheduleViewModel(
            "group-1",
            gateway,
            FakeScheduleGateway(),
            todayInZone = { "2026-08-02" },
        )

        gateway.listResult = SaqzResult.Success(listOf(sampleGame().copy(status = GameStatus.Cancelled)))
        viewModel.onIntent(GroupScheduleIntent.Retry)

        assertTrue(viewModel.state.value.upcoming.isEmpty())
    }

    @Test
    fun `gateway failure is visible and typed`() = runTest {
        val viewModel = GroupScheduleViewModel(
            "group-1",
            FakeGameGateway(
                SaqzResult.Failure(GameError.Data(DataError.Forbidden)),
            ),
            FakeScheduleGateway(),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `slot editing remains local until save`() = runTest {
        val viewModel = GroupScheduleViewModel(
            "group-1",
            FakeGameGateway(),
            FakeScheduleGateway(),
            GroupScheduleState(isLoading = false, slots = listOf(slot)),
        )

        viewModel.onIntent(GroupScheduleIntent.AddSlot)
        viewModel.onIntent(GroupScheduleIntent.PickDraftDay(GroupWeekday.THURSDAY))
        viewModel.onIntent(GroupScheduleIntent.PickDraftTime(hour = 20, minute = 0))
        viewModel.onIntent(GroupScheduleIntent.ConfirmSlot)

        assertEquals(2, viewModel.state.value.slots.size)
        assertEquals("20:00", viewModel.state.value.slots.last().startTime)
    }

    @Test
    fun `save after a load failure is rejected`() = runTest {
        val viewModel = GroupScheduleViewModel(
            "group-1",
            FakeGameGateway(SaqzResult.Failure(GameError.Data(DataError.Forbidden))),
            FakeScheduleGateway(),
        )

        viewModel.onIntent(GroupScheduleIntent.Save)

        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `navigation effect carries the game id`() = runTest {
        val viewModel = GroupScheduleViewModel("group-1", FakeGameGateway(), FakeScheduleGateway())

        viewModel.onIntent(GroupScheduleIntent.OpenGame("game-1"))

        assertEquals(GroupScheduleEffect.OpenGame("game-1"), viewModel.effects.first())
    }

    @Test
    fun `save persists settings and reopening hydrates the server values`() = runTest {
        val gateway = FakeScheduleGateway()
        val viewModel = GroupScheduleViewModel("group-1", FakeGameGateway(), gateway)
        viewModel.onIntent(GroupScheduleIntent.SelectDuration(90))
        viewModel.onIntent(GroupScheduleIntent.SelectConfirmationLead(720))
        viewModel.onIntent(GroupScheduleIntent.TogglePause)
        viewModel.onIntent(GroupScheduleIntent.Save)
        assertEquals(GroupScheduleEffect.Saved, viewModel.effects.first())
        assertEquals(GroupVersionToken("\"7\""), gateway.savedVersion)
        val reopened = GroupScheduleViewModel("group-1", FakeGameGateway(), gateway)
        assertEquals(90, reopened.state.value.durationMinutes)
        assertEquals(720, reopened.state.value.confirmationLeadMinutes)
        assertTrue(reopened.state.value.isPaused)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `turning recurrence off persists duration without slots or pause`() = runTest {
        val gateway = FakeScheduleGateway()
        val vm = GroupScheduleViewModel("group-1", FakeGameGateway(), gateway)
        vm.onIntent(GroupScheduleIntent.TogglePause)
        vm.onIntent(GroupScheduleIntent.ToggleRecurring(false))
        vm.onIntent(GroupScheduleIntent.SelectDuration(150))
        vm.onIntent(GroupScheduleIntent.Save)
        assertFalse(gateway.schedule.recurring)
        assertFalse(gateway.schedule.paused)
        assertTrue(gateway.schedule.slots.isEmpty())
        assertEquals(150, gateway.schedule.durationMinutes)
    }

    @Test
    fun `outros em horas cheias grava os minutos em todos os horarios`() = runTest {
        val gateway = FakeScheduleGateway()
        val vm = GroupScheduleViewModel("group-1", FakeGameGateway(), gateway)
        vm.onIntent(GroupScheduleIntent.SelectOtherDuration)
        vm.onIntent(GroupScheduleIntent.UpdateDurationText("5"))
        vm.onIntent(GroupScheduleIntent.Save)
        assertEquals(300, gateway.schedule.durationMinutes)
        assertEquals(listOf(300), vm.state.value.slots.map { it.durationMinutes })
    }

    @Test
    fun `outros vazio ou fora de 3 a 8 acusa erro no campo e nao salva`() = runTest {
        listOf("", "2", "9", "3,5", "3.5").forEach { text ->
            val gateway = FakeScheduleGateway()
            val vm = GroupScheduleViewModel("group-1", FakeGameGateway(), gateway)
            vm.onIntent(GroupScheduleIntent.SelectOtherDuration)
            vm.onIntent(GroupScheduleIntent.UpdateDurationText(text))
            vm.onIntent(GroupScheduleIntent.Save)
            assertTrue(vm.state.value.durationError, "\"$text\"")
            assertEquals(0, gateway.writes, "\"$text\"")
            assertEquals(null, vm.state.value.error)
        }
    }

    @Test
    fun `editing the hours clears the duration error`() = runTest {
        val vm = GroupScheduleViewModel("group-1", FakeGameGateway(), FakeScheduleGateway())
        vm.onIntent(GroupScheduleIntent.SelectOtherDuration)
        vm.onIntent(GroupScheduleIntent.Save)
        assertTrue(vm.state.value.durationError)
        vm.onIntent(GroupScheduleIntent.UpdateDurationText("3"))
        assertFalse(vm.state.value.durationError)
    }

    @Test
    fun `failure preserves edits and emits no success`() = runTest {
        val gateway = FakeScheduleGateway().apply { failure = GroupProfileError.Conflict() }
        val vm = GroupScheduleViewModel("group-1", FakeGameGateway(), gateway)
        val effects = mutableListOf<GroupScheduleEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.toList(effects) }
        vm.onIntent(GroupScheduleIntent.SelectDuration(90))
        vm.onIntent(GroupScheduleIntent.Save)
        assertEquals(GroupUiError.Conflict, vm.state.value.error)
        assertEquals(90, vm.state.value.durationMinutes)
        assertFalse(vm.state.value.isSaving)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `pending save blocks duplicate requests and edits`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeScheduleGateway().apply { pending = gate }
        val vm = GroupScheduleViewModel("group-1", FakeGameGateway(), gateway)
        vm.onIntent(GroupScheduleIntent.Save)
        vm.onIntent(GroupScheduleIntent.Save)
        vm.onIntent(GroupScheduleIntent.SelectDuration(60))
        vm.onIntent(GroupScheduleIntent.Retry)
        assertTrue(vm.state.value.isSaving)
        assertEquals(1, gateway.writes)
        assertEquals(120, vm.state.value.durationMinutes)
        gate.complete(Unit)
        assertEquals(GroupScheduleEffect.Saved, vm.effects.first())
    }

    @Test
    fun `recurrence without slots cannot report saved`() = runTest {
        val gateway = FakeScheduleGateway(defaultSchedule.copy(recurring = false, slots = emptyList()))
        val vm = GroupScheduleViewModel("group-1", FakeGameGateway(), gateway)
        vm.onIntent(GroupScheduleIntent.ToggleRecurring(true))
        vm.onIntent(GroupScheduleIntent.Save)
        assertEquals(GroupUiError.Validation, vm.state.value.error)
        assertEquals(0, gateway.writes)
    }

    private val slot = GroupRegularSlotForm(
        weekday = GroupWeekday.TUESDAY,
        startTime = "19:30",
        durationMinutes = 120,
    )
}

private val defaultSchedule = GroupSchedule(
    true, listOf(GroupScheduleSlot(DomainGroupWeekday.TUESDAY, "19:30")), 120, 360, false,
)
private class FakeScheduleGateway(var schedule: GroupSchedule = defaultSchedule) : GroupScheduleGateway {
    var failure: GroupProfileError? = null
    var pending: CompletableDeferred<Unit>? = null
    var writes = 0
    var savedVersion: GroupVersionToken? = null
    override suspend fun readSchedule(groupId: GroupId) =
        SaqzResult.Success(VersionedGroupSchedule(schedule, GroupVersionToken("\"7\"")))
    override suspend fun updateSchedule(
        groupId: GroupId,
        versionToken: GroupVersionToken,
        schedule: GroupSchedule,
    ): SaqzResult<VersionedGroupSchedule, GroupProfileError> {
        writes++
        savedVersion = versionToken
        pending?.await()
        failure?.let { return SaqzResult.Failure(it) }
        this.schedule = schedule
        return SaqzResult.Success(VersionedGroupSchedule(schedule, GroupVersionToken("\"8\"")))
    }
}
