package br.com.saqz.groups.presentation.gameeditor

import br.com.saqz.groups.presentation.GameFeeChoice
import androidx.lifecycle.SavedStateHandle
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupTrialAccessInfo
import br.com.saqz.groups.domain.group.GroupTrialAccessPort
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleVersionedGame
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.presentation.ui.gameeditor.GameDateTimePicker
import br.com.saqz.groups.presentation.ui.gameeditor.PickerDay
import br.com.saqz.groups.presentation.ui.gameeditor.buildPickerDays
import br.com.saqz.groups.presentation.ui.gameeditor.pickerRangeStart
import br.com.saqz.groups.presentation.ui.gameeditor.pickerTodayAt
import br.com.saqz.groups.presentation.ui.gameeditor.pickerTimeParts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        gameId: String? = null,
        savedState: SavedStateHandle = SavedStateHandle(),
        gameGateway: FakeGameGateway = FakeGameGateway(),
        groupGateway: FakeGroupGateway = groupGateway { it },
        profileGateway: FakeGroupProfileGateway = FakeGroupProfileGateway(),
        trialAccess: GroupTrialAccessPort? = null,
        notificationPrompt: br.com.saqz.groups.presentation.communication.NotificationPromptOffer? = null,
        now: Instant = Instant.parse("2026-08-01T12:00:00Z"),
    ) = GameEditorViewModel(
        "group-1", gameId, savedState, gameGateway, groupGateway, profileGateway, trialAccess,
        notificationPrompt = notificationPrompt,
        now = { now },
    )

    private fun groupGateway(
        transform: (br.com.saqz.groups.domain.group.Group) -> br.com.saqz.groups.domain.group.Group,
    ) = FakeGroupGateway(
        readResult = SaqzResult.Success(
            br.com.saqz.groups.presentation.sampleVersionedGroup(
                // DEB-146: o grupo de exemplo já escolheu Sem diária; os testes do valor trocam isso.
                transform(br.com.saqz.groups.presentation.sampleGroup().copy(financeDefaults = NoFeeDefaults)),
            ),
        ),
    )

    // ---- Repetir toda semana (4a) ----

    @Test
    fun `create mode offers recurrence with the group slots and venue`() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.value.recurrenceOffered)
        assertTrue(vm.state.value.groupHasVenue)
        assertEquals(listOf(GroupWeekday.TUESDAY), vm.state.value.regularSlots.map { it.weekday })
        assertFalse(vm.state.value.form.recurring)
    }

    @Test
    fun `edit mode does not offer recurrence and ignores the toggle`() = runTest {
        val vm = viewModel(gameId = "game-1")
        vm.onIntent(GameEditorIntent.ToggleRecurring(true))
        assertFalse(vm.state.value.recurrenceOffered)
        assertFalse(vm.state.value.form.recurring)
    }

    @Test
    fun `incomplete profile does not offer recurrence`() = runTest {
        val vm = viewModel(groupGateway = groupGateway { it.copy(profile = null) })
        assertFalse(vm.state.value.recurrenceOffered)
    }

    @Test
    fun `recurring create publishes the game and then adds the slot to the group`() = runTest {
        // O backend devolve o jogo como rascunho; o editor publica antes de gravar a repetição.
        val draft = sampleVersionedGame(sampleGame().copy(status = GameStatus.Draft))
        val gameGateway = FakeGameGateway(createResult = SaqzResult.Success(draft))
        val profileGateway = FakeGroupProfileGateway()
        val vm = viewModel(gameGateway = gameGateway, profileGateway = profileGateway)

        // Quinta 06/08 às 20h, 90 min.
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.SelectDuration(90))
        vm.onIntent(GameEditorIntent.ToggleRecurring(true))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1, gameGateway.createCalls)
        assertEquals(GameLifecycleAction.Publish, gameGateway.lastLifecycleAction)
        val form = assertNotNull(profileGateway.lastUpdateCommand).form
        assertEquals(
            listOf(
                GroupRegularSlot(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120),
                GroupRegularSlot(weekday = GroupWeekday.THURSDAY, startTime = "20:00", durationMinutes = 90),
            ),
            form.regularSlots,
        )
        // O grupo já tem quadra: a deste jogo não a substitui.
        assertEquals("CERET", form.defaultVenue?.name)
        assertEquals("etag-2", profileGateway.lastUpdateCommand?.versionToken?.value)
        assertFalse(vm.state.value.isSaving)
        assertFalse(vm.state.value.recurrenceFailed)
        assertTrue(vm.effects.first() is GameEditorEffect.Saved)
    }

    @Test
    fun `recurring create gives the group this venue when it has none`() = runTest {
        val profileGateway = FakeGroupProfileGateway()
        val vm = viewModel(
            groupGateway = groupGateway { it.copy(profile = it.profile?.copy(defaultVenue = null)) },
            profileGateway = profileGateway,
        )
        assertFalse(vm.state.value.groupHasVenue)

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra da praça"))
        vm.onIntent(GameEditorIntent.UpdateVenueAddress("Praça da Sé, 1"))
        vm.onIntent(GameEditorIntent.ToggleRecurring(true))
        vm.onIntent(GameEditorIntent.Submit)

        val venue = assertNotNull(profileGateway.lastUpdateCommand?.form?.defaultVenue)
        assertEquals("Quadra da praça", venue.name)
        assertEquals("Praça da Sé, 1", venue.address)
    }

    @Test
    fun `recurring create skips the group update when the slot already exists`() = runTest {
        val profileGateway = FakeGroupProfileGateway()
        val vm = viewModel(profileGateway = profileGateway)

        // Terça 04/08 às 19h30: o grupo já se repete aí.
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.ToggleRecurring(true))
        vm.onIntent(GameEditorIntent.Submit)

        assertNull(profileGateway.lastUpdateCommand)
        assertFalse(vm.state.value.recurrenceFailed)
        assertEquals(GameEditorEffect.Saved(created = true), vm.effects.first())
    }

    @Test
    fun `recurrence failure keeps the published game and the retry does not create another`() = runTest {
        val gameGateway = FakeGameGateway()
        val profileGateway = FakeGroupProfileGateway(
            updateResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Server)),
        )
        val vm = viewModel(gameGateway = gameGateway, profileGateway = profileGateway)

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.ToggleRecurring(true))
        vm.onIntent(GameEditorIntent.Submit)

        assertTrue(vm.state.value.recurrenceFailed)
        assertFalse(vm.state.value.saveFailed)
        assertFalse(vm.state.value.isSaving)
        assertEquals(GroupUiError.Network, vm.state.value.error)

        profileGateway.updateResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup())
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1, gameGateway.createCalls)
        assertEquals(1, gameGateway.readCalls)
        assertFalse(vm.state.value.recurrenceFailed)
        assertTrue(vm.effects.first() is GameEditorEffect.Saved)
    }

    @Test
    fun `turning the toggle off after a recurrence failure saves without touching the group`() = runTest {
        val profileGateway = FakeGroupProfileGateway(
            updateResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Server)),
        )
        val vm = viewModel(profileGateway = profileGateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.ToggleRecurring(true))
        vm.onIntent(GameEditorIntent.Submit)
        assertTrue(vm.state.value.recurrenceFailed)

        vm.onIntent(GameEditorIntent.ToggleRecurring(false))
        assertFalse(vm.state.value.recurrenceFailed)
        vm.onIntent(GameEditorIntent.Submit)

        assertFalse(vm.state.value.recurrenceFailed)
        assertTrue(vm.effects.first() is GameEditorEffect.Saved)
    }

    @Test
    fun `recurring flag survives view model recreation`() = runTest {
        val savedState = SavedStateHandle()
        viewModel(savedState = savedState).onIntent(GameEditorIntent.ToggleRecurring(true))
        assertTrue(viewModel(savedState = savedState).state.value.form.recurring)
    }

    @Test
    fun `recurrence slot follows the chosen date time and duration`() {
        val slot = recurrenceSlotFor(
            GameEditorFields(localDate = "2026-08-09", localTime = "08:05", durationMinutes = 60),
        )
        assertEquals(GroupRegularSlot(weekday = GroupWeekday.SUNDAY, startTime = "08:05", durationMinutes = 60), slot)
        assertNull(recurrenceSlotFor(GameEditorFields(localDate = "2026-08-09")))
        assertNull(recurrenceSlotFor(GameEditorFields(localDate = "hoje", localTime = "08:05")))
    }

    @Test
    fun `new manual game uses the duration saved in the schedule`() = runTest {
        val group = br.com.saqz.groups.presentation.sampleGroup().let {
            it.copy(profile = it.profile?.copy(regularSlots = emptyList(), defaultDurationMinutes = 150))
        }
        val gateway = FakeGroupGateway(readResult = SaqzResult.Success(
            br.com.saqz.groups.presentation.sampleVersionedGroup(group),
        ))
        assertEquals(150, viewModel(groupGateway = gateway).state.value.form.durationMinutes)
    }

    @Test
    fun `create mode loads group defaults and does not read a game`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        assertFalse(vm.state.value.isLoading)
        assertEquals(0, gateway.readCalls)
        assertEquals("Vôlei do CERET", vm.state.value.groupName)
        assertEquals("Vôlei do CERET", vm.state.value.form.title)
        assertEquals(12, vm.state.value.form.capacity)
    }

    @Test
    fun `edit mode loads the game on top of group defaults`() = runTest {
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame()))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        assertFalse(vm.state.value.isLoading)
        assertEquals(1, gateway.readCalls)
        assertEquals("Jogo de terça", vm.state.value.form.title)
        assertEquals("2026-08-04", vm.state.value.form.localDate)
        assertEquals("19:30", vm.state.value.form.localTime)
    }

    @Test
    fun `duration chosen on the counter saves the minutes`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.SelectDuration(180))
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(180, gateway.lastCreateCommand?.durationMinutes)
    }

    @Test
    fun `duration of 8 hours saves 480`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.SelectDuration(480))
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(480, gateway.lastCreateCommand?.durationMinutes)
    }

    @Test
    fun `duration outside 1h to 8h gives a field error and does not save`() = runTest {
        listOf(45, 540).forEach { minutes ->
            val gateway = FakeGameGateway()
            val vm = viewModel(gameGateway = gateway)
            vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
            vm.onIntent(GameEditorIntent.SelectDuration(minutes))
            vm.onIntent(GameEditorIntent.Submit)
            assertEquals(0, gateway.createCalls, "$minutes não deveria salvar")
            assertEquals(setOf(GameEditorFieldError.DurationInvalid), vm.state.value.validationErrors)
        }
    }

    @Test
    fun `game saved with a duration outside the grid keeps that value`() = runTest {
        val game = br.com.saqz.groups.presentation.sampleGame().copy(durationMinutes = 195)
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(game)))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        assertEquals(195, vm.state.value.form.durationMinutes)
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(195, gateway.lastEditCommand?.durationMinutes)
    }

    @Test
    fun `chosen duration survives view model recreation`() = runTest {
        val handle = SavedStateHandle()
        viewModel(savedState = handle).onIntent(GameEditorIntent.SelectDuration(360))
        val restored = viewModel(savedState = handle)
        assertEquals(360, restored.state.value.form.durationMinutes)
    }

    @Test
    fun `saving a new game reports created and saving an edit does not`() = runTest {
        val creating = viewModel()
        creating.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        creating.onIntent(GameEditorIntent.Submit)
        assertEquals(GameEditorEffect.Saved(created = true), creating.effects.first())

        val editing = viewModel(
            gameId = "game-1",
            gameGateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame())),
        )
        editing.onIntent(GameEditorIntent.Submit)
        assertEquals(GameEditorEffect.Saved(created = false), editing.effects.first())
    }

    @Test
    fun `creating a game offers the notification prompt to the organizer and editing one does not`() = runTest {
        val offers = br.com.saqz.groups.presentation.RecordingNotificationPromptOffer()
        val editing = viewModel(
            gameId = "game-1",
            gameGateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame())),
            notificationPrompt = offers,
        )
        editing.onIntent(GameEditorIntent.Submit)
        assertTrue(offers.offered.isEmpty())

        val creating = viewModel(notificationPrompt = offers)
        creating.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        creating.onIntent(GameEditorIntent.Submit)
        assertEquals(
            listOf(br.com.saqz.groups.presentation.communication.NotificationPromptAudience.Organizer),
            offers.offered,
        )
    }

    @Test
    fun `edit mode preserves the existing game fee in the write command`() = runTest {
        val game = sampleGame().copy(gameFeeCents = 2500)
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(game)))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)

        assertEquals(2500, vm.state.value.form.gameFeeCents)
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(2500, gateway.lastEditCommand?.gameFeeCents)
        assertFalse(gateway.lastEditCommand?.useDefaultGameFee ?: true)
    }

    @Test
    fun `group gateway failure is visible and typed`() = runTest {
        val vm = viewModel(
            groupGateway = FakeGroupGateway(readResult = SaqzResult.Failure(
                br.com.saqz.groups.domain.group.GroupProfileError.DataFailure(DataError.Forbidden),
            )),
        )
        assertTrue(vm.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, vm.state.value.error)
    }

    @Test
    fun `game gateway failure is visible and typed`() = runTest {
        val vm = viewModel(
            gameId = "game-1",
            gameGateway = FakeGameGateway(readResult = SaqzResult.Failure(GameError.Data(DataError.Forbidden))),
        )
        assertTrue(vm.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, vm.state.value.error)
    }

    @Test
    fun `retry after failure reloads and succeeds`() = runTest {
        val first = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val second = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(first, second)))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        first.complete(SaqzResult.Failure(GameError.Data(DataError.Forbidden)))
        assertTrue(vm.state.value.loadFailed)
        vm.onIntent(GameEditorIntent.Retry)
        second.complete(SaqzResult.Success(sampleVersionedGame()))
        assertFalse(vm.state.value.run { loadFailed || isLoading })
    }

    @Test
    fun `stale read response cannot replace a newer generation`() = runTest {
        val old = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val fresh = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(old, fresh)))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        vm.onIntent(GameEditorIntent.Retry)
        gateway.completeRead(1, SaqzResult.Success(sampleVersionedGame()))
        old.complete(SaqzResult.Failure(GameError.Data(DataError.Forbidden)))
        assertFalse(vm.state.value.run { loadFailed || isLoading })
    }

    @Test
    fun `version conflict reloads the current game version instead of showing duplicate game`() = runTest {
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(sampleVersionedGame()),
            editResult = SaqzResult.Failure(GameError.VersionConflict),
        )
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        gateway.readResult = SaqzResult.Success(
            sampleVersionedGame().copy(
                game = sampleVersionedGame().game.copy(title = "Título atualizado"),
                version = GameVersionToken("etag-new"),
            ),
        )
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(2, gateway.readCalls)
        assertEquals("etag-new", vm.state.value.versionToken)
        assertEquals("Título atualizado", vm.state.value.form.title)
        assertFalse(vm.state.value.hasConflict)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `submit with missing date does not call the gateway`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SelectDuration(90))
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(0, gateway.createCalls)
        assertEquals(
            setOf(GameEditorFieldError.DateMissing, GameEditorFieldError.TimeMissing),
            vm.state.value.validationErrors,
        )
    }

    @Test
    fun `submit with date and time creates the game`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.SelectDuration(120))
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(1, gateway.createCalls)
        val command = gateway.lastCreateCommand
        assertNotNull(command)
        assertEquals("2026-08-04", command?.localDate)
        assertEquals("19:30", command?.localTime)
        assertEquals(120, command?.durationMinutes)
    }

    @Test
    fun `successful create publishes the returned draft`() = runTest {
        val draft = sampleVersionedGame(
            sampleGame().copy(status = GameStatus.Draft),
        ).copy(version = GameVersionToken("etag-draft"))
        val gateway = FakeGameGateway(createResult = SaqzResult.Success(draft))
        val vm = viewModel(gameGateway = gateway)

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(GameLifecycleAction.Publish, gateway.lastLifecycleAction)
        assertEquals(listOf(GameVersionToken("etag-draft")), gateway.lifecycleVersions)
        assertFalse(vm.state.value.isSaving)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun `failed publish edits the existing draft after a form change`() = runTest {
        val draft = sampleVersionedGame(
            sampleGame().copy(status = GameStatus.Draft),
        ).copy(version = GameVersionToken("etag-draft"))
        val editedDraft = draft.copy(version = GameVersionToken("etag-edited"))
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(draft),
            createResult = SaqzResult.Success(draft),
            editResult = SaqzResult.Success(editedDraft),
            lifecycleResults = ArrayDeque(
                listOf(
                    SaqzResult.Failure(GameError.Data(DataError.Server)),
                    SaqzResult.Success(editedDraft.copy(game = editedDraft.game.copy(status = GameStatus.Published))),
                ),
            ),
        )
        val vm = viewModel(gameGateway = gateway)

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertTrue(vm.state.value.saveFailed)

        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra corrigida"))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1, gateway.createCalls)
        assertEquals(1, gateway.editCalls)
        assertEquals("game-1", gateway.lastEditGameId)
        assertEquals(listOf(GameVersionToken("etag-draft")), gateway.editVersions)
        assertEquals(
            listOf(GameVersionToken("etag-draft"), GameVersionToken("etag-edited")),
            gateway.lifecycleVersions,
        )
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun `retry rereads a published draft and edits form changes before succeeding`() = runTest {
        val draft = sampleVersionedGame(
            sampleGame().copy(status = GameStatus.Draft),
        ).copy(version = GameVersionToken("etag-draft"))
        val published = draft.copy(
            version = GameVersionToken("etag-published"),
            game = draft.game.copy(status = GameStatus.Published),
        )
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(published),
            createResult = SaqzResult.Success(draft),
            editResult = SaqzResult.Success(published.copy(version = GameVersionToken("etag-edited"))),
            lifecycleResults = ArrayDeque(
                listOf(SaqzResult.Failure(GameError.Data(DataError.Connectivity))),
            ),
        )
        val vm = viewModel(gameGateway = gateway)

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra corrigida"))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1, gateway.createCalls)
        assertEquals(1, gateway.readCalls)
        assertEquals(1, gateway.editCalls)
        assertEquals("game-1", gateway.lastEditGameId)
        assertEquals(listOf(GameVersionToken("etag-published")), gateway.editVersions)
        assertEquals("Quadra corrigida", gateway.lastEditCommand?.venue?.name)
        assertEquals(listOf(GameVersionToken("etag-draft")), gateway.lifecycleVersions)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun `retry with group default fee recognizes own published draft`() = runTest {
        val submittedGame = sampleGame().copy(
            title = "Vôlei do CERET",
            startsAt = "2026-08-04T22:30:00Z",
            confirmationDeadline = "2026-08-04T16:30:00Z",
            gameFeeCents = 2500,
            noGameFee = false,
            status = GameStatus.Draft,
        )
        val draft = sampleVersionedGame(
            submittedGame,
        ).copy(version = GameVersionToken("etag-draft"))
        val published = draft.copy(
            version = GameVersionToken("etag-published"),
            game = draft.game.copy(status = GameStatus.Published),
        )
        val gateway = FakeGameGateway(
            readResults = ArrayDeque(listOf(SaqzResult.Success(published))),
            createResult = SaqzResult.Success(draft),
            lifecycleResults = ArrayDeque(
                listOf(SaqzResult.Failure(GameError.Data(DataError.Connectivity))),
            ),
        )
        val groupGateway = FakeGroupGateway(
            readResult = SaqzResult.Success(
                br.com.saqz.groups.presentation.sampleVersionedGroup(
                    br.com.saqz.groups.presentation.sampleGroup().copy(
                        financeDefaults = GroupFinanceDefaults(2500, null, null),
                    ),
                ),
            ),
        )
        val vm = viewModel(gameGateway = gateway, groupGateway = groupGateway)

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertTrue(vm.state.value.saveFailed)
        // O campo já vem com o padrão do grupo e o que está nele é enviado, sem "usar o padrão".
        assertEquals(2500, gateway.lastCreateCommand?.gameFeeCents)
        assertTrue(gateway.lastCreateCommand?.useDefaultGameFee == false)

        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1, gateway.createCalls)
        assertEquals(1, gateway.readCalls)
        assertEquals(0, gateway.editCalls)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun `retry does not overwrite a concurrent published edit`() = runTest {
        val draft = sampleVersionedGame(
            sampleGame().copy(status = GameStatus.Draft),
        ).copy(version = GameVersionToken("etag-draft"))
        val published = draft.copy(
            version = GameVersionToken("etag-published"),
            game = draft.game.copy(status = GameStatus.Published),
        )
        val collaboratorEdit = published.copy(
            version = GameVersionToken("etag-collaborator"),
            game = published.game.copy(title = "Título do outro organizador"),
        )
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(collaboratorEdit),
            createResult = SaqzResult.Success(draft),
            lifecycleResults = ArrayDeque(
                listOf(SaqzResult.Failure(GameError.Data(DataError.Connectivity))),
            ),
        )
        val vm = viewModel(gameGateway = gateway)

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra corrigida"))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1, gateway.createCalls)
        assertEquals(1, gateway.readCalls)
        assertEquals(0, gateway.editCalls)
        assertEquals(listOf(GameVersionToken("etag-draft")), gateway.lifecycleVersions)
        assertTrue(vm.state.value.saveFailed)
        assertEquals(GroupUiError.Conflict, vm.state.value.error)
    }

    @Test
    fun `pending draft identity survives view model recreation`() = runTest {
        val savedState = SavedStateHandle()
        val draft = sampleVersionedGame(
            sampleGame().copy(status = GameStatus.Draft),
        ).copy(version = GameVersionToken("etag-draft"))
        val firstGateway = FakeGameGateway(
            createResult = SaqzResult.Success(draft),
            lifecycleResults = ArrayDeque(
                listOf(SaqzResult.Failure(GameError.Data(DataError.Connectivity))),
            ),
        )
        val first = viewModel(savedState = savedState, gameGateway = firstGateway)
        first.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        first.onIntent(GameEditorIntent.Submit)

        val refreshedDraft = draft.copy(version = GameVersionToken("etag-refreshed"))
        val editedDraft = refreshedDraft.copy(version = GameVersionToken("etag-edited"))
        val secondGateway = FakeGameGateway(
            readResult = SaqzResult.Success(refreshedDraft),
            editResult = SaqzResult.Success(editedDraft),
            lifecycleResults = ArrayDeque(
                listOf(
                    SaqzResult.Success(
                        editedDraft.copy(game = editedDraft.game.copy(status = GameStatus.Published)),
                    ),
                ),
            ),
        )
        val recreated = viewModel(savedState = savedState, gameGateway = secondGateway)
        recreated.onIntent(GameEditorIntent.UpdateVenueName("Quadra corrigida"))
        recreated.onIntent(GameEditorIntent.Submit)

        assertEquals(0, secondGateway.createCalls)
        assertEquals(1, secondGateway.readCalls)
        assertEquals(1, secondGateway.editCalls)
        assertEquals("game-1", secondGateway.lastEditGameId)
        assertEquals(listOf(GameVersionToken("etag-refreshed")), secondGateway.editVersions)
        assertFalse(recreated.state.value.saveFailed)
    }

    @Test
    fun `retry without form changes reuses the command key`() = runTest {
        val gateway = FakeGameGateway(
            createResult = SaqzResult.Failure(GameError.Data(DataError.Server)),
        )
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        val firstKey = gateway.lastCreateCommand?.requestId
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(firstKey, gateway.lastCreateCommand?.requestId)
    }

    @Test
    fun `changing the form rotates the command key before retry`() = runTest {
        val gateway = FakeGameGateway(
            createResult = SaqzResult.Failure(GameError.Data(DataError.Server)),
        )
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        val firstKey = gateway.lastCreateCommand?.requestId

        gateway.createResult = SaqzResult.Success(sampleVersionedGame())
        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra corrigida"))
        vm.onIntent(GameEditorIntent.Submit)

        assertNotEquals(firstKey, gateway.lastCreateCommand?.requestId)
    }

    @Test
    fun `venue validation requires the backend minimum lengths`() {
        val errors = validateGameEditor(
            GameEditorFields(
                localDate = "2026-08-04",
                localTime = "19:30",
                venue = GameVenue(name = "A", address = "Rua"),
            ),
        )

        assertTrue(GameEditorFieldError.VenueNameMissing in errors)
        assertTrue(GameEditorFieldError.VenueAddressTooShort in errors)
    }

    @Test
    fun `venue address is optional`() {
        val blank = GameEditorFields(
            localDate = "2026-08-04", localTime = "19:30", venue = GameVenue(name = "CERET", address = ""),
            gameFeeChoice = GameFeeChoice.NoFee,
        )
        val absent = blank.copy(venue = null)

        assertTrue(validateGameEditor(blank).isEmpty())
        assertEquals(setOf(GameEditorFieldError.VenueNameMissing), validateGameEditor(absent))
    }

    @Test
    fun `a group without a venue gets the first game's venue even without recurrence`() = runTest {
        val profileGateway = FakeGroupProfileGateway()
        val vm = viewModel(
            groupGateway = groupGateway { it.copy(profile = it.profile?.copy(defaultVenue = null)) },
            profileGateway = profileGateway,
        )

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra da praça"))
        vm.onIntent(GameEditorIntent.Submit)

        val form = assertNotNull(profileGateway.lastUpdateCommand?.form)
        assertEquals("Quadra da praça", form.defaultVenue?.name)
        assertEquals("", form.defaultVenue?.address)
        assertTrue(vm.effects.first() is GameEditorEffect.Saved)
    }

    @Test
    fun `a group that has a venue is left alone`() = runTest {
        val profileGateway = FakeGroupProfileGateway()
        val vm = viewModel(profileGateway = profileGateway)

        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.Submit)

        assertNull(profileGateway.lastUpdateCommand)
    }

    @Test
    fun `failing to save the default venue does not fail the game`() = runTest {
        val profileGateway = FakeGroupProfileGateway(
            updateResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Connectivity)),
        )
        val vm = viewModel(
            groupGateway = groupGateway { it.copy(profile = it.profile?.copy(defaultVenue = null)) },
            profileGateway = profileGateway,
        )
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "20:00"))
        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra da praça"))
        vm.onIntent(GameEditorIntent.Submit)

        assertFalse(vm.state.value.recurrenceFailed)
        assertTrue(vm.effects.first() is GameEditorEffect.Saved)
    }

    @Test
    fun `an invalid submit counts the attempt so the screen scrolls again`() = runTest {
        val vm = viewModel(
            groupGateway = groupGateway { it.copy(profile = it.profile?.copy(defaultVenue = null)) },
        )
        vm.onIntent(GameEditorIntent.Submit)
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(2, vm.state.value.submitAttempts)
        assertTrue(GameEditorFieldError.VenueNameMissing in vm.state.value.validationErrors)
    }

    @Test
    fun `venue validation requires the backend maximum lengths`() {
        val errors = validateGameEditor(
            GameEditorFields(
                localDate = "2026-08-04",
                localTime = "19:30",
                venue = GameVenue(name = "N".repeat(121), address = "R".repeat(301)),
            ),
        )

        assertTrue(GameEditorFieldError.VenueNameTooLong in errors)
        assertTrue(GameEditorFieldError.VenueAddressTooLong in errors)
    }

    @Test
    fun `successful create emits Saved and clears the command key`() = runTest {
        val vm = viewModel()
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertFalse(vm.state.value.isSaving)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun `conflict on create surfaces conflictGameId and no save failure`() = runTest {
        val gateway = FakeGameGateway(
            createResult = SaqzResult.Failure(GameError.Conflict("game-existing")),
        )
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertFalse(vm.state.value.isSaving)
        assertFalse(vm.state.value.saveFailed)
        assertEquals("game-existing", vm.state.value.conflictGameId)
    }

    @Test
    fun `conflict without gameId surfaces conflict state with null id`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.Conflict(null)))
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertNull(vm.state.value.conflictGameId)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun `dismiss conflict clears conflictGameId`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.Conflict("game-x")))
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        vm.onIntent(GameEditorIntent.DismissConflict)
        assertNull(vm.state.value.conflictGameId)
    }

    @Test
    fun `open existing game emits OpenGameDetail with conflict id`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.Conflict("game-existing")))
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        vm.onIntent(GameEditorIntent.OpenExistingGame)
        val effect = vm.effects.first()
        assertTrue(effect is GameEditorEffect.OpenGameDetail)
        assertEquals("game-existing", (effect as GameEditorEffect.OpenGameDetail).gameId)
    }

    @Test
    fun `save failure surfaces saveFailed and error`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.Data(DataError.Server)))
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertTrue(vm.state.value.saveFailed)
        assertEquals(GroupUiError.Network, vm.state.value.error)
    }

    @Test
    fun `subscription required on create opens the plan dialog instead of the retry banner`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.Data(DataError.SubscriptionRequired)))
        val vm = viewModel(
            gameGateway = gateway,
            trialAccess = GroupTrialAccessPort { GroupTrialAccessInfo("2026-08-01T00:00:00Z", true, true, trialEnded = true) },
        )
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertTrue(vm.state.value.subscriptionRequired)
        assertTrue(vm.state.value.trialEnded)
        assertFalse(vm.state.value.saveFailed)
        assertFalse(vm.state.value.isSaving)
        // "Agora não" fecha e mantém o formulário preenchido.
        vm.onIntent(GameEditorIntent.DismissSubscriptionRequired)
        assertFalse(vm.state.value.subscriptionRequired)
        assertEquals("2026-08-04", vm.state.value.form.localDate)
        assertEquals("19:30", vm.state.value.form.localTime)
    }

    @Test
    fun `subscription required on edit opens the plan dialog`() = runTest {
        val gateway = FakeGameGateway(editResult = SaqzResult.Failure(GameError.Data(DataError.SubscriptionRequired)))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(1, gateway.editCalls)
        assertTrue(vm.state.value.subscriptionRequired)
        assertFalse(vm.state.value.trialEnded)
        assertFalse(vm.state.value.saveFailed)
    }

    // DEB-075: o limite do plano ou do teste bloqueia, 0h do dia do fim no fuso do grupo (São Paulo nos testes).

    @Test
    fun `a game at the plan limit or later is blocked while the day before is free and no create goes out`() = runTest {
        val gateway = FakeGameGateway()
        // Plano pago até 02/08: o limite é 02/08 0h em São Paulo = 03h UTC.
        val info = GroupTrialAccessInfo("2026-08-02T15:00:00Z", false, true, planActive = true, gamesUntil = "2026-08-02T03:00:00Z")
        val vm = viewModel(gameGateway = gateway, trialAccess = GroupTrialAccessPort { info })
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-01", "23:59"))
        assertFalse(vm.state.value.planBlocked)
        assertEquals("2026-08-01", vm.state.value.planLastDay)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-02", "00:00"))
        assertTrue(vm.state.value.planBlocked)
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(0, gateway.createCalls)
        assertFalse(vm.state.value.planLimitIsTrial)
        assertTrue(vm.state.value.isOwner)
    }

    @Test
    fun `a trial from an older server blocks from the zero hour of the trial end day and names it a trial`() = runTest {
        val info = GroupTrialAccessInfo("2026-08-04T15:00:00Z", false, true, activeTrialDays = 14)
        val vm = viewModel(trialAccess = GroupTrialAccessPort { info })
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-03", "23:00"))
        assertFalse(vm.state.value.planBlocked)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "00:00"))
        assertTrue(vm.state.value.planBlocked)
        assertTrue(vm.state.value.planLimitIsTrial)
        assertEquals("2026-08-03", vm.state.value.planLastDay)
    }

    @Test
    fun `refreshing the plan after subscribing releases the block without reloading the form`() = runTest {
        var info = GroupTrialAccessInfo("2026-08-01T00:00:00Z", false, true, activeTrialDays = 14)
        val vm = viewModel(trialAccess = GroupTrialAccessPort { info })
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        assertTrue(vm.state.value.planBlocked)
        info = info.copy(planActive = true, activeTrialDays = null, gamesUntil = "2026-09-04T03:00:00Z")
        vm.onIntent(GameEditorIntent.RefreshPlan)
        assertFalse(vm.state.value.planBlocked)
        assertEquals("2026-08-04", vm.state.value.form.localDate)
    }

    @Test
    fun `an admin without the right to subscribe is blocked and told it is the group plan`() = runTest {
        val info = GroupTrialAccessInfo("2026-08-02T15:00:00Z", false, false, planActive = true, gamesUntil = "2026-08-02T03:00:00Z")
        val vm = viewModel(trialAccess = GroupTrialAccessPort { info })
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-03", "19:30"))
        assertTrue(vm.state.value.planBlocked)
        assertFalse(vm.state.value.isOwner)
    }

    @Test
    fun `the server after plan end error leads to the block and not to the retry banner`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.AfterPlanEnd("2026-08-02T03:00:00Z")))
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(1, gateway.createCalls)
        assertTrue(vm.state.value.planBlocked)
        assertEquals("2026-08-01", vm.state.value.planLastDay)
        assertFalse(vm.state.value.saveFailed)
        assertFalse(vm.state.value.isSaving)
        // O dia anterior ao fim libera.
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-01", "19:30"))
        assertFalse(vm.state.value.planBlocked)
    }

    // DEB-075: 422 sem data e sem limite conhecido no app vira o texto aprovado "_passed" do papel, nunca o erro genérico.
    private fun refusedWithoutLimit(info: GroupTrialAccessInfo?): GameEditorViewModel {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.AfterPlanEnd(null)))
        val vm = viewModel(gameGateway = gateway, trialAccess = info?.let { GroupTrialAccessPort { _ -> it } })
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        assertFalse(vm.state.value.planBlocked)
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(1, gateway.createCalls)
        assertFalse(vm.state.value.saveFailed)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.planBlocked)
        assertTrue(vm.state.value.planLastDayPassed)
        assertNull(vm.state.value.planLastDay)
        return vm
    }

    @Test
    fun `a 422 without a date for an owner in the trial blocks with the trial passed text`() = runTest {
        val vm = refusedWithoutLimit(GroupTrialAccessInfo(null, false, true, activeTrialDays = 14))
        assertTrue(vm.state.value.isOwner)
        assertTrue(vm.state.value.planLimitIsTrial)
    }

    @Test
    fun `a 422 without a date for an owner with a plan blocks with the plan passed text`() = runTest {
        val vm = refusedWithoutLimit(GroupTrialAccessInfo(null, false, true, planActive = true))
        assertTrue(vm.state.value.isOwner)
        assertFalse(vm.state.value.planLimitIsTrial)
    }

    @Test
    fun `a 422 without a date for an admin blocks with the group plan text`() = runTest {
        val vm = refusedWithoutLimit(GroupTrialAccessInfo(null, false, false, planActive = true))
        assertFalse(vm.state.value.isOwner)
    }

    @Test
    fun `a 422 without a date releases the block once another start is chosen`() = runTest {
        val vm = refusedWithoutLimit(null)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-03", "19:30"))
        assertFalse(vm.state.value.planBlocked)
        assertFalse(vm.state.value.planLastDayPassed)
    }

    private fun planEndEvents(block: () -> Unit): List<Map<String, String>> {
        val recorded = mutableListOf<Pair<String, Map<String, String>>>()
        SaqzAnalytics.track = { name, params -> recorded += name to params }
        try {
            block()
        } finally {
            SaqzAnalytics.reset()
        }
        return recorded.filter { it.first == "game_after_plan_end" }.map { it.second }
    }

    @Test
    fun `the server after plan end on create reports the attempted date the limit and the action`() = runTest {
        val events = planEndEvents {
            val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.AfterPlanEnd("2026-08-02T03:00:00Z")))
            val vm = viewModel(gameGateway = gateway)
            vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
            vm.onIntent(GameEditorIntent.Submit)
        }

        assertEquals(
            listOf(
                mapOf(
                    "attempted_date" to "2026-08-04",
                    "limit_date" to "2026-08-02",
                    "error_code" to "GAME_AFTER_PLAN_END",
                    "action" to "create",
                ),
            ),
            events,
        )
    }

    @Test
    fun `the server after plan end on edit and on publish report their own action and omit an unknown limit`() = runTest {
        val onEdit = planEndEvents {
            val gateway = FakeGameGateway(editResult = SaqzResult.Failure(GameError.AfterPlanEnd(null)))
            val vm = viewModel(gameId = "game-1", gameGateway = gateway)
            vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-05", "19:30"))
            vm.onIntent(GameEditorIntent.Submit)
        }
        assertEquals(
            listOf(mapOf("attempted_date" to "2026-08-05", "error_code" to "GAME_AFTER_PLAN_END", "action" to "edit")),
            onEdit,
        )

        val onPublish = planEndEvents {
            val draft = sampleVersionedGame().copy(game = sampleGame().copy(status = GameStatus.Draft))
            val gateway = FakeGameGateway(
                createResult = SaqzResult.Success(draft),
                lifecycleResult = SaqzResult.Failure(GameError.AfterPlanEnd(null)),
            )
            val vm = viewModel(gameGateway = gateway)
            vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-06", "19:30"))
            vm.onIntent(GameEditorIntent.Submit)
        }
        assertEquals(
            listOf(mapOf("attempted_date" to "2026-08-06", "error_code" to "GAME_AFTER_PLAN_END", "action" to "publish")),
            onPublish,
        )
    }

    @Test
    fun `an expired trial keeps going to the subscription dialog and not to the block`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.Data(DataError.SubscriptionRequired)))
        val vm = viewModel(
            gameGateway = gateway,
            trialAccess = GroupTrialAccessPort { GroupTrialAccessInfo("2026-08-01T00:00:00Z", true, true, trialEnded = true) },
        )
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        assertFalse(vm.state.value.planBlocked)
        vm.onIntent(GameEditorIntent.Submit)
        assertTrue(vm.state.value.subscriptionRequired)
    }

    @Test
    fun `when the last day to book has already passed the state says so and while it is today or later it does not`() = runTest {
        // Teste que acaba hoje (01/08, 12h UTC = 09h em São Paulo): o limite é 0h de hoje e o último dia foi ontem.
        val info = GroupTrialAccessInfo("2026-08-01T15:00:00Z", false, true, activeTrialDays = 14, gamesUntil = "2026-08-01T03:00:00Z")
        val vm = viewModel(trialAccess = GroupTrialAccessPort { info })
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-05", "19:30"))
        assertTrue(vm.state.value.planBlocked)
        assertEquals("2026-07-31", vm.state.value.planLastDay)
        assertTrue(vm.state.value.planLastDayPassed)

        // Termina amanhã: ainda dá para marcar hoje, o texto segue com a data.
        val tomorrow = info.copy(gamesUntil = "2026-08-02T03:00:00Z")
        val later = viewModel(trialAccess = GroupTrialAccessPort { tomorrow })
        later.onIntent(GameEditorIntent.SaveDateTime("2026-08-05", "19:30"))
        assertEquals("2026-08-01", later.state.value.planLastDay)
        assertFalse(later.state.value.planLastDayPassed)
    }

    @Test
    fun `editing only blocks when the date or the time changed`() = runTest {
        // Jogo antigo em 2026-08-12 19:30, além do limite de 02/08: salvar outro campo não bloqueia.
        val info = GroupTrialAccessInfo("2026-08-02T15:00:00Z", false, true, planActive = true, gamesUntil = "2026-08-02T03:00:00Z")
        val vm = viewModel(gameId = "game-1", trialAccess = GroupTrialAccessPort { info })
        assertFalse(vm.state.value.planBlocked)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-13", "19:30"))
        assertTrue(vm.state.value.planBlocked)
        vm.onIntent(GameEditorIntent.SaveDateTime(vm.state.value.originalStart!!.substringBefore('T'), vm.state.value.originalStart!!.substringAfter('T')))
        assertFalse(vm.state.value.planBlocked)
    }

    @Test
    fun `reconfirming the same start in the picker does not block an old game even when the server sends seconds`() = runTest {
        val old = sampleGame().copy(localDate = "2026-08-12", localTime = "19:30:00", startsAt = "2026-08-12T19:30:00-03:00")
        val info = GroupTrialAccessInfo("2026-08-02T15:00:00Z", false, true, planActive = true, gamesUntil = "2026-08-02T03:00:00Z")
        val vm = viewModel(
            gameId = "game-1",
            gameGateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(old))),
            trialAccess = GroupTrialAccessPort { info },
        )
        assertFalse(vm.state.value.planBlocked)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-12", "19:30"))
        assertFalse(vm.state.value.planBlocked)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-12", "19:45"))
        assertTrue(vm.state.value.planBlocked)
    }

    @Test
    fun `save failure can be retried successfully`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.Data(DataError.Server)))
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.Submit)
        gateway.createResult = SaqzResult.Success(sampleVersionedGame())
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(2, gateway.createCalls)
        assertFalse(vm.state.value.saveFailed)
    }

    @Test
    fun `edit mode submit calls edit instead of create`() = runTest {
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame()))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-05", "20:00"))
        vm.onIntent(GameEditorIntent.Submit)
        assertEquals(0, gateway.createCalls)
        assertEquals(1, gateway.editCalls)
        assertEquals("2026-08-05", gateway.lastEditCommand?.localDate)
        assertEquals("Jogo de terça", gateway.lastEditCommand?.title)
    }

    @Test
    fun `saved form edits are restored over the server response`() = runTest {
        val savedState = SavedStateHandle()
        val first = viewModel(gameId = "game-1", savedState = savedState)
        first.onIntent(GameEditorIntent.SaveDateTime("2026-08-10", "20:15"))
        first.onIntent(GameEditorIntent.UpdateVenueName("Quadra preservada"))

        val recreated = viewModel(gameId = "game-1", savedState = savedState)
        assertEquals("2026-08-10", recreated.state.value.form.localDate)
        assertEquals("20:15", recreated.state.value.form.localTime)
        assertEquals("Quadra preservada", recreated.state.value.form.venue?.name)
    }

    // DEB-051: o "Recado para a galera" saiu do editor; o app não envia mais `notes`.
    @Test
    fun `the create command never carries a note`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.Submit)

        assertNull(gateway.lastCreateCommand?.notes)
    }

    @Test
    fun `editing a game that already has a note does not send it back`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)

        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1, gateway.editCalls)
        assertNull(gateway.lastEditCommand?.notes)
    }

    @Test
    fun `capacity cannot drop below minimum of 4`() = runTest {
        val vm = viewModel()
        listOf(0, 2, 3).forEach { value ->
            vm.onIntent(GameEditorIntent.UpdateCapacity(value))
            assertEquals(4, vm.state.value.form.capacity)
        }
        vm.onIntent(GameEditorIntent.UpdateCapacity(100))
        assertEquals(100, vm.state.value.form.capacity)
    }

    @Test
    fun `new game starts at the minimum when the group default is an old 2 or 3`() = runTest {
        listOf(2, 3).forEach { old ->
            val group = br.com.saqz.groups.presentation.sampleGroup().let {
                it.copy(profile = it.profile?.copy(defaultCapacity = old))
            }
            val gateway = FakeGroupGateway(readResult = SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedGroup(group)))
            assertEquals(4, viewModel(groupGateway = gateway).state.value.form.capacity)
        }
    }

    @Test
    fun `old game with 3 spots opens at the minimum and explains it`() = runTest {
        val old = sampleGame().copy(capacity = 3)
        val vm = viewModel(gameId = "game-1", gameGateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(old))))

        assertEquals(4, vm.state.value.form.capacity)
        assertEquals(3, vm.state.value.legacyCapacity)
    }

    @Test
    fun `game already at 4 or more does not show the legacy warning`() = runTest {
        val vm = viewModel(gameId = "game-1", gameGateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame(sampleGame().copy(capacity = 4)))))

        assertEquals(4, vm.state.value.form.capacity)
        assertNull(vm.state.value.legacyCapacity)
    }

    @Test
    fun `retry in create mode reloads group defaults`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.Retry)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `picker range contains a date more than thirty days ahead`() {
        val today = LocalDate.parse("2026-08-04")
        val selected = "2026-10-01"
        val start = pickerRangeStart(today, selected)
        val days = buildPickerDays(start, 30, List(7) { "weekday" }, List(12) { "month" })
        assertTrue(days.any { it.isoDate == selected })
        assertTrue(days.indexOfFirst { it.isoDate == selected } < days.lastIndex)
    }

    @Test
    fun `picker today uses the group timezone`() {
        val now = Instant.parse("2026-08-04T01:00:00Z")
        assertEquals(LocalDate.parse("2026-08-03"), pickerTodayAt(now, "America/Sao_Paulo"))
        assertEquals(LocalDate.parse("2026-08-04"), pickerTodayAt(now, "Asia/Tokyo"))
    }

    @Test
    fun `picker accepts local time with seconds`() {
        assertEquals(19 to 30, pickerTimeParts("19:30:45"))
    }

    @Test
    fun `picker preserves a minute outside the fifteen minute grid`() {
        val state = GameDateTimePicker.state(
            days = listOf(PickerDay("2026-08-04", "terça, 4 de agosto")),
            selectedDate = "2026-08-04",
            selectedHour = 19,
            selectedMinute = 37,
        )

        assertEquals(listOf(0, 15, 30, 37, 45), state.minutes)
        assertEquals(37, state.selectedMinute)
    }

    @Test
    fun `picker calendar aligns the weeks and names the month where the range turns`() {
        // 2026-10-28 é uma quarta: 5 dias de outubro (28 a 31) e 3 de novembro (1 a 3, de domingo a terça).
        val days = buildPickerDays(LocalDate.parse("2026-10-28"), 7, List(7) { "dia" }, List(12) { "m" })
        val months = GameDateTimePicker.months(days, List(12) { "mês${it + 1}" })

        assertEquals(listOf("mês10", "mês11"), months.map { it.title })
        // Outubro: quarta é a 3ª coluna, então duas células vazias antes do dia 28.
        assertEquals(listOf(null, null, 0, 1, 2, 3, null), months[0].weeks.single().map { it?.index })
        assertEquals(listOf(28, 29, 30, 31), months[0].weeks.single().filterNotNull().map { it.dayOfMonth })
        // Novembro: dia 1 cai num domingo (última coluna).
        assertEquals(listOf(null, null, null, null, null, null, 4), months[1].weeks.first().map { it?.index })
        assertEquals(listOf(5, 6), months[1].weeks.last().filterNotNull().map { it.index })
    }

    @Test
    fun `picker selects the day by date and falls back to the first day`() {
        val days = buildPickerDays(LocalDate.parse("2026-08-03"), 30, List(7) { "dia" }, List(12) { "m" })
        assertEquals(2, GameDateTimePicker.state(days, "2026-08-05", 19, 0).selectedDayIndex)
        assertEquals("2026-08-05", GameDateTimePicker.selectedDate(GameDateTimePicker.state(days, "2026-08-05", 19, 0)))
        assertEquals(0, GameDateTimePicker.state(days, "2030-01-01", 19, 0).selectedDayIndex)
    }

    @Test
    fun `picker calendar of a single month has no extra months`() {
        val days = buildPickerDays(LocalDate.parse("2026-08-01"), 31, List(7) { "dia" }, List(12) { "m" })
        val months = GameDateTimePicker.months(days, List(12) { "mês${it + 1}" })
        assertEquals(1, months.size)
        // 1º de agosto de 2026 é sábado: 5 células vazias; 31 dias fecham em 6 semanas.
        assertEquals(5, months.single().weeks.first().count { it == null })
        assertEquals(6, months.single().weeks.size)
    }

    // ---- DEB-050: quadra sem endereço é sempre aceita ----

    /** Grupo sem quadra padrão: o formulário pede nome e endereço, e a pessoa só digita o nome. */
    private fun groupWithoutVenue() = groupGateway { it.copy(profile = it.profile?.copy(defaultVenue = null)) }

    private fun refusedBy(field: String) = SaqzResult.Failure(
        GameError.Validation(DataError.Validation(ValidationDetails(emptyList(), mapOf(field to listOf("is required"))))),
    )

    @Test
    fun `a venue without address is accepted and goes to the server with an empty address`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Success(sampleVersionedGame()))
        val vm = viewModel(gameGateway = gateway, groupGateway = groupWithoutVenue())
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.UpdateVenueName("Quadra do Parque"))

        vm.onIntent(GameEditorIntent.Submit)

        val state = vm.state.value
        assertTrue(state.validationErrors.isEmpty(), "o endereço é sempre opcional (DEB-050)")
        assertEquals(1, gateway.createCalls)
        assertEquals("", gateway.lastCreateCommand?.venue?.address.orEmpty())
        assertTrue(vm.effects.first() is GameEditorEffect.Saved)
    }

    @Test
    fun `a venue without address is accepted when editing too`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        vm.onIntent(GameEditorIntent.UpdateVenueAddress(""))

        vm.onIntent(GameEditorIntent.Submit)

        assertTrue(vm.state.value.validationErrors.isEmpty())
        assertEquals(1, gateway.editCalls)
    }

    @Test
    fun `a conflict with another game shows the busy time and offers the existing game`() = runTest {
        val gateway = FakeGameGateway(createResult = SaqzResult.Failure(GameError.Conflict("existing-game")))
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.Submit)

        val state = vm.state.value
        assertTrue(state.hasConflict)
        assertEquals("existing-game", state.conflictGameId)
        assertFalse(state.saveFailed)
        assertFalse(state.isSaving)
    }

    @Test
    fun `a conflict when editing keeps the form and shows the busy time`() = runTest {
        val gateway = FakeGameGateway(editResult = SaqzResult.Failure(GameError.Conflict("other-game")))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        val formBefore = vm.state.value.form

        vm.onIntent(GameEditorIntent.Submit)

        assertTrue(vm.state.value.hasConflict)
        assertEquals("other-game", vm.state.value.conflictGameId)
        assertEquals(formBefore, vm.state.value.form)
    }

    // ---- Valor por jogo: vem do padrão do grupo e pode ser trocado só neste jogo ----

    private fun groupWithGameFee(cents: Long?, monthly: Long? = null) =
        groupGateway { it.copy(financeDefaults = GroupFinanceDefaults(cents, monthly, null)) }

    private val NoFeeDefaults = GroupFinanceDefaults(null, null, null, defaultNoGameFee = true)

    @Test
    fun `the fee field starts with the group default`() = runTest {
        val vm = viewModel(groupGateway = groupWithGameFee(2500))

        assertEquals(2500L, vm.state.value.form.gameFeeCents)
        assertEquals("25,00", vm.state.value.form.gameFeeText)
    }

    @Test
    fun `a group without default fee starts the field empty`() = runTest {
        val vm = viewModel(groupGateway = groupWithGameFee(null))

        assertEquals("", vm.state.value.form.gameFeeText)
        assertNull(vm.state.value.form.gameFeeCents)
        assertNull(vm.state.value.form.gameFeeChoice, "grupo sem valor abre sem nada marcado")
    }

    @Test
    fun `the inherited fee is sent as is`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway, groupGateway = groupWithGameFee(2500))
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(2500L, gateway.lastCreateCommand?.gameFeeCents)
        assertFalse(gateway.lastCreateCommand?.useDefaultGameFee == true)
    }

    @Test
    fun `changing the fee applies only to this game`() = runTest {
        val gateway = FakeGameGateway()
        val profileGateway = FakeGroupProfileGateway()
        val vm = viewModel(gameGateway = gateway, groupGateway = groupWithGameFee(2500), profileGateway = profileGateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.UpdateGameFee("30"))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(3000L, gateway.lastCreateCommand?.gameFeeCents)
        assertNull(profileGateway.lastUpdateCommand, "o padrão do grupo não muda")
    }

    @Test
    fun `typing a decimal fee keeps the cents`() = runTest {
        val vm = viewModel(groupGateway = groupWithGameFee(null))

        vm.onIntent(GameEditorIntent.UpdateGameFee("12,5"))

        assertEquals("12,5", vm.state.value.form.gameFeeText)
        assertEquals(1250L, vm.state.value.form.gameFeeCents)
    }

    @Test
    fun `erasing the fee with Cobrar diária is refused and nothing is sent - JOGO-008`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway, groupGateway = groupWithGameFee(2500))
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.UpdateGameFee(""))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(setOf(GameEditorFieldError.GameFeeInvalid), vm.state.value.validationErrors)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun `nothing chosen does not save and points at the fee block - JOGO-008`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway, groupGateway = groupWithGameFee(null))
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(setOf(GameEditorFieldError.GameFeeMissing), vm.state.value.validationErrors)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun `Sem diária sends the mark without a fee - JOGO-008`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway, groupGateway = groupWithGameFee(2500))
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.SelectGameFeeChoice(GameFeeChoice.NoFee))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1, gateway.createCalls)
        assertNull(gateway.lastCreateCommand?.gameFeeCents)
        assertEquals(true, gateway.lastCreateCommand?.noGameFee)
    }

    @Test
    fun `Cobrar diária sends the fee with the mark off`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway, groupGateway = groupWithGameFee(null))
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.SelectGameFeeChoice(GameFeeChoice.Charge))
        vm.onIntent(GameEditorIntent.UpdateGameFee("20"))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(2000L, gateway.lastCreateCommand?.gameFeeCents)
        assertEquals(false, gateway.lastCreateCommand?.noGameFee)
    }

    @Test
    fun `a group that chose Sem diária opens new games with Sem diária and the monthly fee only as a hint`() = runTest {
        val vm = viewModel(groupGateway = groupGateway { it.copy(financeDefaults = GroupFinanceDefaults(null, 8000, 10, defaultNoGameFee = true)) })

        assertEquals(GameFeeChoice.NoFee, vm.state.value.form.gameFeeChoice)
        assertNull(vm.state.value.form.gameFeeCents, "no editor a conta fica só no apoio")
        assertEquals(8000L, vm.state.value.monthlyFeeCents)
    }

    @Test
    fun `recurring create in a group without fee gives the group this game's choice - DEB-146`() = runTest {
        val profileGateway = FakeGroupProfileGateway()
        val groups = groupGateway { it.copy(financeDefaults = GroupFinanceDefaults(null, null, null, gameFeeUndefined = true)) }
        val vm = viewModel(groupGateway = groups, profileGateway = profileGateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.ToggleRecurring(true))
        vm.onIntent(GameEditorIntent.SelectGameFeeChoice(GameFeeChoice.Charge))
        vm.onIntent(GameEditorIntent.UpdateGameFee("15"))

        vm.onIntent(GameEditorIntent.Submit)

        val form = assertNotNull(profileGateway.lastUpdateCommand).form
        assertEquals(1500L, form.defaultGameFeeCents)
        assertEquals(false, form.defaultNoGameFee)
    }

    @Test
    fun `a single new game in a group without fee gives the group this game's choice - DEB-146`() = runTest {
        val profileGateway = FakeGroupProfileGateway()
        val groups = groupGateway { it.copy(financeDefaults = GroupFinanceDefaults(null, null, null, gameFeeUndefined = true)) }
        val vm = viewModel(groupGateway = groups, profileGateway = profileGateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.SelectGameFeeChoice(GameFeeChoice.NoFee))

        vm.onIntent(GameEditorIntent.Submit)

        val form = assertNotNull(profileGateway.lastUpdateCommand).form
        assertNull(form.defaultGameFeeCents)
        assertEquals(true, form.defaultNoGameFee)
        assertTrue(vm.effects.first() is GameEditorEffect.Saved)
    }

    @Test
    fun `a single new game in a group with a fee leaves the group alone`() = runTest {
        val profileGateway = FakeGroupProfileGateway()
        val vm = viewModel(groupGateway = groupWithGameFee(2500), profileGateway = profileGateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.Submit)

        assertNull(profileGateway.lastUpdateCommand)
    }

    @Test
    fun `a zero fee is refused and nothing is sent`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway, groupGateway = groupWithGameFee(2500))
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.UpdateGameFee("0,00"))
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(setOf(GameEditorFieldError.GameFeeInvalid), vm.state.value.validationErrors)
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun `editing a game shows its own fee and keeps it when untouched`() = runTest {
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(sampleVersionedGame(sampleGame().copy(gameFeeCents = 1800))),
        )
        val vm = viewModel(gameId = "game-1", gameGateway = gateway, groupGateway = groupWithGameFee(2500))

        assertEquals("18,00", vm.state.value.form.gameFeeText)
        vm.onIntent(GameEditorIntent.Submit)

        assertEquals(1800L, gateway.lastEditCommand?.gameFeeCents)
    }

    @Test
    fun `editing a game without charge keeps it without charge even if the group has a default`() = runTest {
        val gateway = FakeGameGateway(
            readResult = SaqzResult.Success(sampleVersionedGame(sampleGame().copy(gameFeeCents = null))),
        )
        val vm = viewModel(gameId = "game-1", gameGateway = gateway, groupGateway = groupWithGameFee(2500))

        vm.onIntent(GameEditorIntent.Submit)

        assertNull(gateway.lastEditCommand?.gameFeeCents)
        assertFalse(gateway.lastEditCommand?.useDefaultGameFee == true)
    }

    @Test
    fun `the typed fee survives recreation`() = runTest {
        val savedState = SavedStateHandle()
        val first = viewModel(savedState = savedState, groupGateway = groupWithGameFee(2500))
        first.onIntent(GameEditorIntent.UpdateGameFee("40"))

        val recreated = viewModel(savedState = savedState, groupGateway = groupWithGameFee(2500))

        assertEquals("40", recreated.state.value.form.gameFeeText)
        assertEquals(4000L, recreated.state.value.form.gameFeeCents)
    }

    @Test
    fun `a refused field the form cannot point at keeps the generic failure`() = runTest {
        val gateway = FakeGameGateway(createResult = refusedBy("title"))
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.Submit)

        val state = vm.state.value
        assertTrue(state.saveFailed)
        assertEquals(GroupUiError.Validation, state.error)
        assertTrue(state.validationErrors.isEmpty())
        assertEquals(0, state.submitAttempts)
    }

    @Test
    fun `the current backend accepts a venue without address and nothing changes`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))

        vm.onIntent(GameEditorIntent.Submit)

        assertTrue(vm.state.value.validationErrors.isEmpty())
        assertFalse(vm.state.value.saveFailed)
        assertTrue(vm.effects.first() is GameEditorEffect.Saved)
    }
}
