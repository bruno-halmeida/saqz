package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSetupViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `criar com campo faltando marca o erro e nao avanca para a revisao`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(name = ""))
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(setOf(GroupSetupError.NameRequired), viewModel.state.value.errors)
        assertEquals(GroupSetupStep.Form, viewModel.state.value.step)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `recorrente sem horario produz o 2g`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(regularSlots = emptyList()), recurring = true)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(setOf(GroupSetupError.SlotsRequired), viewModel.state.value.errors)
    }

    @Test
    fun `desligar a recorrencia limpa o erro de horario`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(regularSlots = emptyList()), recurring = true)
        viewModel.onIntent(GroupSetupIntent.Submit)

        viewModel.onIntent(GroupSetupIntent.ToggleRecurring(false))
        runCurrent()

        assertTrue(viewModel.state.value.errors.isEmpty())
    }

    @Test
    fun `criar valido leva para a revisao e a volta preserva o formulario`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertEquals(GroupSetupStep.Review, viewModel.state.value.step)

        viewModel.onIntent(GroupSetupIntent.BackToForm)
        runCurrent()

        assertEquals(GroupSetupStep.Form, viewModel.state.value.step)
        assertEquals(completeForm, viewModel.state.value.form)
    }

    @Test
    fun `confirmar a criacao marca o envio e emite Created`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.ConfirmCreate)
        runCurrent()

        assertTrue(viewModel.state.value.isSaving)
        assertEquals(listOf(GroupSetupEffect.Created(groupId = "")), effects)
    }

    @Test
    fun `editar salva direto sem passar pela revisao`() = runTest(mainDispatcher) {
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"))
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(GroupSetupStep.Form, viewModel.state.value.step)
        assertTrue(viewModel.state.value.isSaving)
        assertEquals(listOf(GroupSetupEffect.Saved), effects)
    }

    @Test
    fun `excluir so vale no modo de edicao`() = runTest(mainDispatcher) {
        val creating = viewModel()
        val creatingEffects = collectEffects(creating)
        creating.onIntent(GroupSetupIntent.ConfirmDelete)
        runCurrent()
        assertTrue(creatingEffects.isEmpty())
        assertTrue(!creating.state.value.isDeleting)

        val editing = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"))
        val editingEffects = collectEffects(editing)
        editing.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.ConfirmDelete))
        editing.onIntent(GroupSetupIntent.ConfirmDelete)
        runCurrent()

        assertTrue(editing.state.value.isDeleting)
        assertNull(editing.state.value.sheet)
        assertEquals(listOf(GroupSetupEffect.Deleted), editingEffects)
    }

    @Test
    fun `salvar um horario usa a duracao do grupo e fecha a folha`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(regularSlots = emptyList()))

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = null)))
        viewModel.onIntent(GroupSetupIntent.PickSlotWeekday(GroupWeekday.FRIDAY))
        viewModel.onIntent(GroupSetupIntent.PickSlotTime(hour = 7, minute = 0))
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        runCurrent()

        assertEquals(
            listOf(
                GroupRegularSlotForm(
                    weekday = GroupWeekday.FRIDAY,
                    startTime = "07:00",
                    durationMinutes = GroupSetupDefaults.DurationMinutes,
                ),
            ),
            viewModel.state.value.form.regularSlots,
        )
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun `abrir a folha de um horario existente carrega o rascunho`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = 0)))
        runCurrent()

        assertEquals(GroupSlotDraft(GroupWeekday.TUESDAY, hour = 19, minute = 30), viewModel.state.value.slotDraft)
    }

    @Test
    fun `desligar a recorrencia esvazia o recorte do comando sem perder os horarios`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(form = completeForm.copy(regularSlots = emptyList()))
            viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = null)))
            viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
            runCurrent()
            val saved = viewModel.state.value.form.regularSlots
            assertEquals(1, saved.size)

            viewModel.onIntent(GroupSetupIntent.ToggleRecurring(false))
            runCurrent()

            // O comando não leva agenda nenhuma…
            assertEquals(emptyList(), viewModel.state.value.slotsForCommand)
            // …mas o formulário guarda os horários para quem religa o switch.
            assertEquals(saved, viewModel.state.value.form.regularSlots)

            viewModel.onIntent(GroupSetupIntent.ToggleRecurring(true))
            runCurrent()

            assertEquals(saved, viewModel.state.value.slotsForCommand)
        }

    @Test
    fun `a duracao do grupo se espelha em todos os horarios`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupSetupIntent.SelectDuration(90))
        runCurrent()

        assertEquals(90, viewModel.state.value.durationMinutes)
        assertEquals(listOf(90), viewModel.state.value.form.regularSlots.map { it.durationMinutes })
    }

    @Test
    fun `so o texto digitado vai para o SavedStateHandle`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle()
        val viewModel = viewModel(savedState = handle)

        viewModel.onIntent(GroupSetupIntent.UpdateName("Beach da Vila"))
        viewModel.onIntent(GroupSetupIntent.UpdateVenueAddress("Av. Atlântica, 100"))
        viewModel.onIntent(GroupSetupIntent.SelectModality(GroupModality.BEACH_VOLLEYBALL))
        viewModel.onIntent(GroupSetupIntent.SelectDuration(90))
        viewModel.onIntent(GroupSetupIntent.ToggleRecurring(false))
        runCurrent()

        assertEquals("Beach da Vila", handle.get<String>("group-setup-name"))
        assertEquals("Av. Atlântica, 100", handle.get<String>("group-setup-venue-address"))
        assertEquals(emptySet(), handle.keys() - TextKeys)
    }

    @Test
    fun `o texto salvo volta por cima do estado inicial`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                "group-setup-name" to "Beach da Vila",
                "group-setup-venue-name" to "Praia Grande",
            ),
        )

        val viewModel = viewModel(savedState = handle)

        assertEquals("Beach da Vila", viewModel.state.value.form.name)
        assertEquals("Praia Grande", viewModel.state.value.form.defaultVenue?.name)
        // O que não foi digitado continua vindo do estado inicial.
        assertEquals(completeForm.defaultVenue?.address, viewModel.state.value.form.defaultVenue?.address)
        assertEquals(GroupModality.COURT_VOLLEYBALL, viewModel.state.value.form.modality)
    }

    @Test
    fun `salvar rascunho emite o efeito e limpa a falha`() = runTest(mainDispatcher) {
        val viewModel = viewModel(saveFailed = true)
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.SaveDraft)
        runCurrent()

        assertTrue(!viewModel.state.value.saveFailed)
        assertEquals(listOf(GroupSetupEffect.DraftSaved), effects)
    }

    private fun TestScope.collectEffects(viewModel: GroupSetupViewModel): List<GroupSetupEffect> {
        val received = mutableListOf<GroupSetupEffect>()
        backgroundScope.launch(mainDispatcher) { viewModel.effects.toList(received) }
        return received
    }

    private fun viewModel(
        mode: GroupSetupMode = GroupSetupMode.Create,
        form: GroupSetupForm = completeForm,
        recurring: Boolean = true,
        saveFailed: Boolean = false,
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = GroupSetupViewModel(
        initialState = GroupSetupState(
            mode = mode,
            form = form,
            recurring = recurring,
            saveFailed = saveFailed,
        ),
        savedState = savedState,
    )

    private companion object {
        val TextKeys = setOf(
            "group-setup-name",
            "group-setup-description",
            "group-setup-custom-level",
            "group-setup-venue-name",
            "group-setup-venue-address",
        )

        val completeForm = GroupSetupForm(
            name = "Vôlei do CERET",
            modality = GroupModality.COURT_VOLLEYBALL,
            composition = GroupComposition.MIXED,
            level = GroupLevel.INTERMEDIATE,
            defaultVenue = GroupVenueForm(name = "CERET — Quadra 2", address = "R. Canuto Abreu, s/n"),
            regularSlots = listOf(
                GroupRegularSlotForm(
                    weekday = GroupWeekday.TUESDAY,
                    startTime = "19:30",
                    durationMinutes = 120,
                ),
            ),
            defaultCapacity = 12,
            defaultConfirmationLeadMinutes = 360,
        )
    }
}
