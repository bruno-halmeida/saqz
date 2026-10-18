package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.model.MonthlyLateJoinPolicy
import br.com.saqz.groups.model.PromotionMode
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupTrialAccessInfo
import br.com.saqz.groups.domain.group.GroupTrialAccessPort
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import br.com.saqz.groups.presentation.FakeGroupSystemTimeZonePort
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGroup
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSetupViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `criar com campo faltando marca o erro e nao cria`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(name = ""))
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(setOf(GroupSetupError.NameRequired), viewModel.state.value.errors)
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
    fun `enviar o formulario valido cria direto e emite Created`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
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

        viewModel.onIntent(GroupSetupIntent.Submit)
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
        runCurrent()
        assertTrue(viewModel.state.value.saveFailed)

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
    fun `editar o payload descarta a chave antes de um novo create`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val handle = SavedStateHandle()
        var attempts = 0
        profileGateway.createHandler = {
            attempts += 1
            if (attempts == 1) {
                SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Connectivity))
            } else {
                SaqzResult.Success(br.com.saqz.groups.presentation.sampleGroup())
            }
        }
        val viewModel = viewModel(profileGateway = profileGateway, savedState = handle)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        val firstKey = profileGateway.createCommands.single().commandKey

        viewModel.onIntent(GroupSetupIntent.UpdateName("Nome editado"))
        assertEquals(null, viewModel.state.value.creationCommandKey)
        assertEquals(null, handle.get<String>("group-setup-create-command-key"))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(2, profileGateway.createCommands.size)
        assertTrue(firstKey != profileGateway.createCommands[1].commandKey)
        assertEquals("Nome editado", profileGateway.createCommands[1].form.name)
    }

    @Test
    fun `o padrao de vagas do grupo nao desce de 4`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        listOf(0, 2, 3).forEach { value ->
            viewModel.onIntent(GroupSetupIntent.UpdateCapacity(value))
            assertEquals(4, viewModel.state.value.form.defaultCapacity)
        }
        viewModel.onIntent(GroupSetupIntent.UpdateCapacity(4))
        assertEquals(4, viewModel.state.value.form.defaultCapacity)
        viewModel.onIntent(GroupSetupIntent.UpdateCapacity(100))
        assertEquals(100, viewModel.state.value.form.defaultCapacity)
    }

    @Test
    fun `criar grupo com o padrao no minimo envia 4`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(profileGateway = profileGateway)
        viewModel.onIntent(GroupSetupIntent.UpdateCapacity(2))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(4, profileGateway.createCommands.last().form.defaultCapacity)
    }

    @Test
    fun `criar grupo envia o valor por jogo digitado`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(profileGateway = profileGateway)
        viewModel.onIntent(GroupSetupIntent.UpdateDefaultGameFee(2500))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(2500L, profileGateway.createCommands.last().form.defaultGameFeeCents)
    }

    @Test
    fun `apagar o valor por jogo envia sem cobranca`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(profileGateway = profileGateway)
        viewModel.onIntent(GroupSetupIntent.UpdateDefaultGameFee(2500))
        viewModel.onIntent(GroupSetupIntent.UpdateDefaultGameFee(null))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertNull(profileGateway.createCommands.last().form.defaultGameFeeCents)
    }

    @Test
    fun `valor por jogo zerado e recusado e nada e enviado`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(profileGateway = profileGateway)
        viewModel.onIntent(GroupSetupIntent.UpdateDefaultGameFee(0))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(GroupSetupError.GameFeeInvalid in viewModel.state.value.errors)
        assertTrue(profileGateway.createCommands.isEmpty())
    }

    @Test
    fun `os ajustes abrem com o valor por jogo do grupo e salvam a troca`() = runTest(mainDispatcher) {
        val group = br.com.saqz.groups.presentation.sampleGroup().copy(
            financeDefaults = br.com.saqz.groups.domain.group.GroupFinanceDefaults(2500, null, null),
        )
        val profileGateway = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup(group)),
        )
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"), profileGateway = profileGateway)
        runCurrent()
        assertEquals(2500L, viewModel.state.value.form.defaultGameFeeCents)

        viewModel.onIntent(GroupSetupIntent.UpdateDefaultGameFee(3000))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(3000L, profileGateway.lastUpdateCommand?.form?.defaultGameFeeCents)
    }

    @Test
    fun `grupo antigo com padrao de 2 ou 3 vagas abre nos ajustes com 4`() = runTest(mainDispatcher) {
        listOf(2, 3).forEach { old ->
            val group = br.com.saqz.groups.presentation.sampleGroup().let {
                it.copy(profile = it.profile?.copy(defaultCapacity = old))
            }
            val viewModel = viewModel(
                mode = GroupSetupMode.Edit(groupId = "grp-1"),
                profileGateway = FakeGroupProfileGateway(
                    readResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup(group)),
                ),
            )
            runCurrent()

            assertEquals(4, viewModel.state.value.form.defaultCapacity)
        }
    }

    @Test
    fun `admin nao recebe a acao de excluir no formulario de edicao`() = runTest(mainDispatcher) {
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"))
        runCurrent()

        assertEquals(false, viewModel.state.value.canDelete)
    }

    @Test
    fun `plano inativo ao salvar o grupo abre o dialogo e mantem o formulario`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            profileGateway = FakeGroupProfileGateway(
                updateResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.SubscriptionRequired)),
            ),
            trialAccess = GroupTrialAccessPort { GroupTrialAccessInfo("2026-08-01T00:00:00Z", true, true, trialEnded = true) },
        )
        val effects = collectEffects(viewModel)
        val nameBefore = viewModel.state.value.form.name

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(viewModel.state.value.subscriptionRequired)
        assertTrue(viewModel.state.value.trialEnded)
        assertTrue(!viewModel.state.value.saveFailed)
        assertNull(viewModel.state.value.gatewayError)
        assertTrue(!viewModel.state.value.isSaving)
        assertTrue(effects.isEmpty())

        viewModel.onIntent(GroupSetupIntent.DismissSubscriptionRequired)
        assertTrue(!viewModel.state.value.subscriptionRequired)
        assertEquals(nameBefore, viewModel.state.value.form.name)
    }

    @Test
    fun `editar salva direto`() = runTest(mainDispatcher) {
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"))
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(!viewModel.state.value.isSaving)
        assertEquals(listOf(GroupSetupEffect.Saved), effects)
    }

    // ---- Backend v.0.0.17: quadra sem endereço volta como 400 VALIDATION_FAILED `defaultVenue.address` ----

    private fun refusedBy(field: String) = GroupProfileError.Validation(
        ValidationDetails(emptyList(), mapOf(field to listOf("is required"))),
    )

    private val venueWithoutAddress = GroupVenueForm(name = "Quadra do Parque", address = "")

    @Test
    fun `criar com quadra sem endereco envia endereco vazio e nao acusa erro`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(
            form = completeForm.copy(defaultVenue = venueWithoutAddress),
            profileGateway = profileGateway,
        )
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(viewModel.state.value.errors.isEmpty())
        assertEquals("", profileGateway.lastCreateCommand?.form?.defaultVenue?.address)
        assertEquals(listOf(GroupSetupEffect.Created(groupId = "group-1")), effects)
    }

    @Test
    fun `editar com quadra sem endereco salva`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"), profileGateway = profileGateway)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdateVenueName("Quadra do Parque"))
        viewModel.onIntent(GroupSetupIntent.UpdateVenueAddress(""))

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(viewModel.state.value.errors.isEmpty())
        assertNotNull(profileGateway.lastUpdateCommand)
    }

    @Test
    fun `endereco preenchido com menos de 5 caracteres e recusado`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(
            form = completeForm.copy(defaultVenue = GroupVenueForm(name = "Quadra do Parque", address = "Rua")),
            profileGateway = profileGateway,
        )

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(GroupSetupError.VenueAddressNotFound in viewModel.state.value.errors)
        assertTrue(profileGateway.createCommands.isEmpty())
    }

    @Test
    fun `campo recusado que o formulario nao sabe apontar segue como falha generica`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            profileGateway = FakeGroupProfileGateway(createResult = SaqzResult.Failure(refusedBy("description"))),
        )

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        val state = viewModel.state.value
        assertTrue(state.saveFailed)
        assertEquals(GroupUiError.Validation, state.gatewayError)
        assertTrue(state.errors.isEmpty())
    }

    @Test
    fun `create e update enviam slots vazios quando a recorrencia esta desligada`() =
        runTest(mainDispatcher) {
            val createGateway = FakeGroupProfileGateway()
            val creating = viewModel(recurring = false, profileGateway = createGateway)
            creating.onIntent(GroupSetupIntent.Submit)
            runCurrent()

            assertEquals(emptyList(), createGateway.lastCreateCommand?.form?.regularSlots)

            val updateGateway = FakeGroupProfileGateway()
            val editing = viewModel(
                mode = GroupSetupMode.Edit(groupId = "grp-1"),
                profileGateway = updateGateway,
            )
            // O `load()` liga a recorrência porque o grupo tem horário; a pessoa desliga.
            runCurrent()
            editing.onIntent(GroupSetupIntent.ToggleRecurring(false))
            editing.onIntent(GroupSetupIntent.Submit)
            runCurrent()

            assertEquals(emptyList(), updateGateway.lastUpdateCommand?.form?.regularSlots)
        }

    @Test
    fun `criar comeca sem recorrencia e aceita so os tres campos a vista`() = runTest(mainDispatcher) {
        val creatingGateway = FakeGroupProfileGateway()
        val creating = GroupSetupViewModel(
            initialState = GroupSetupState(
                mode = GroupSetupMode.Create,
                form = GroupSetupDefaults.Form.copy(
                    name = "Vôlei de terça",
                    modality = GroupModality.COURT_VOLLEYBALL,
                ),
            ),
            savedState = SavedStateHandle(),
            groupGateway = FakeGroupGateway(),
            profileGateway = creatingGateway,
            timeZonePort = FakeGroupSystemTimeZonePort(),
        )

        creating.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(false, creating.state.value.recurring)
        assertEquals(emptySet(), creating.state.value.errors)
        assertEquals(1, creatingGateway.createCommands.size)
    }

    @Test
    fun `editar grupo com horario carrega a recorrencia ligada e preserva os horarios`() =
        runTest(mainDispatcher) {
            val gateway = FakeGroupProfileGateway()
            val editing = viewModel(
                mode = GroupSetupMode.Edit(groupId = "grp-1"),
                recurring = false,
                profileGateway = gateway,
            )
            runCurrent()

            assertEquals(true, editing.state.value.recurring)

            editing.onIntent(GroupSetupIntent.Submit)
            runCurrent()

            assertEquals(1, gateway.lastUpdateCommand?.form?.regularSlots?.size)
        }

    @Test
    fun `editar grupo sem horario carrega a recorrencia desligada`() = runTest(mainDispatcher) {
        val group = sampleGroup()
        val gateway = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(
                sampleVersionedGroup(group.copy(profile = group.profile?.copy(regularSlots = emptyList()))),
            ),
        )
        val editing = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            recurring = true,
            profileGateway = gateway,
        )
        runCurrent()

        assertEquals(false, editing.state.value.recurring)

        editing.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(emptySet(), editing.state.value.errors)
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

    @Test
    fun `escolher modalidade ou categoria aplica e fecha a folha`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Modality))
        viewModel.onIntent(GroupSetupIntent.SelectModality(GroupModality.BEACH_VOLLEYBALL))
        assertEquals(GroupModality.BEACH_VOLLEYBALL, viewModel.state.value.form.modality)
        assertNull(viewModel.state.value.sheet)

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Level))
        viewModel.onIntent(GroupSetupIntent.SelectLevel(GroupLevel.ADVANCED))
        assertEquals(GroupLevel.ADVANCED, viewModel.state.value.form.level)
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

    @Test
    fun `configuracoes nao salvas sobrevivem a recriacao e ao load`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle()
        viewModel(mode = GroupSetupMode.Edit("grp-1"), savedState = handle).also {
            it.onIntent(GroupSetupIntent.ToggleMensalistaPriority(false))
            it.onIntent(GroupSetupIntent.SelectPromotionMode(PromotionMode.MANUAL))
        }
        assertEquals(false, handle.get<Boolean>("group-setup-mensalista-priority"))
        assertEquals("MANUAL", handle.get<String>("group-setup-promotion-mode"))

        val serverGroup = br.com.saqz.groups.presentation.sampleGroup().copy(
            gameConfig = br.com.saqz.groups.domain.group.GroupGameConfig(),
        )
        val restored = viewModel(
            mode = GroupSetupMode.Edit("grp-1"),
            savedState = handle,
            profileGateway = FakeGroupProfileGateway(
                readResult = SaqzResult.Success(
                    br.com.saqz.groups.presentation.sampleVersionedGroup(serverGroup),
                ),
            ),
        )
        runCurrent()

        assertEquals(false, restored.state.value.form.mensalistaPriority)
        assertEquals(PromotionMode.MANUAL, restored.state.value.form.promotionMode)
    }

    @Test
    fun `edicao hidrata a duracao do primeiro horario e a permissao de excluir`() =
        runTest(mainDispatcher) {
            val group = br.com.saqz.groups.presentation.sampleGroup(
                role = br.com.saqz.groups.domain.group.GroupRole.OWNER,
                profile = br.com.saqz.groups.presentation.sampleGroup().profile?.copy(
                    regularSlots = listOf(
                        br.com.saqz.groups.domain.group.GroupRegularSlot(
                            weekday = br.com.saqz.groups.domain.group.GroupWeekday.TUESDAY,
                            startTime = "19:30",
                            durationMinutes = 90,
                        ),
                    ),
                    defaultConfirmationLeadMinutes = 180,
                ),
            )
            val viewModel = viewModel(
                mode = GroupSetupMode.Edit(groupId = "grp-1"),
                profileGateway = FakeGroupProfileGateway(
                    readResult = SaqzResult.Success(
                        br.com.saqz.groups.presentation.sampleVersionedGroup(group),
                    ),
                ),
            )
            runCurrent()

            assertEquals(90, viewModel.state.value.durationMinutes)
            assertEquals(listOf(90), viewModel.state.value.form.regularSlots.map { it.durationMinutes })
            assertTrue(viewModel.state.value.canDelete)
            assertTrue(viewModel.state.value.canManageGameConfig)
        }

    @Test
    fun `edicao hidrata a configuracao de jogo do grupo`() = runTest(mainDispatcher) {
        val group = br.com.saqz.groups.presentation.sampleGroup().copy(
            gameConfig = br.com.saqz.groups.domain.group.GroupGameConfig(
                mensalistaPriority = false,
                promotionMode = br.com.saqz.groups.domain.group.PromotionMode.MANUAL,
            ),
        )
        val viewModel = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            profileGateway = FakeGroupProfileGateway(
                readResult = SaqzResult.Success(
                    br.com.saqz.groups.presentation.sampleVersionedGroup(group),
                ),
            ),
        )
        runCurrent()

        val form = viewModel.state.value.form
        assertEquals(false, form.mensalistaPriority)
        assertEquals(PromotionMode.MANUAL, form.promotionMode)
    }

    @Test
    fun `edicao hidrata os campos Pix do grupo`() = runTest(mainDispatcher) {
        val group = br.com.saqz.groups.presentation.sampleGroup().copy(
            profile = br.com.saqz.groups.presentation.sampleGroup().profile?.copy(
                pixKey = "racha@saqz.test",
                pixLabel = "Lucas Prado · Nubank",
            ),
        )
        val viewModel = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            profileGateway = FakeGroupProfileGateway(
                readResult = SaqzResult.Success(
                    br.com.saqz.groups.presentation.sampleVersionedGroup(group),
                ),
            ),
        )
        runCurrent()

        assertEquals("racha@saqz.test", viewModel.state.value.pixKey)
        assertEquals("Lucas Prado · Nubank", viewModel.state.value.pixLabel)
    }

    @Test
    fun `editar envia a configuracao de jogo no comando de update`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            profileGateway = profileGateway,
        )
        viewModel.onIntent(GroupSetupIntent.ToggleMensalistaPriority(false))
        viewModel.onIntent(GroupSetupIntent.SelectPromotionMode(PromotionMode.MANUAL))
        runCurrent()

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        val form = profileGateway.lastUpdateCommand?.form
        assertEquals(false, form?.mensalistaPriority)
        assertEquals(
            br.com.saqz.groups.domain.group.PromotionMode.MANUAL,
            form?.promotionMode,
        )
    }

    private fun groupWithMonthly(
        fee: Long? = 5_000,
        policy: br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy? =
            br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy.GROUP_DUE_DATE,
    ) = br.com.saqz.groups.presentation.sampleGroup().copy(
        financeDefaults = br.com.saqz.groups.domain.group.GroupFinanceDefaults(
            defaultGameFeeCents = null,
            monthlyFeeCents = fee,
            monthlyDueDay = 10,
            monthlyLateJoinPolicy = policy,
        ),
    )

    private fun TestScope.editViewModel(
        group: br.com.saqz.groups.domain.group.Group,
        profileGateway: FakeGroupProfileGateway = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup(group)),
        ),
        savedState: SavedStateHandle = SavedStateHandle(),
        athleteGateway: FakeAthleteGateway? = null,
    ) = viewModel(
        mode = GroupSetupMode.Edit(groupId = "grp-1"),
        profileGateway = profileGateway,
        savedState = savedState,
        athleteGateway = athleteGateway,
    ).also { runCurrent() }

    private fun rosterGateway(vararg entries: br.com.saqz.groups.domain.athlete.AthleteRosterEntry) =
        FakeAthleteGateway(rosterResult = SaqzResult.Success(entries.toList()))

    @Test
    fun `seletor da primeira mensalidade aparece com mensalidade e politica vindas do backend`() = runTest(mainDispatcher) {
        val viewModel = editViewModel(groupWithMonthly())

        assertEquals(MonthlyLateJoinPolicy.GROUP_DUE_DATE, viewModel.state.value.form.monthlyLateJoinPolicy)
        assertTrue(viewModel.state.value.showsMonthlyLateJoinPolicy)
    }

    @Test
    fun `com mensalidade padrao o seletor aparece sem buscar os membros`() = runTest(mainDispatcher) {
        val athletes = rosterGateway()
        val viewModel = editViewModel(groupWithMonthly(fee = 5_000), athleteGateway = athletes)

        assertTrue(viewModel.state.value.showsMonthlyLateJoinPolicy)
        assertNull(athletes.lastRosterFilter)
    }

    @Test
    fun `sem mensalidade padrao o seletor aparece se algum mensalista tem valor proprio`() = runTest(mainDispatcher) {
        val athletes = rosterGateway(
            br.com.saqz.groups.presentation.sampleRosterEntry("sem-valor"),
            br.com.saqz.groups.presentation.sampleRosterEntry("com-valor").copy(monthlyFeeCents = 7_000),
        )
        val viewModel = editViewModel(groupWithMonthly(fee = null), athleteGateway = athletes)

        assertEquals(null, viewModel.state.value.form.monthlyFeeCents)
        assertTrue(viewModel.state.value.rosterHasMensalista)
        assertTrue(viewModel.state.value.showsMonthlyLateJoinPolicy)
        assertEquals(br.com.saqz.groups.domain.athlete.AthleteRosterFilter(), athletes.lastRosterFilter)
    }

    @Test
    fun `sem mensalidade padrao o seletor fica escondido se nenhum mensalista tem valor proprio`() = runTest(mainDispatcher) {
        val semValor = br.com.saqz.groups.presentation.sampleRosterEntry("sem-valor")
        val avulsoComValor = br.com.saqz.groups.presentation.sampleRosterEntry("avulso").copy(
            membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO,
            monthlyFeeCents = 7_000,
        )
        listOf(rosterGateway(), rosterGateway(semValor), rosterGateway(avulsoComValor)).forEach { athletes ->
            val viewModel = editViewModel(groupWithMonthly(fee = null), athleteGateway = athletes)

            assertEquals(false, viewModel.state.value.showsMonthlyLateJoinPolicy)
        }
    }

    private fun ownerGroup() = groupWithMonthly().copy(role = br.com.saqz.groups.domain.group.GroupRole.OWNER)

    private fun entry(id: String, role: br.com.saqz.groups.domain.group.GroupRole, active: Boolean = true) =
        br.com.saqz.groups.presentation.sampleRosterEntry(id).copy(role = role, active = active)

    @Test
    fun `a exclusao conta os membros ativos fora o dono`() = runTest(mainDispatcher) {
        val role = br.com.saqz.groups.domain.group.GroupRole.entries.associateBy { it.name }
        val athletes = rosterGateway(
            entry("dono", role.getValue("OWNER")),
            entry("admin", role.getValue("ADMIN")),
            entry("a", role.getValue("ATHLETE")),
            entry("b", role.getValue("ATHLETE")),
            entry("saiu", role.getValue("ATHLETE"), active = false),
        )
        val viewModel = editViewModel(ownerGroup(), athleteGateway = athletes)

        assertEquals(3, viewModel.state.value.memberCount)
    }

    @Test
    fun `grupo so com o dono conta zero`() = runTest(mainDispatcher) {
        val athletes = rosterGateway(entry("dono", br.com.saqz.groups.domain.group.GroupRole.OWNER))
        val viewModel = editViewModel(ownerGroup(), athleteGateway = athletes)

        assertEquals(0, viewModel.state.value.memberCount)
    }

    @Test
    fun `falha ao buscar os membros deixa a contagem desconhecida`() = runTest(mainDispatcher) {
        val athletes = FakeAthleteGateway(
            rosterResult = SaqzResult.Failure(br.com.saqz.groups.domain.athlete.AthleteError.DataFailure(DataError.Connectivity)),
        )
        val viewModel = editViewModel(ownerGroup(), athleteGateway = athletes)

        assertNull(viewModel.state.value.memberCount)
    }

    @Test
    fun `admin nao busca os membros so para a exclusao`() = runTest(mainDispatcher) {
        val athletes = rosterGateway()
        val viewModel = editViewModel(groupWithMonthly(fee = 5_000), athleteGateway = athletes)

        assertNull(viewModel.state.value.memberCount)
        assertNull(athletes.lastRosterFilter)
    }

    @Test
    fun `falha ao buscar os membros esconde o seletor sem derrubar o formulario`() = runTest(mainDispatcher) {
        val athletes = FakeAthleteGateway(
            rosterResult = SaqzResult.Failure(br.com.saqz.groups.domain.athlete.AthleteError.DataFailure(DataError.Connectivity)),
        )
        val viewModel = editViewModel(groupWithMonthly(fee = null), athleteGateway = athletes)

        assertEquals(false, viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.gatewayError)
        assertEquals(false, viewModel.state.value.showsMonthlyLateJoinPolicy)
    }

    @Test
    fun `sem o campo do backend o seletor fica escondido e os membros nem sao buscados`() = runTest(mainDispatcher) {
        val athletes = rosterGateway(
            br.com.saqz.groups.presentation.sampleRosterEntry("com-valor").copy(monthlyFeeCents = 7_000),
        )
        val viewModel = editViewModel(groupWithMonthly(fee = null, policy = null), athleteGateway = athletes)

        assertEquals(false, viewModel.state.value.showsMonthlyLateJoinPolicy)
        assertNull(athletes.lastRosterFilter)
    }

    @Test
    fun `sem o campo do backend o seletor fica escondido mesmo com mensalidade padrao`() = runTest(mainDispatcher) {
        assertEquals(false, editViewModel(groupWithMonthly(policy = null)).state.value.showsMonthlyLateJoinPolicy)
    }

    @Test
    fun `seletor da primeira mensalidade fica escondido na criacao`() = runTest(mainDispatcher) {
        assertEquals(false, viewModel(mode = GroupSetupMode.Create).state.value.showsMonthlyLateJoinPolicy)
    }

    @Test
    fun `escolher a politica vai no comando de update e sobrevive a recriacao`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle()
        val profileGateway = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup(groupWithMonthly())),
        )
        val viewModel = editViewModel(groupWithMonthly(), profileGateway, handle)

        viewModel.onIntent(GroupSetupIntent.SelectMonthlyLateJoinPolicy(MonthlyLateJoinPolicy.NEXT_MONTH))
        assertEquals("NEXT_MONTH", handle.get<String>("group-setup-monthly-late-join-policy"))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(
            br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy.NEXT_MONTH,
            profileGateway.lastUpdateCommand?.form?.monthlyLateJoinPolicy,
        )
        val restored = editViewModel(groupWithMonthly(), profileGateway, handle)
        assertEquals(MonthlyLateJoinPolicy.NEXT_MONTH, restored.state.value.form.monthlyLateJoinPolicy)
    }

    @Test
    fun `sem o campo do backend o update nao envia politica`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(
                br.com.saqz.groups.presentation.sampleVersionedGroup(groupWithMonthly(policy = null)),
            ),
        )
        val viewModel = editViewModel(groupWithMonthly(policy = null), profileGateway)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(null, profileGateway.lastUpdateCommand?.form?.monthlyLateJoinPolicy)
    }

    @Test
    fun `editar envia Pix com trim sem validar o formato da chave`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            profileGateway = profileGateway,
        )
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey("  texto livre sem formato  "))
        viewModel.onIntent(GroupSetupIntent.UpdatePixLabel("  Lucas Prado · Nubank  "))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        val form = profileGateway.lastUpdateCommand?.form
        assertEquals("texto livre sem formato", form?.pixKey)
        assertEquals("Lucas Prado · Nubank", form?.pixLabel)
    }

    @Test
    fun `editar envia string vazia para limpar os campos Pix`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            profileGateway = profileGateway,
        )
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey(""))
        viewModel.onIntent(GroupSetupIntent.UpdatePixLabel(""))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        val form = profileGateway.lastUpdateCommand?.form
        assertEquals("", form?.pixKey)
        assertEquals("", form?.pixLabel)
    }

    @Test
    fun `criar envia nulo para campos Pix vazios`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(profileGateway = profileGateway)
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey(""))
        viewModel.onIntent(GroupSetupIntent.UpdatePixLabel(""))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        val form = profileGateway.lastCreateCommand?.form
        assertNull(form?.pixKey)
        assertNull(form?.pixLabel)
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
        viewModel.onIntent(
            GroupSetupIntent.UpdatePixKey("P".repeat(GroupPixTextLimits.KeyMax + 1)),
        )
        viewModel.onIntent(
            GroupSetupIntent.UpdatePixLabel("L".repeat(GroupPixTextLimits.LabelMax + 1)),
        )
        runCurrent()

        val form = viewModel.state.value.form
        assertEquals(GroupTextLimits.NameMax, form.name.length)
        assertEquals(GroupTextLimits.DescriptionMax, form.description?.length)
        assertEquals(GroupTextLimits.CustomLevelMax, form.customLevel?.length)
        assertEquals(GroupTextLimits.VenueNameMax, form.defaultVenue?.name?.length)
        assertEquals(GroupTextLimits.VenueAddressMax, form.defaultVenue?.address?.length)
        assertEquals(GroupPixTextLimits.KeyMax, viewModel.state.value.pixKey?.length)
        assertEquals(GroupPixTextLimits.LabelMax, viewModel.state.value.pixLabel?.length)
        // O que foi cortado também não vaza para o process death.
        assertEquals(GroupTextLimits.NameMax, handle.get<String>("group-setup-name")?.length)
        assertEquals(GroupPixTextLimits.KeyMax, handle.get<String>("group-setup-pix-key")?.length)
        assertEquals(GroupPixTextLimits.LabelMax, handle.get<String>("group-setup-pix-label")?.length)
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

            // Com recorrência ligada a quadra é obrigatória; o caso aqui é o grupo sem recorrência.
            viewModel.onIntent(GroupSetupIntent.ToggleRecurring(false))
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
        // Com recorrência ligada a quadra é obrigatória; o caso aqui é o grupo sem recorrência.
        viewModel.onIntent(GroupSetupIntent.ToggleRecurring(false))
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
    fun `falha ao criar nunca descarta o que foi digitado`() = runTest(mainDispatcher) {
        val viewModel = viewModel(
            profileGateway = FakeGroupProfileGateway(
                createResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Connectivity)),
            ),
        )
        val effects = collectEffects(viewModel)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        // A pessoa segue no formulário preenchido, com "Tentar de novo"; nada fecha a tela.
        assertTrue(viewModel.state.value.saveFailed)
        assertEquals(completeForm, viewModel.state.value.form)
        assertTrue(effects.isEmpty())
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
        trialAccess: GroupTrialAccessPort? = null,
        athleteGateway: FakeAthleteGateway? = null,
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
        trialAccess = trialAccess,
        athleteGateway = athleteGateway,
    )

    private companion object {
        val TextKeys = setOf(
            "group-setup-name",
            "group-setup-description",
            "group-setup-custom-level",
            "group-setup-pix-key",
            "group-setup-pix-label",
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
