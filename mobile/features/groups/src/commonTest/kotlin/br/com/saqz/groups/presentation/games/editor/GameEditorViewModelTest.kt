package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.game.VersionedSeries
import br.com.saqz.groups.domain.game.Weekday
import br.com.saqz.groups.domain.game.WeeklySeries
import br.com.saqz.groups.domain.game.WeeklySeriesWriteCommand
import br.com.saqz.groups.domain.game.WeeklySlot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameEditorViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new draft copies group defaults exactly once`() = runTest(mainDispatcher) {
        val fixture = fixture()

        val form = fixture.viewModel.state.value.draft.form

        assertEquals("Grupo", form.title)
        assertEquals("Arena", form.venue?.name)
        assertEquals("24", form.capacity)
        assertEquals("25,00", form.gameFeeBrl)
    }

    @Test
    fun `restored draft wins over changed current defaults`() = runTest(mainDispatcher) {
        val restored = draft().copy(form = validForm().copy(title = "Restored"), commandKey = "restored")

        val fixture = fixture(stored = restored, defaults = defaults().copy(title = "Changed"))

        assertEquals("Restored", fixture.viewModel.state.value.draft.form.title)
        assertEquals("restored", fixture.viewModel.state.value.draft.commandKey)
    }

    @Test
    fun `unsupported draft schema is ignored`() = runTest(mainDispatcher) {
        val old = draft().copy(schemaVersion = 99, form = validForm().copy(title = "Old"))

        val fixture = fixture(stored = old)

        assertEquals("Grupo", fixture.viewModel.state.value.draft.form.title)
    }

    @Test
    fun `draft read failure exposes unavailable state`() = runTest(mainDispatcher) {
        val store = FakeStore(null, readFails = true)

        val fixture = fixture(store = store)

        assertEquals(GameEditorError.DRAFT_UNAVAILABLE, fixture.viewModel.state.value.error)
    }

    @Test
    fun `restored invalid draft never auto-submits and awaits normal correction`() = runTest(mainDispatcher) {
        val invalidForm = GameEditorForm(durationMinutes = "2", capacity = "1", gameFeeBrl = "x", notes = "x")
        val restored = draft().copy(form = invalidForm, commandKey = "restored-invalid")

        val fixture = fixture(stored = restored)

        // Restored (PMVI-018) but never silently submitted (PMVI-019).
        assertEquals("restored-invalid", fixture.viewModel.state.value.draft.commandKey)
        assertEquals("2", fixture.viewModel.state.value.draft.form.durationMinutes)
        assertEquals(0, fixture.gateway.calls.size)
        assertTrue(fixture.viewModel.state.value.fieldErrors.isEmpty())

        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        // Normal corrective validation state, still no request.
        assertTrue(
            fixture.viewModel.state.value.fieldErrors.keys.containsAll(
                listOf("title", "durationMinutes", "capacity", "gameFeeBrl", "notes"),
            ),
        )
        assertEquals(0, fixture.gateway.calls.size)
    }

    @Test
    fun `mode and form changes persist together`() = runTest(mainDispatcher) {
        val fixture = fixture()

        fixture.viewModel.onIntent(GameEditorIntent.SetMode(GameEditorMode.WEEKLY))
        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm().copy(slots = listOf(slot()))))

        assertEquals(GameEditorMode.WEEKLY, fixture.store.writes.last().mode)
        assertEquals(1, fixture.store.writes.last().form.slots.size)
    }

    @Test
    fun `add slot uses injected stable identity and copied form defaults`() = runTest(mainDispatcher) {
        val fixture = fixture()

        fixture.viewModel.onIntent(GameEditorIntent.AddSlot)

        val added = fixture.viewModel.state.value.draft.form.slots.single()
        assertEquals("stable-key", added.slotKey)
        assertEquals("Grupo", added.title)
        assertEquals("Arena", added.venue.name)
        assertEquals(24, added.capacity)
        assertEquals(2500, added.gameFeeCents)
    }

    @Test
    fun `one time validation reports every required and bounded field`() = runTest(mainDispatcher) {
        val fixture = fixture()
        val invalid = GameEditorForm(durationMinutes = "2", capacity = "1", gameFeeBrl = "x", notes = "x")

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(invalid))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)

        assertTrue(
            fixture.viewModel.state.value.fieldErrors.keys.containsAll(
                listOf(
                    "title",
                    "venue",
                    "localDate",
                    "localTime",
                    "zoneId",
                    "startsAt",
                    "confirmationDeadline",
                    "durationMinutes",
                    "capacity",
                    "gameFeeBrl",
                    "notes",
                ),
            ),
        )
        assertEquals(0, fixture.gateway.calls.size)
    }

    @Test
    fun `weekly validation requires one slot`() = runTest(mainDispatcher) {
        val fixture = fixture()

        fixture.viewModel.onIntent(GameEditorIntent.SetMode(GameEditorMode.WEEKLY))
        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm().copy(slots = emptyList())))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)

        assertEquals(listOf("must not be empty"), fixture.viewModel.state.value.fieldErrors["slots"])
    }

    @Test
    fun `BRL fee converts comma amount to integer cents`() = runTest(mainDispatcher) {
        val fixture = fixture()

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm().copy(gameFeeBrl = "12,34")))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(1234, fixture.gateway.gameCommands.single().gameFeeCents)
    }

    @Test
    fun `blank notes normalize to null`() = runTest(mainDispatcher) {
        val fixture = fixture()

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm().copy(notes = "  ")))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertNull(fixture.gateway.gameCommands.single().notes)
    }

    @Test
    fun `one time create retains stable command key`() = runTest(mainDispatcher) {
        val fixture = fixture()

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals("stable-key", fixture.gateway.gameCommands.single().requestId)
    }

    @Test
    fun `weekly create sends every local slot and optional end`() = runTest(mainDispatcher) {
        val fixture = fixture()
        val form = validForm().copy(
            localEndDate = "2026-12-31",
            slots = listOf(slot(), slot().copy(slotKey = "slot-2")),
        )

        fixture.viewModel.onIntent(GameEditorIntent.SetMode(GameEditorMode.WEEKLY))
        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(form))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(2, fixture.gateway.seriesCommands.single().slots?.size)
        assertEquals("2026-12-31", fixture.gateway.seriesCommands.single().localEndDate)
    }

    @Test
    fun `an incompatible weekly draft is discarded when editing a standalone game`() = runTest(mainDispatcher) {
        val weeklyDraftForStandalone = draft().copy(
            gameId = GAME,
            mode = GameEditorMode.WEEKLY,
            form = validForm().copy(slots = listOf(slot())),
        )
        val fixture = fixture(stored = weeklyDraftForStandalone, existing = versionedGame())

        // A standalone game is one-time; a persisted WEEKLY draft is incompatible and is
        // discarded, so restore never lands the editor in weekly mode where submit would
        // crash on the missing series context.
        assertEquals(GameEditorMode.ONE_TIME, fixture.viewModel.state.value.draft.mode)

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(listOf("edit"), fixture.gateway.calls)
        assertTrue(fixture.gateway.boundaries.isEmpty())
    }

    @Test
    fun `an existing standalone game cannot switch to weekly mode`() = runTest(mainDispatcher) {
        val fixture = fixture(existing = versionedGame())

        fixture.viewModel.onIntent(GameEditorIntent.SetMode(GameEditorMode.WEEKLY))

        // Recurrence type is fixed on an edit: the mode stays one-time, so weekly slot
        // edits can never be entered and silently dropped by the single-game save path.
        assertEquals(GameEditorMode.ONE_TIME, fixture.viewModel.state.value.draft.mode)
    }

    @Test
    fun `editing a standalone game submits a single game edit even after a weekly toggle`() = runTest(mainDispatcher) {
        val fixture = fixture(existing = versionedGame())

        fixture.viewModel.onIntent(GameEditorIntent.SetMode(GameEditorMode.WEEKLY))
        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(listOf("edit"), fixture.gateway.calls)
        assertTrue(fixture.gateway.boundaries.isEmpty())
    }

    @Test
    fun `weekly edit refuses silent scope default`() = runTest(mainDispatcher) {
        val fixture = fixture(existing = versionedGame(), series = versionedSeries())

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm().copy(slots = listOf(slot()))))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)

        assertEquals(listOf("is required"), fixture.viewModel.state.value.fieldErrors["scope"])
        assertTrue(fixture.gateway.boundaries.isEmpty())
    }

    @Test
    fun `only this edit carries replacement and exact domain version`() = runTest(mainDispatcher) {
        val fixture = fixture(existing = versionedGame(), series = versionedSeries())

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm().copy(slots = listOf(slot()))))
        fixture.viewModel.onIntent(GameEditorIntent.SetScope(SeriesBoundaryScope.OnlyThis))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        val call = fixture.gateway.boundaries.single()
        assertEquals(GameVersionToken("\"7\""), call.version)
        assertNotNull(call.command.replacement)
        assertNull(call.command.successor)
    }

    @Test
    fun `this and future edit carries successor and explicit boundary`() = runTest(mainDispatcher) {
        val fixture = fixture(existing = versionedGame(), series = versionedSeries())

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm().copy(slots = listOf(slot()))))
        fixture.viewModel.onIntent(GameEditorIntent.SetScope(SeriesBoundaryScope.ThisAndFuture))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        val command = fixture.gateway.boundaries.single().command
        assertEquals("2026-08-12", command.boundary)
        assertNotNull(command.successor)
    }

    @Test
    fun `conflict preserves draft command key and offers reload`() = runTest(mainDispatcher) {
        val fixture = fixture(existing = versionedGame(), gameResult = SaqzResult.Failure(GameError.Conflict))

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm().copy(title = "Unsaved")))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals("Unsaved", fixture.viewModel.state.value.draft.form.title)
        assertEquals("stable-key", fixture.viewModel.state.value.draft.commandKey)
        assertTrue(fixture.viewModel.state.value.reloadAvailable)
    }

    @Test
    fun `typed validation preserves safe global and indexed field messages`() = runTest(mainDispatcher) {
        val validation = DataError.Validation(
            ValidationDetails(
                globalMessages = listOf("Revise os horários"),
                fieldMessages = mapOf("slots[0].weekday" to listOf("bad")),
            ),
        )
        val fixture = fixture(gameResult = SaqzResult.Failure(GameError.Validation(validation)))

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(listOf("bad"), fixture.viewModel.state.value.fieldErrors["slots[0].weekday"])
        assertEquals(listOf("Revise os horários"), fixture.viewModel.state.value.globalValidationMessages)
        assertNull(fixture.viewModel.state.value.error)
    }

    @Test
    fun `validation without safe global message requests localized generic fallback`() = runTest(mainDispatcher) {
        val validation = DataError.Validation(ValidationDetails(emptyList(), mapOf("title" to listOf("bad"))))
        val fixture = fixture(gameResult = SaqzResult.Failure(GameError.Validation(validation)))

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(GameEditorError.VALIDATION, fixture.viewModel.state.value.error)
        assertTrue(fixture.viewModel.state.value.globalValidationMessages.isEmpty())
    }

    @Test
    fun `connectivity failure exposes unavailable state and stops loading`() = runTest(mainDispatcher) {
        val failure = SaqzResult.Failure(GameError.Data(DataError.Connectivity))
        val fixture = fixture(gameResult = failure)

        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(GameEditorError.UNAVAILABLE, fixture.viewModel.state.value.error)
        assertFalse(fixture.viewModel.state.value.isLoading)
    }

    @Test
    fun `reload effect targets authoritative existing game`() = runTest(mainDispatcher) {
        val fixture = fixture(existing = versionedGame(), gameResult = SaqzResult.Failure(GameError.Conflict))
        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()
        val effect = async { fixture.viewModel.effects.first() }

        fixture.viewModel.onIntent(GameEditorIntent.Reload)

        assertEquals(GameEditorEffect.Reload(GROUP, GAME), effect.await())
    }

    @Test
    fun `duplicate submit is single flight`() = runTest(mainDispatcher) {
        val fixture = fixture()
        fixture.gateway.gate = CompletableDeferred()
        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))

        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(1, fixture.gateway.calls.size)
        fixture.gateway.gate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun `success clears matching draft and emits saved once`() = runTest(mainDispatcher) {
        val fixture = fixture()
        fixture.viewModel.onIntent(GameEditorIntent.UpdateForm(validForm()))
        val effect = async { fixture.viewModel.effects.first() }

        fixture.viewModel.onIntent(GameEditorIntent.Submit)
        runCurrent()

        assertEquals(GameEditorEffect.Saved(GAME), effect.await())
        assertEquals("stable-key", fixture.store.clears.single().third)
        assertEquals(GAME, fixture.viewModel.state.value.successId)
    }

    private fun fixture(
        stored: GameEditorDraft? = null,
        defaults: GameEditorDefaults = defaults(),
        existing: VersionedGame? = null,
        series: VersionedSeries? = null,
        gameResult: SaqzResult<VersionedGame, GameError> = SaqzResult.Success(versionedGame()),
        store: FakeStore = FakeStore(stored),
    ): Fixture {
        val gateway = FakeGateway(gameResult)
        val viewModel = GameEditorViewModel(
            GameEditorInput(GROUP, defaults, existing, series),
            gateway,
            store,
            GameCommandKeyFactory { "stable-key" },
        )
        return Fixture(viewModel, gateway, store)
    }

    private fun defaults() = GameEditorDefaults(
        "Grupo",
        GameVenue(null, "Arena", "Rua Central 100"),
        "America/Sao_Paulo",
        90,
        24,
        180,
        2500,
    )

    private fun validForm() = GameEditorForm(
        "Treino",
        GameVenue(null, "Arena", "Rua Central 100"),
        "2026-08-12",
        "19:30:00",
        "America/Sao_Paulo",
        "2026-08-12T22:30:00Z",
        "90",
        "24",
        "2026-08-12T19:30:00Z",
        "25,00",
        "Notas",
    )

    private fun slot() = WeeklySlot(
        "slot-1",
        Weekday.Wednesday,
        "19:30:00",
        90,
        GameVenue(null, "Arena", "Rua Central 100"),
        24,
        180,
        2500,
        "Treino",
    )

    private fun draft() = GameEditorDraft(
        groupId = GROUP,
        gameId = null,
        seriesId = null,
        commandKey = "old",
        version = null,
        mode = GameEditorMode.ONE_TIME,
        form = validForm(),
    )

    private fun versionedGame() = VersionedGame(
        Game(
            GAME,
            GroupId(GROUP),
            "Treino",
            GameVenue(null, "Arena", "Rua Central 100"),
            "2026-08-12",
            "19:30:00",
            "America/Sao_Paulo",
            "2026-08-12T22:30:00Z",
            90,
            24,
            "2026-08-12T19:30:00Z",
            2500,
            "Notas",
            GameStatus.Draft,
            7,
            0,
            24,
            0,
        ),
        GameVersionToken("\"7\""),
    )

    private fun versionedSeries() = VersionedSeries(
        WeeklySeries(
            SERIES,
            "revision",
            1,
            "America/Sao_Paulo",
            "2026-08-12",
            slots = listOf(slot()),
            occurrences = emptyList(),
            version = 7,
        ),
        GameVersionToken("\"7\""),
    )

    private class FakeStore(
        private val stored: GameEditorDraft?,
        private val readFails: Boolean = false,
    ) : GameDraftStorePort {
        val writes = mutableListOf<GameEditorDraft>()
        val clears = mutableListOf<Triple<String, String?, String>>()

        override fun read(groupId: String, resourceId: String?, done: (GameDraftReadResult) -> Unit) {
            done(if (readFails) GameDraftReadResult.Failure else GameDraftReadResult.Success(stored))
        }

        override fun write(draft: GameEditorDraft, done: (GameDraftWriteResult) -> Unit) {
            writes += draft
            done(GameDraftWriteResult.Success)
        }

        override fun clear(
            groupId: String,
            resourceId: String?,
            commandKey: String,
            done: (GameDraftWriteResult) -> Unit,
        ) {
            clears += Triple(groupId, resourceId, commandKey)
            done(GameDraftWriteResult.Success)
        }
    }

    private data class Boundary(val version: GameVersionToken, val command: SeriesBoundaryCommand)

    private inner class FakeGateway(
        var gameResult: SaqzResult<VersionedGame, GameError>,
    ) : GameGateway {
        val calls = mutableListOf<String>()
        val gameCommands = mutableListOf<GameWriteCommand>()
        val seriesCommands = mutableListOf<WeeklySeriesWriteCommand>()
        val boundaries = mutableListOf<Boundary>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun list(groupId: GroupId) = error("unused")

        override suspend fun read(groupId: GroupId, gameId: String) = gameResult

        override suspend fun create(
            groupId: GroupId,
            command: GameWriteCommand,
        ): SaqzResult<VersionedGame, GameError> {
            calls += "create"
            gameCommands += command
            gate?.await()
            return gameResult
        }

        override suspend fun edit(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            command: GameWriteCommand,
        ): SaqzResult<VersionedGame, GameError> {
            calls += "edit"
            gameCommands += command
            gate?.await()
            return gameResult
        }

        override suspend fun lifecycle(
            groupId: GroupId,
            gameId: String,
            version: GameVersionToken,
            action: GameLifecycleAction,
        ) = gameResult

        override suspend fun createSeries(
            groupId: GroupId,
            command: WeeklySeriesWriteCommand,
        ): SaqzResult<VersionedSeries, GameError> {
            calls += "series"
            seriesCommands += command
            return SaqzResult.Success(versionedSeries())
        }

        override suspend fun readSeries(groupId: GroupId, seriesId: String) =
            SaqzResult.Success(versionedSeries())

        override suspend fun boundary(
            groupId: GroupId,
            seriesId: String,
            version: GameVersionToken,
            command: SeriesBoundaryCommand,
        ): SaqzResult<VersionedSeries, GameError> {
            calls += "boundary"
            boundaries += Boundary(version, command)
            return SaqzResult.Success(versionedSeries())
        }
    }

    private data class Fixture(
        val viewModel: GameEditorViewModel,
        val gateway: FakeGateway,
        val store: FakeStore,
    )

    private companion object {
        const val GROUP = "group"
        const val GAME = "game"
        const val SERIES = "series"
    }
}
