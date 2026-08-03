package br.com.saqz.groups.presentation.gameeditor

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleVersionedGame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class GameEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()
    @Test
    fun `create mode does not load`() = runTest {
        val gateway = FakeGameGateway()
        val viewModel = GameEditorViewModel("group-1", gameId = null, gateway)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(0, gateway.readCalls)
    }
    @Test
    fun `edit mode loads the game`() = runTest {
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame()))
        val viewModel = GameEditorViewModel("group-1", "game-1", gateway)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, gateway.readCalls)
    }
    @Test
    fun `gateway failure is visible and typed`() = runTest {
        val viewModel = GameEditorViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Failure(GameError.Data(DataError.Forbidden))),
        )
        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }
    @Test
    fun `retry after failure reloads and succeeds`() = runTest {
        val first = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val second = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(first, second)))
        val viewModel = GameEditorViewModel("group-1", "game-1", gateway)
        first.complete(SaqzResult.Failure(GameError.Data(DataError.Forbidden)))
        assertTrue(viewModel.state.value.loadFailed)
        viewModel.onIntent(GameEditorIntent.Retry)
        second.complete(SaqzResult.Success(sampleVersionedGame()))
        assertFalse(viewModel.state.value.run { loadFailed || isLoading })
    }
    @Test
    fun `stale read response cannot replace a newer generation`() = runTest {
        val old = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val fresh = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(old, fresh)))
        val viewModel = GameEditorViewModel("group-1", "game-1", gateway)
        viewModel.onIntent(GameEditorIntent.Retry)
        gateway.completeRead(1, SaqzResult.Success(sampleVersionedGame()))
        old.complete(SaqzResult.Failure(GameError.Data(DataError.Forbidden)))
        assertFalse(viewModel.state.value.run { loadFailed || isLoading })
    }
    @Test
    fun `retry in create mode does nothing`() = runTest {
        val gateway = FakeGameGateway()
        val viewModel = GameEditorViewModel("group-1", gameId = null, gateway)
        viewModel.onIntent(GameEditorIntent.Retry)
        assertEquals(0, gateway.readCalls)
        assertFalse(viewModel.state.value.isLoading)
    }
}
