package br.com.saqz.groups.presentation.gameeditor

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleVersionedGame
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.CompletableDeferred
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
        gameGateway: FakeGameGateway = FakeGameGateway(),
        groupGateway: FakeGroupGateway = FakeGroupGateway(),
    ) = GameEditorViewModel("group-1", gameId, SavedStateHandle(), gameGateway, groupGateway)

    @Test
    fun `create mode loads group defaults and does not read a game`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        assertFalse(vm.state.value.isLoading)
        assertEquals(0, gateway.readCalls)
        assertEquals("Vôlei do CERET", vm.state.value.groupName)
        assertEquals(12, vm.state.value.form.capacity)
    }

    @Test
    fun `edit mode loads the game on top of group defaults`() = runTest {
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame()))
        val vm = viewModel(gameId = "game-1", gameGateway = gateway)
        assertFalse(vm.state.value.isLoading)
        assertEquals(1, gateway.readCalls)
        assertEquals("2026-08-04", vm.state.value.form.localDate)
        assertEquals("19:30", vm.state.value.form.localTime)
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
    }

    @Test
    fun `notes are trimmed to null when blank in the command`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.SaveDateTime("2026-08-04", "19:30"))
        vm.onIntent(GameEditorIntent.UpdateNotes("   "))
        vm.onIntent(GameEditorIntent.Submit)
        assertNull(gateway.lastCreateCommand?.notes)
    }

    @Test
    fun `capacity cannot drop below minimum`() = runTest {
        val vm = viewModel()
        vm.onIntent(GameEditorIntent.UpdateCapacity(0))
        assertEquals(2, vm.state.value.form.capacity)
    }

    @Test
    fun `retry in create mode reloads group defaults`() = runTest {
        val gateway = FakeGameGateway()
        val vm = viewModel(gameGateway = gateway)
        vm.onIntent(GameEditorIntent.Retry)
        assertFalse(vm.state.value.isLoading)
    }
}
