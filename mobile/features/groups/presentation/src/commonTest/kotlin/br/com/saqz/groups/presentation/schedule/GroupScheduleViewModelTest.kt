package br.com.saqz.groups.presentation.schedule

import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupScheduleViewModelTest {

    @Test
    fun addSlotOpensSheetAndConfirmAppendsTheDraft() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupScheduleIntent.AddSlot)
        assertEquals(GroupScheduleState.NEW_SLOT, viewModel.state.value.slotSheet)

        viewModel.onIntent(GroupScheduleIntent.PickDraftDay(GroupWeekday.THURSDAY))
        viewModel.onIntent(GroupScheduleIntent.PickDraftTime(hour = 20, minute = 0))
        viewModel.onIntent(GroupScheduleIntent.ConfirmSlot)

        assertEquals(
            listOf(
                slot,
                GroupRegularSlotForm(
                    weekday = GroupWeekday.THURSDAY,
                    startTime = "20:00",
                    durationMinutes = 120,
                ),
            ),
            viewModel.state.value.slots,
        )
        assertNull(viewModel.state.value.slotSheet)
    }

    @Test
    fun confirmWithoutAnOpenSheetChangesNothing() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupScheduleIntent.ConfirmSlot)

        assertEquals(listOf(slot), viewModel.state.value.slots)
    }

    @Test
    fun removeSlotDropsItWithoutTouchingRecurrence() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupScheduleIntent.RemoveSlot(slot))

        // Recorrência ligada sem horário é estado legítimo: não se deriva de slots.
        assertEquals(emptyList(), viewModel.state.value.slots)
        assertTrue(viewModel.state.value.recurring)
    }

    @Test
    fun toggleRecurringKeepsTheSlotsAlreadyChosen() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupScheduleIntent.ToggleRecurring(false))

        assertFalse(viewModel.state.value.recurring)
        assertEquals(listOf(slot), viewModel.state.value.slots)
    }

    @Test
    fun durationAndConfirmationLeadFollowTheSelectedChip() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupScheduleIntent.SelectDuration(90))
        viewModel.onIntent(GroupScheduleIntent.SelectConfirmationLead(1_440))

        assertEquals(90, viewModel.state.value.durationMinutes)
        assertEquals(1_440, viewModel.state.value.confirmationLeadMinutes)
    }

    @Test
    fun durationReachesEverySlotAlreadyCreated() {
        val second = GroupRegularSlotForm(
            weekday = GroupWeekday.THURSDAY,
            startTime = "20:00",
            durationMinutes = 120,
        )
        val viewModel = GroupScheduleViewModel(
            GroupScheduleState(isLoading = false, slots = listOf(slot, second)),
        )

        viewModel.onIntent(GroupScheduleIntent.SelectDuration(90))

        // A duração é do grupo; o form a guarda por slot. Salvar com slots em 120 e o
        // grupo em 90 persistiria estado inconsistente.
        assertEquals(listOf(90, 90), viewModel.state.value.slots.map { it.durationMinutes })
    }

    @Test
    fun pauseAndResumeFlipTheSameFlag() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupScheduleIntent.TogglePause)
        assertTrue(viewModel.state.value.isPaused)

        viewModel.onIntent(GroupScheduleIntent.TogglePause)
        assertFalse(viewModel.state.value.isPaused)
    }

    @Test
    fun saveMarksSaving() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupScheduleIntent.Save)

        assertTrue(viewModel.state.value.isSaving)
    }

    private fun viewModel() = GroupScheduleViewModel(
        GroupScheduleState(isLoading = false, slots = listOf(slot)),
    )

    private val slot = GroupRegularSlotForm(
        weekday = GroupWeekday.TUESDAY,
        startTime = "19:30",
        durationMinutes = 120,
    )
}
