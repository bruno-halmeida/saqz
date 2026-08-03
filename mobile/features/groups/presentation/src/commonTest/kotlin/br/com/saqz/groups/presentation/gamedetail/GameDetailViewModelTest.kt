package br.com.saqz.groups.presentation.gamedetail

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
class GameDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()
    @Test
    fun `loads the game on init`() = runTest {
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame()))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, gateway.readCalls)
    }
    @Test
    fun `gateway failure is visible and typed`() = runTest {
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Failure(GameError.Data(DataError.Forbidden))),
        )
        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }
    @Test
    fun `hidden resource surfaces as not found`() = runTest {
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Failure(GameError.HiddenResource)),
        )
        assertEquals(GroupUiError.NotFound, viewModel.state.value.error)
    }
    @Test
    fun `retry after failure reloads and succeeds`() = runTest {
        val first = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val second = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(first, second)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway)
        first.complete(SaqzResult.Failure(GameError.Data(DataError.Forbidden)))
        assertTrue(viewModel.state.value.loadFailed)
        viewModel.onIntent(GameDetailIntent.Retry)
        second.complete(SaqzResult.Success(sampleVersionedGame()))
        assertFalse(viewModel.state.value.run { loadFailed || isLoading })
    }
    @Test
    fun `stale read response cannot replace a newer generation`() = runTest {
        val old = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val fresh = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(old, fresh)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway)
        viewModel.onIntent(GameDetailIntent.Retry)
        gateway.completeRead(1, SaqzResult.Success(sampleVersionedGame()))
        old.complete(SaqzResult.Failure(GameError.Data(DataError.Forbidden)))
        assertFalse(viewModel.state.value.run { loadFailed || isLoading })
    }
}
