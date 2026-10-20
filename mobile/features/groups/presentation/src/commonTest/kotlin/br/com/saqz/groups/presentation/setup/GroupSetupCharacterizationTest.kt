package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import br.com.saqz.groups.presentation.FakeGroupSystemTimeZonePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Bloco 0 do redesenho da tela de criar grupo: fixa o comportamento de HOJE do ViewModel
 * (estado inicial, antecedência no comando, remoção de horário) antes da troca da tela.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSetupCharacterizationTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun creating(
        profileGateway: FakeGroupProfileGateway = FakeGroupProfileGateway(),
        state: GroupSetupState = GroupSetupState(mode = GroupSetupMode.Create),
    ) = GroupSetupViewModel(
        initialState = state,
        savedState = SavedStateHandle(),
        groupGateway = FakeGroupGateway(),
        profileGateway = profileGateway,
        timeZonePort = FakeGroupSystemTimeZonePort(),
    )

    @Test
    fun `o estado inicial da criacao nasce com publico misto sem recorrencia e com os padroes`() = runTest(mainDispatcher) {
        val state = creating().state.value

        assertEquals(GroupSetupMode.Create, state.mode)
        // Hoje o público já nasce marcado (Misto), assim como o sistema 6-0.
        assertEquals(GroupComposition.MIXED, state.form.composition)
        assertEquals(GroupPlayStyle.SIX_ZERO, state.form.playStyle)
        assertFalse(state.recurring)
        assertEquals(emptyList(), state.form.regularSlots)
        assertEquals(12, state.form.defaultCapacity)
        assertEquals(360, state.form.defaultConfirmationLeadMinutes)
        assertEquals(120, state.durationMinutes)
        assertNull(state.form.modality)
        assertNull(state.form.level)
        assertEquals("", state.form.name)
        assertEquals(emptySet(), state.errors)
        assertNull(state.sheet)
        assertFalse(state.isEditing)
    }

    @Test
    fun `a antecedencia escolhida na criacao vai no comando de criacao`() = runTest(mainDispatcher) {
        val gateway = FakeGroupProfileGateway()
        val viewModel = creating(
            gateway,
            GroupSetupState(
                mode = GroupSetupMode.Create,
                form = GroupSetupDefaults.Form.copy(
                    name = "Vôlei de terça",
                    modality = br.com.saqz.groups.model.GroupModality.COURT_VOLLEYBALL,
                ),
            ),
        )

        viewModel.onIntent(GroupSetupIntent.SelectConfirmationLead(1_440))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(1_440, gateway.lastCreateCommand?.form?.defaultConfirmationLeadMinutes)
    }

    @Test
    fun `remover um horario tira so ele e o comando sai sem ele`() = runTest(mainDispatcher) {
        val tuesday = GroupRegularSlotForm(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120)
        val thursday = GroupRegularSlotForm(weekday = GroupWeekday.THURSDAY, startTime = "20:00", durationMinutes = 120)
        val gateway = FakeGroupProfileGateway()
        val viewModel = creating(
            gateway,
            GroupSetupState(
                mode = GroupSetupMode.Create,
                form = GroupSetupDefaults.Form.copy(
                    name = "Vôlei de terça",
                    modality = br.com.saqz.groups.model.GroupModality.BEACH_VOLLEYBALL,
                    defaultVenue = br.com.saqz.groups.model.GroupVenueForm(name = "Quadra", address = "R. Um, 1"),
                    regularSlots = listOf(tuesday, thursday),
                ),
                recurring = true,
            ),
        )

        viewModel.onIntent(GroupSetupIntent.RemoveSlot(tuesday))
        assertEquals(listOf(thursday), viewModel.state.value.form.regularSlots)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(listOf(GroupWeekday.THURSDAY.name), gateway.lastCreateCommand?.form?.regularSlots?.map { it.weekday.name })
        assertEquals(listOf("20:00"), gateway.lastCreateCommand?.form?.regularSlots?.map { it.startTime })
    }
}
