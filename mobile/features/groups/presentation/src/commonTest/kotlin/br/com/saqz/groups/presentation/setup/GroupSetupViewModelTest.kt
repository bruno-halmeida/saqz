package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import br.com.saqz.groups.presentation.FakeGroupSystemTimeZonePort
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
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

        assertTrue(!viewModel.state.value.isSaving)
        assertEquals(listOf(GroupSetupEffect.Created(groupId = "group-1")), effects)
    }

    @Test
    fun `falha da criacao fica visivel como erro tipado`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            profileGateway = FakeGroupProfileGateway(
                createResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.NotFound)),
            ),
        )
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.ConfirmCreate)
        runCurrent()

        assertTrue(viewModel.state.value.saveFailed)
        assertEquals(GroupUiError.NotFound, viewModel.state.value.gatewayError)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `retry da criacao reutiliza a mesma chave de idempotencia`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        var attempts = 0
        profileGateway.createHandler = {
            attempts += 1
            if (attempts == 1) {
                SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Connectivity))
            } else {
                SaqzResult.Success(br.com.saqz.groups.presentation.sampleGroup())
            }
        }
        val viewModel = viewModel(profileGateway = profileGateway)
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
        viewModel.onIntent(GroupSetupIntent.ConfirmCreate)
        runCurrent()
        assertEquals(GroupSetupStep.Form, viewModel.state.value.step)

        viewModel.onIntent(GroupSetupIntent.Retry)
        runCurrent()

        assertEquals(2, profileGateway.createCommands.size)
        assertEquals(
            profileGateway.createCommands[0].commandKey,
            profileGateway.createCommands[1].commandKey,
        )
        assertEquals(listOf(GroupSetupEffect.Created(groupId = "group-1")), effects)
        assertEquals(null, viewModel.state.value.creationCommandKey)
    }

    @Test
    fun `editar salva direto sem passar pela revisao`() = runTest(mainDispatcher) {
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"))
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(GroupSetupStep.Form, viewModel.state.value.step)
        assertTrue(!viewModel.state.value.isSaving)
        assertEquals(listOf(GroupSetupEffect.Saved), effects)
    }

    @Test
    fun `create e update enviam slots vazios quando a recorrencia esta desligada`() =
        runTest(mainDispatcher) {
            val createGateway = FakeGroupProfileGateway()
            val creating = viewModel(recurring = false, profileGateway = createGateway)
            creating.onIntent(GroupSetupIntent.Submit)
            creating.onIntent(GroupSetupIntent.ConfirmCreate)
            runCurrent()

            assertEquals(emptyList(), createGateway.lastCreateCommand?.form?.regularSlots)

            val updateGateway = FakeGroupProfileGateway()
            val editing = viewModel(
                mode = GroupSetupMode.Edit(groupId = "grp-1"),
                recurring = false,
                profileGateway = updateGateway,
            )
            editing.onIntent(GroupSetupIntent.Submit)
            runCurrent()

            assertEquals(emptyList(), updateGateway.lastUpdateCommand?.form?.regularSlots)
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

        assertTrue(!editing.state.value.isDeleting)
        assertNull(editing.state.value.sheet)
        assertEquals(listOf(GroupSetupEffect.Deleted), editingEffects)
    }

    @Test
    fun `falha do delete vira erro tipado visivel`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            groupGateway = FakeGroupGateway(
                deleteResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Forbidden)),
            ),
        )

        viewModel.onIntent(GroupSetupIntent.ConfirmDelete)
        runCurrent()

        assertTrue(viewModel.state.value.saveFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.gatewayError)
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

    /** Folha fechada, intent inválido: o segundo toque em Salvar não pode duplicar. */
    @Test
    fun `confirmar o horario duas vezes seguidas entra uma vez so`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(regularSlots = emptyList()))

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = null)))
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        runCurrent()

        assertEquals(1, viewModel.state.value.form.regularSlots.size)
        assertNull(viewModel.state.value.sheet)
    }

    /**
     * O backend conta code point. Um emoji tem `length` 2 e conta 1, e o corte no teto
     * não pode partir o par substituto ao meio.
     */
    @Test
    fun `o corte do teto conta code point e nao parte o emoji`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        val ball = "🏐"

        viewModel.onIntent(GroupSetupIntent.UpdateName(ball.repeat(GroupTextLimits.NameMax + 5)))
        runCurrent()

        val name = viewModel.state.value.form.name
        assertEquals(GroupTextLimits.NameMax, name.codePointLength())
        assertEquals(GroupTextLimits.NameMax * 2, name.length)
        assertTrue(name.none { it.isHighSurrogate() && name.indexOf(it) == name.lastIndex })
    }

    @Test
    fun `caractere de controle sai na digitacao`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupSetupIntent.UpdateName("Vôlei\ndo CERET"))
        runCurrent()

        assertEquals("Vôleido CERET", viewModel.state.value.form.name)
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
    fun `o texto salvo continua por cima do perfil carregado no modo edicao`() =
        runTest(mainDispatcher) {
            val handle = SavedStateHandle(
                mapOf(
                    "group-setup-name" to "Nome restaurado",
                    "group-setup-description" to "Descrição restaurada",
                    "group-setup-custom-level" to "Nível restaurado",
                    "group-setup-venue-name" to "Quadra restaurada",
                    "group-setup-venue-address" to "Endereço restaurado",
                ),
            )

            val viewModel = viewModel(
                mode = GroupSetupMode.Edit(groupId = "grp-1"),
                savedState = handle,
            )
            runCurrent()

            assertEquals("Nome restaurado", viewModel.state.value.form.name)
            assertEquals("Descrição restaurada", viewModel.state.value.form.description)
            assertEquals("Nível restaurado", viewModel.state.value.form.customLevel)
            assertEquals("Quadra restaurada", viewModel.state.value.form.defaultVenue?.name)
            assertEquals("Endereço restaurado", viewModel.state.value.form.defaultVenue?.address)
        }

    /**
     * Teto de digitação: o estado para no máximo do backend em vez de deixar digitar e
     * reprovar depois. Fronteira nos dois lados — o máximo exato entra inteiro, um
     * caractere além é cortado.
     */
    @Test
    fun `o texto para no maximo do backend`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle()
        val viewModel = viewModel(savedState = handle)

        viewModel.onIntent(GroupSetupIntent.UpdateName("N".repeat(GroupTextLimits.NameMax)))
        runCurrent()
        assertEquals(GroupTextLimits.NameMax, viewModel.state.value.form.name.length)

        viewModel.onIntent(GroupSetupIntent.UpdateName("N".repeat(GroupTextLimits.NameMax + 1)))
        viewModel.onIntent(
            GroupSetupIntent.UpdateDescription("D".repeat(GroupTextLimits.DescriptionMax + 1)),
        )
        viewModel.onIntent(
            GroupSetupIntent.UpdateCustomLevel("C".repeat(GroupTextLimits.CustomLevelMax + 1)),
        )
        viewModel.onIntent(GroupSetupIntent.UpdateVenueName("Q".repeat(GroupTextLimits.VenueNameMax + 1)))
        viewModel.onIntent(
            GroupSetupIntent.UpdateVenueAddress("E".repeat(GroupTextLimits.VenueAddressMax + 1)),
        )
        runCurrent()

        val form = viewModel.state.value.form
        assertEquals(GroupTextLimits.NameMax, form.name.length)
        assertEquals(GroupTextLimits.DescriptionMax, form.description?.length)
        assertEquals(GroupTextLimits.CustomLevelMax, form.customLevel?.length)
        assertEquals(GroupTextLimits.VenueNameMax, form.defaultVenue?.name?.length)
        assertEquals(GroupTextLimits.VenueAddressMax, form.defaultVenue?.address?.length)
        // O que foi cortado também não vaza para o process death.
        assertEquals(GroupTextLimits.NameMax, handle.get<String>("group-setup-name")?.length)
    }

    /** Quadra vazia não é quadra: o backend recusa `defaultVenue` com campos em branco. */
    @Test
    fun `apagar os dois campos da quadra devolve a quadra a nulo`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(defaultVenue = null))

        viewModel.onIntent(GroupSetupIntent.UpdateVenueName("CERET"))
        runCurrent()
        assertEquals("CERET", viewModel.state.value.form.defaultVenue?.name)

        viewModel.onIntent(GroupSetupIntent.UpdateVenueName(""))
        runCurrent()

        assertNull(viewModel.state.value.form.defaultVenue)
    }

    /** `court` vem do grupo carregado e não tem campo: sozinho não segura a quadra viva. */
    @Test
    fun `apagar os campos visiveis apaga a quadra mesmo com complemento salvo`() =
        runTest(mainDispatcher) {
            val viewModel = viewModel(
                form = completeForm.copy(
                    defaultVenue = GroupVenueForm(
                        id = "venue-1",
                        name = "CERET — Quadra 2",
                        address = "R. Canuto Abreu, s/n",
                        court = "Quadra 2",
                    ),
                ),
            )

            viewModel.onIntent(GroupSetupIntent.UpdateVenueName(""))
            viewModel.onIntent(GroupSetupIntent.UpdateVenueAddress(""))
            viewModel.onIntent(GroupSetupIntent.Submit)
            runCurrent()

            assertNull(viewModel.state.value.form.defaultVenue)
            assertNull(viewModel.state.value.venueForCommand)
            // E sem quadra não há erro de quadra: o usuário consegue sair do formulário.
            assertTrue(viewModel.state.value.errors.isEmpty())
        }

    /** A volta do process death tem de reproduzir o que estava na tela, não um objeto vazio. */
    @Test
    fun `quadra apagada antes da morte do processo volta nula`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle(
            mapOf("group-setup-venue-name" to "", "group-setup-venue-address" to ""),
        )

        val viewModel = viewModel(savedState = handle)
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertNull(viewModel.state.value.form.defaultVenue)
        assertNull(viewModel.state.value.venueForCommand)
        assertTrue(viewModel.state.value.errors.isEmpty())
    }

    @Test
    fun `quadra pela metade continua existindo para ser acusada`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(defaultVenue = null))

        viewModel.onIntent(GroupSetupIntent.UpdateVenueAddress("R. Canuto Abreu, s/n"))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals("R. Canuto Abreu, s/n", viewModel.state.value.form.defaultVenue?.address)
        assertEquals(setOf(GroupSetupError.VenueNameRequired), viewModel.state.value.errors)
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
        groupGateway: FakeGroupGateway = FakeGroupGateway(),
        profileGateway: FakeGroupProfileGateway = FakeGroupProfileGateway(),
    ) = GroupSetupViewModel(
        initialState = GroupSetupState(
            mode = mode,
            form = form,
            recurring = recurring,
            saveFailed = saveFailed,
        ),
        savedState = savedState,
        groupGateway = groupGateway,
        profileGateway = profileGateway,
        timeZonePort = FakeGroupSystemTimeZonePort(),
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
