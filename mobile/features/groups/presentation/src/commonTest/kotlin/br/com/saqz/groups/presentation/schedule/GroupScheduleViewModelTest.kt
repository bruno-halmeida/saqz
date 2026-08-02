package br.com.saqz.groups.presentation.schedule

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
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
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("game-1"), viewModel.state.value.upcoming.map { it.id })
        assertEquals("19:30 · Jogo de terça", viewModel.state.value.upcoming.single().label)
        assertEquals("04", viewModel.state.value.upcoming.single().day)
        assertEquals("AGO", viewModel.state.value.upcoming.single().month)
    }

    @Test
    fun `empty game list leaves the schedule empty`() = runTest {
        val viewModel = GroupScheduleViewModel("group-1", FakeGameGateway())

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.upcoming.isEmpty())
    }

    @Test
    fun `gateway failure is visible and typed`() = runTest {
        val viewModel = GroupScheduleViewModel(
            "group-1",
            FakeGameGateway(
                SaqzResult.Failure(GameError.Data(DataError.Forbidden)),
            ),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `slot editing remains local while the read path is wired`() = runTest {
        val viewModel = GroupScheduleViewModel(
            "group-1",
            FakeGameGateway(),
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
        )

        viewModel.onIntent(GroupScheduleIntent.Save)

        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `navigation effect carries the game id`() = runTest {
        val viewModel = GroupScheduleViewModel("group-1", FakeGameGateway())

        viewModel.onIntent(GroupScheduleIntent.OpenGame("game-1"))

        assertEquals(GroupScheduleEffect.OpenGame("game-1"), viewModel.effects.first())
    }

    private val slot = GroupRegularSlotForm(
        weekday = GroupWeekday.TUESDAY,
        startTime = "19:30",
        durationMinutes = 120,
    )
}
