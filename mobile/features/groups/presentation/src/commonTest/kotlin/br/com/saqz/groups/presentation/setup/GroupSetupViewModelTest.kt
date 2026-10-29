package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.presentation.GameFeeChoice
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import androidx.lifecycle.SavedStateHandle
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.presentation.pix.PixKeyType
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.model.AttendanceUpdatesMode
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
import kotlin.test.assertFalse
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
        viewModel.onIntent(GroupSetupIntent.SelectGameFeeChoice(GameFeeChoice.Charge))
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
        viewModel.onIntent(GroupSetupIntent.SelectGameFeeChoice(GameFeeChoice.Charge))
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

        // DEB-146: na criação o valor por jogo é opcional (29/10); sem escolha o grupo nasce sem valor.
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
    fun `marcar dois dias cria um horario por dia com a duracao do grupo`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(regularSlots = emptyList()))

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = null)))
        viewModel.onIntent(GroupSetupIntent.ToggleSlotWeekday(GroupWeekday.THURSDAY))
        viewModel.onIntent(GroupSetupIntent.PickSlotTime(hour = 19, minute = 30))
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        runCurrent()

        assertEquals(
            listOf(GroupWeekday.TUESDAY, GroupWeekday.THURSDAY),
            viewModel.state.value.form.regularSlots.map { it.weekday },
        )
        assertEquals(setOf("19:30"), viewModel.state.value.form.regularSlots.map { it.startTime }.toSet())
        assertEquals(setOf(120), viewModel.state.value.form.regularSlots.map { it.durationMinutes }.toSet())
    }

    @Test
    fun `salvar nao duplica dia e hora que ja existem`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = null)))
        viewModel.onIntent(GroupSetupIntent.ToggleSlotWeekday(GroupWeekday.THURSDAY))
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        runCurrent()

        assertEquals(
            listOf(GroupWeekday.TUESDAY, GroupWeekday.THURSDAY),
            viewModel.state.value.form.regularSlots.map { it.weekday },
        )
    }

    @Test
    fun `sem nenhum dia marcado salvar nao faz nada`() = runTest(mainDispatcher) {
        val viewModel = viewModel(form = completeForm.copy(regularSlots = emptyList()))

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = null)))
        viewModel.onIntent(GroupSetupIntent.ToggleSlotWeekday(GroupWeekday.TUESDAY))
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        runCurrent()

        assertEquals(emptyList(), viewModel.state.value.form.regularSlots)
        assertTrue(viewModel.state.value.sheet is GroupSetupSheet.Slot)
    }

    @Test
    fun `horario salvo com outro minuto mantem o minuto ate a pessoa trocar`() = runTest(mainDispatcher) {
        val odd = GroupRegularSlotForm(weekday = GroupWeekday.FRIDAY, startTime = "20:20", durationMinutes = 120)
        val viewModel = viewModel(form = completeForm.copy(regularSlots = listOf(odd)))

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = 0)))
        assertEquals(20, viewModel.state.value.slotDraft.minute)
        viewModel.onIntent(GroupSetupIntent.PickSlotTime(hour = 21, minute = 20))
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        runCurrent()

        assertEquals("21:20", viewModel.state.value.form.regularSlots.single().startTime)
    }

    @Test
    fun `editar um horario troca so aquele e nao cria outro`() = runTest(mainDispatcher) {
        val tuesday = GroupRegularSlotForm(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120)
        val friday = GroupRegularSlotForm(weekday = GroupWeekday.FRIDAY, startTime = "20:00", durationMinutes = 120)
        val viewModel = viewModel(form = completeForm.copy(regularSlots = listOf(tuesday, friday)))

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = 1)))
        assertEquals(GroupSlotDraft(GroupWeekday.FRIDAY, 20, 0), viewModel.state.value.slotDraft)
        viewModel.onIntent(GroupSetupIntent.PickSlotWeekday(GroupWeekday.SATURDAY))
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        runCurrent()

        assertEquals(
            listOf(GroupWeekday.TUESDAY to "19:30", GroupWeekday.SATURDAY to "20:00"),
            viewModel.state.value.form.regularSlots.map { it.weekday to it.startTime },
        )
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun `editar para um dia e hora que ja existem deixa um so`() = runTest(mainDispatcher) {
        val tuesday = GroupRegularSlotForm(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120)
        val friday = GroupRegularSlotForm(weekday = GroupWeekday.FRIDAY, startTime = "19:30", durationMinutes = 120)
        val viewModel = viewModel(form = completeForm.copy(regularSlots = listOf(tuesday, friday)))

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = 1)))
        viewModel.onIntent(GroupSetupIntent.PickSlotWeekday(GroupWeekday.TUESDAY))
        viewModel.onIntent(GroupSetupIntent.ConfirmSlot)
        runCurrent()

        assertEquals(listOf(tuesday), viewModel.state.value.form.regularSlots)
    }

    @Test
    fun `fechar a folha de um horario sem salvar nao muda nada`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        val before = viewModel.state.value.form.regularSlots

        viewModel.onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = 0)))
        viewModel.onIntent(GroupSetupIntent.PickSlotWeekday(GroupWeekday.SATURDAY))
        viewModel.onIntent(GroupSetupIntent.CloseSheet)
        runCurrent()

        assertNull(viewModel.state.value.sheet)
        assertEquals(before, viewModel.state.value.form.regularSlots)
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
    fun `a duracao de 4h do contador vale para o grupo e se espelha nos horarios`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupSetupIntent.SelectDuration(240))
        runCurrent()

        assertEquals(240, viewModel.state.value.durationMinutes)
        assertEquals(listOf(240), viewModel.state.value.form.regularSlots.map { it.durationMinutes })
    }

    @Test
    fun `duracao fora de 1h a 8h acusa erro no contador e nao salva`() = runTest(mainDispatcher) {
        listOf(0, 45, 540).forEach { minutes ->
            val viewModel = viewModel()
            viewModel.onIntent(GroupSetupIntent.SelectDuration(minutes))
            viewModel.onIntent(GroupSetupIntent.Submit)
            runCurrent()

            assertTrue(GroupSetupError.DurationOutOfRange in viewModel.state.value.errors, "$minutes")
        }
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
            defaultNoGameFee = true,
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
    fun `lista no WhatsApp abre por horario e o dono troca para a cada confirmacao`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle()
        val group = groupWithMonthly().copy(role = GroupRole.OWNER)
        val profileGateway = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup(group)),
        )
        val viewModel = editViewModel(group, profileGateway, handle)
        assertEquals(AttendanceUpdatesMode.SCHEDULED, viewModel.state.value.form.attendanceUpdatesMode)
        assertTrue(viewModel.state.value.canChangeAttendanceUpdates)

        viewModel.onIntent(GroupSetupIntent.SelectAttendanceUpdatesMode(AttendanceUpdatesMode.ON_CHANGE))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(
            br.com.saqz.groups.domain.group.AttendanceUpdatesMode.ON_CHANGE,
            profileGateway.lastUpdateCommand?.form?.attendanceUpdatesMode,
        )
        val restored = editViewModel(group, profileGateway, handle)
        assertEquals(AttendanceUpdatesMode.ON_CHANGE, restored.state.value.form.attendanceUpdatesMode)
    }

    @Test
    fun `lista no WhatsApp le o modo do grupo e so o dono troca`() = runTest(mainDispatcher) {
        val onChange = groupWithMonthly().copy(
            gameConfig = br.com.saqz.groups.domain.group.GroupGameConfig(
                attendanceUpdatesMode = br.com.saqz.groups.domain.group.AttendanceUpdatesMode.ON_CHANGE,
            ),
        )
        assertEquals(AttendanceUpdatesMode.ON_CHANGE, editViewModel(onChange).state.value.form.attendanceUpdatesMode)
        val admin = editViewModel(onChange.copy(role = GroupRole.ADMIN))
        assertEquals(false, admin.state.value.canChangeAttendanceUpdates)
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
    fun `salvar com tipo novo envia o tipo no pixLabel e a chave normalizada`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"), profileGateway = profileGateway)
        viewModel.onIntent(GroupSetupIntent.SelectPixType(PixKeyType.CPF))
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey("529.982.247-25"))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        val form = profileGateway.lastUpdateCommand?.form
        assertEquals("52998224725", form?.pixKey)
        assertEquals("CPF", form?.pixLabel)
    }

    @Test
    fun `e-mail vai em minusculas e sem espacos`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"), profileGateway = profileGateway)
        viewModel.onIntent(GroupSetupIntent.SelectPixType(PixKeyType.EMAIL))
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey("  Racha@Saqz.Test "))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals("racha@saqz.test", profileGateway.lastUpdateCommand?.form?.pixKey)
        assertEquals("E-mail", profileGateway.lastUpdateCommand?.form?.pixLabel)
    }

    @Test
    fun `telefone colado com mais 55 guarda so os 11 digitos`() = runTest(mainDispatcher) {
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"))
        viewModel.onIntent(GroupSetupIntent.SelectPixType(PixKeyType.PHONE))
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey("+55 (11) 98765-4321"))

        assertEquals("11987654321", viewModel.state.value.pixKey)
    }

    @Test
    fun `chave invalida para o tipo e recusada e nada e enviado`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"), profileGateway = profileGateway)
        viewModel.onIntent(GroupSetupIntent.SelectPixType(PixKeyType.CPF))
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey("11111111111"))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(GroupSetupError.PixKeyInvalid in viewModel.state.value.errors)
        assertNull(profileGateway.lastUpdateCommand)
    }

    @Test
    fun `chave sem tipo depois de mexer no Pix pede o tipo`() = runTest(mainDispatcher) {
        val viewModel = viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"))
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey("algo"))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertTrue(GroupSetupError.PixTypeRequired in viewModel.state.value.errors)
    }

    private fun openWithPix(key: String?, label: String?, gateway: FakeGroupProfileGateway = FakeGroupProfileGateway()): GroupSetupViewModel {
        val group = br.com.saqz.groups.presentation.sampleGroup().copy(
            profile = br.com.saqz.groups.presentation.sampleGroup().profile?.copy(pixKey = key, pixLabel = label),
        )
        gateway.readResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup(group))
        return viewModel(mode = GroupSetupMode.Edit(groupId = "grp-1"), profileGateway = gateway)
    }

    @Test
    fun `ao abrir o tipo vem do pixLabel quando e um dos quatro`() = runTest(mainDispatcher) {
        val viewModel = openWithPix("52998224725", "CPF")
        runCurrent()
        assertEquals(PixKeyType.CPF, viewModel.state.value.pixType)
        assertEquals("52998224725", viewModel.state.value.pixKey)
    }

    @Test
    fun `ao abrir o tipo e deduzido do formato da chave antiga`() = runTest(mainDispatcher) {
        listOf(
            "racha@saqz.test" to PixKeyType.EMAIL,
            "123e4567-e89b-12d3-a456-426614174000" to PixKeyType.RANDOM,
            "123e4567e89b12d3a456426614174000" to PixKeyType.RANDOM,
            "529.982.247-25" to PixKeyType.CPF,
            "11987654321" to PixKeyType.PHONE,
            "+55 11 98765-4321" to PixKeyType.PHONE,
        ).forEach { (key, expected) ->
            val viewModel = openWithPix(key, "Lucas Prado · Nubank")
            runCurrent()
            assertEquals(expected, viewModel.state.value.pixType, key)
        }
    }

    @Test
    fun `chave antiga em texto livre abre sem tipo e salvar sem mexer mantem os dois valores`() = runTest(mainDispatcher) {
        val gateway = FakeGroupProfileGateway()
        val viewModel = openWithPix("chave antiga", "Lucas Prado · Nubank", gateway)
        runCurrent()
        assertNull(viewModel.state.value.pixType)
        assertEquals("chave antiga", viewModel.state.value.pixKey)

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals("chave antiga", gateway.lastUpdateCommand?.form?.pixKey)
        assertEquals("Lucas Prado · Nubank", gateway.lastUpdateCommand?.form?.pixLabel)
    }

    @Test
    fun `chave deduzida salva sem mexer tambem mantem os valores antigos`() = runTest(mainDispatcher) {
        val gateway = FakeGroupProfileGateway()
        val viewModel = openWithPix("529.982.247-25", "Lucas Prado", gateway)
        runCurrent()

        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals("529.982.247-25", gateway.lastUpdateCommand?.form?.pixKey)
        assertEquals("Lucas Prado", gateway.lastUpdateCommand?.form?.pixLabel)
    }

    @Test
    fun `editar envia string vazia para limpar os campos Pix`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway()
        val viewModel = viewModel(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            profileGateway = profileGateway,
        )
        viewModel.onIntent(GroupSetupIntent.SelectPixType(PixKeyType.CPF))
        viewModel.onIntent(GroupSetupIntent.UpdatePixKey(""))
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
        runCurrent()

        val form = viewModel.state.value.form
        assertEquals(GroupTextLimits.NameMax, form.name.length)
        assertEquals(GroupTextLimits.DescriptionMax, form.description?.length)
        assertEquals(GroupTextLimits.CustomLevelMax, form.customLevel?.length)
        assertEquals(GroupTextLimits.VenueNameMax, form.defaultVenue?.name?.length)
        assertEquals(GroupTextLimits.VenueAddressMax, form.defaultVenue?.address?.length)
        assertEquals(GroupPixTextLimits.KeyMax, viewModel.state.value.pixKey?.length)
        // O que foi cortado também não vaza para o process death.
        assertEquals(GroupTextLimits.NameMax, handle.get<String>("group-setup-name")?.length)
        assertEquals(GroupPixTextLimits.KeyMax, handle.get<String>("group-setup-pix-key")?.length)
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
        gameFeeChoice: GameFeeChoice? = GameFeeChoice.NoFee,
    ) = GroupSetupViewModel(
        initialState = GroupSetupState(
            mode = mode,
            form = form,
            recurring = recurring,
            saveFailed = saveFailed,
            gameFeeChoice = gameFeeChoice,
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
            defaultNoGameFee = true,
        )
    }

    // ---- DEB-146 · JOGO-008: valor por jogo obrigatório ----

    @Test
    fun `nothing chosen in Valor por jogo saves the group without a fee`() = runTest {
        val profiles = FakeGroupProfileGateway()
        val vm = viewModel(form = completeForm.copy(defaultNoGameFee = false), gameFeeChoice = null, profileGateway = profiles)

        vm.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(emptySet(), vm.state.value.errors)
        val form = assertNotNull(profiles.lastCreateCommand).form
        assertNull(form.defaultGameFeeCents)
        assertEquals(false, form.defaultNoGameFee)
    }

    @Test
    fun `Cobrar diária without a fee or with zero does not save`() = runTest {
        val profiles = FakeGroupProfileGateway()
        val vm = viewModel(form = completeForm.copy(defaultNoGameFee = false), gameFeeChoice = null, profileGateway = profiles)
        vm.onIntent(GroupSetupIntent.SelectGameFeeChoice(GameFeeChoice.Charge))

        vm.onIntent(GroupSetupIntent.Submit)
        assertEquals(setOf(GroupSetupError.GameFeeInvalid), vm.state.value.errors)

        vm.onIntent(GroupSetupIntent.UpdateDefaultGameFee(0))
        vm.onIntent(GroupSetupIntent.Submit)
        assertEquals(setOf(GroupSetupError.GameFeeInvalid), vm.state.value.errors)
        assertNull(profiles.lastCreateCommand)
    }

    @Test
    fun `Criar grupo with a monthly fee fills Cobrar diária with the monthly fee divided by 4 - rounded to the real`() = runTest {
        val vm = viewModel(form = completeForm.copy(defaultNoGameFee = false, monthlyFeeCents = 9000), gameFeeChoice = null)

        vm.onIntent(GroupSetupIntent.SelectGameFeeChoice(GameFeeChoice.Charge))

        assertEquals(2300L, vm.state.value.form.defaultGameFeeCents, "R$ 90 ÷ 4 = R$ 22,50, arredondado a R$ 23")
    }

    @Test
    fun `Sem diária creates the group with the mark and no fee - Cobrar diária sends the fee with the mark off`() = runTest {
        val profiles = FakeGroupProfileGateway()
        viewModel(form = completeForm.copy(defaultGameFeeCents = 2000), profileGateway = profiles).onIntent(GroupSetupIntent.Submit)
        assertEquals(true, profiles.lastCreateCommand?.form?.defaultNoGameFee)
        assertNull(profiles.lastCreateCommand?.form?.defaultGameFeeCents, "o valor digitado antes não vai com Sem diária")

        val charged = FakeGroupProfileGateway()
        val vm = viewModel(form = completeForm.copy(defaultNoGameFee = false), gameFeeChoice = null, profileGateway = charged)
        vm.onIntent(GroupSetupIntent.SelectGameFeeChoice(GameFeeChoice.Charge))
        vm.onIntent(GroupSetupIntent.UpdateDefaultGameFee(2000))
        vm.onIntent(GroupSetupIntent.Submit)
        assertEquals(2000L, charged.lastCreateCommand?.form?.defaultGameFeeCents)
        assertEquals(false, charged.lastCreateCommand?.form?.defaultNoGameFee)
    }

    @Test
    fun `Editar grupo of a group without fee opens with nothing chosen and does not prefill the suggestion`() = runTest {
        val legacy = sampleGroup(financeDefaults = GroupFinanceDefaults(null, 8000, 10, gameFeeUndefined = true))
        val vm = viewModel(
            mode = GroupSetupMode.Edit("group-1"),
            profileGateway = FakeGroupProfileGateway(readResult = SaqzResult.Success(sampleVersionedGroup(legacy))),
        )

        assertNull(vm.state.value.gameFeeChoice)
        vm.onIntent(GroupSetupIntent.SelectGameFeeChoice(GameFeeChoice.Charge))
        assertNull(vm.state.value.form.defaultGameFeeCents, "fora do Criar grupo a conta fica só no apoio")
    }

    @Test
    fun `opened from the card or the notice - a group without fee comes with the suggestion filled`() = runTest {
        val legacy = sampleGroup(financeDefaults = GroupFinanceDefaults(null, 8000, 10, gameFeeUndefined = true))
        val vm = viewModel(
            mode = GroupSetupMode.Edit("group-1", focusGameFee = true),
            profileGateway = FakeGroupProfileGateway(readResult = SaqzResult.Success(sampleVersionedGroup(legacy))),
        )

        assertEquals(GameFeeChoice.Charge, vm.state.value.gameFeeChoice)
        assertEquals(2000L, vm.state.value.form.defaultGameFeeCents)

        val noMonthly = sampleGroup(financeDefaults = GroupFinanceDefaults(null, null, null, gameFeeUndefined = true))
        val empty = viewModel(
            mode = GroupSetupMode.Edit("group-1", focusGameFee = true),
            profileGateway = FakeGroupProfileGateway(readResult = SaqzResult.Success(sampleVersionedGroup(noMonthly))),
        )
        assertNull(empty.state.value.gameFeeChoice, "sem mensalidade, vazio")
    }

    @Test
    fun `Editar grupo keeps the saved choice`() = runTest {
        val charged = sampleGroup(financeDefaults = GroupFinanceDefaults(1500, null, null))
        val vm = viewModel(
            mode = GroupSetupMode.Edit("group-1"),
            profileGateway = FakeGroupProfileGateway(readResult = SaqzResult.Success(sampleVersionedGroup(charged))),
        )
        assertEquals(GameFeeChoice.Charge, vm.state.value.gameFeeChoice)
        assertEquals(1500L, vm.state.value.form.defaultGameFeeCents)
        val none = viewModel(mode = GroupSetupMode.Edit("group-1"))
        assertEquals(GameFeeChoice.NoFee, none.state.value.gameFeeChoice)
    }

    // ---- DEB-150 · PROM-010: pagamento antecipado ----

    @Test
    fun `advance payment only exists with Escolha manual and goes to the server`() = runTest {
        val profiles = FakeGroupProfileGateway()
        val vm = viewModel(mode = GroupSetupMode.Edit("group-1"), profileGateway = profiles)
        assertFalse(vm.state.value.showsAdvancePayment)
        vm.onIntent(GroupSetupIntent.ToggleAdvancePayment(true))
        assertFalse(vm.state.value.form.advancePaymentRequired, "em Ordem de chegada a chave não liga")

        vm.onIntent(GroupSetupIntent.SelectPromotionMode(PromotionMode.MANUAL))
        assertTrue(vm.state.value.showsAdvancePayment)
        vm.onIntent(GroupSetupIntent.ToggleAdvancePayment(true))
        vm.onIntent(GroupSetupIntent.Submit)

        assertEquals(true, profiles.lastUpdateCommand?.form?.advancePaymentRequired)
    }

    @Test
    fun `back to Ordem de chegada turns advance payment off with the notice`() = runTest {
        val profiles = FakeGroupProfileGateway()
        val vm = viewModel(mode = GroupSetupMode.Edit("group-1"), profileGateway = profiles)
        vm.onIntent(GroupSetupIntent.SelectPromotionMode(PromotionMode.MANUAL))
        vm.onIntent(GroupSetupIntent.ToggleAdvancePayment(true))

        vm.onIntent(GroupSetupIntent.SelectPromotionMode(PromotionMode.FIFO))

        assertFalse(vm.state.value.form.advancePaymentRequired)
        assertTrue(vm.state.value.advancePaymentTurnedOff)
        vm.onIntent(GroupSetupIntent.Submit)
        assertEquals(false, profiles.lastUpdateCommand?.form?.advancePaymentRequired)
    }

    @Test
    fun `cartao mensalidade aparece na edicao para quem gere e grava valor e dia no update`() = runTest(mainDispatcher) {
        val profileGateway = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup(groupWithMonthly(fee = null))),
        )
        val viewModel = editViewModel(groupWithMonthly(fee = null), profileGateway)
        assertEquals(true, viewModel.state.value.showsMonthlyFee)

        viewModel.onIntent(GroupSetupIntent.UpdateMonthlyFee(null))
        viewModel.onIntent(GroupSetupIntent.UpdateMonthlyDueDay(5))
        assertEquals(null, viewModel.state.value.form.monthlyDueDay)
        viewModel.onIntent(GroupSetupIntent.UpdateMonthlyFee(8_000))
        assertEquals(10, viewModel.state.value.form.monthlyDueDay)
        viewModel.onIntent(GroupSetupIntent.UpdateMonthlyDueDay(40))
        assertEquals(28, viewModel.state.value.form.monthlyDueDay)
        viewModel.onIntent(GroupSetupIntent.UpdateMonthlyDueDay(12))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(8_000, profileGateway.lastUpdateCommand?.form?.monthlyFeeCents)
        assertEquals(12, profileGateway.lastUpdateCommand?.form?.monthlyDueDay)
    }

    @Test
    fun `apagar o valor tira valor e dia e o cartao some na criacao`() = runTest(mainDispatcher) {
        val viewModel = editViewModel(groupWithMonthly())
        viewModel.onIntent(GroupSetupIntent.UpdateMonthlyFee(null))
        assertEquals(null, viewModel.state.value.form.monthlyFeeCents)
        assertEquals(null, viewModel.state.value.form.monthlyDueDay)
        assertEquals(false, viewModel(mode = GroupSetupMode.Create).state.value.showsMonthlyFee)
    }
}
