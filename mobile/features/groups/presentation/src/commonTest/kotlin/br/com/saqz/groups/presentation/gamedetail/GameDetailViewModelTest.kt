package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleCancelledGame
import br.com.saqz.groups.presentation.sampleVersionedGame
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads game details`() = runTest {
        val gateway = FakeGameGateway(readResult = SaqzResult.Success(sampleVersionedGame()))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway())
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, gateway.readCalls)
        assertNotNull(viewModel.state.value.header)
        assertNotNull(viewModel.state.value.attendance)
    }

    @Test
    fun `maps gateway failures`() = runTest {
        val viewModel = GameDetailViewModel(
            "group-1", "game-1",
            FakeGameGateway(readResult = SaqzResult.Failure(GameError.Data(DataError.Forbidden))),
            FakeGroupGateway(),
        )
        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `retry and generation guard work`() = runTest {
        val old = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val fresh = CompletableDeferred<SaqzResult<VersionedGame, GameError>>()
        val gateway = FakeGameGateway(reads = ArrayDeque(listOf(old, fresh)))
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway())
        viewModel.onIntent(GameDetailIntent.Retry)
        gateway.completeRead(1, SaqzResult.Success(sampleVersionedGame()))
        old.complete(SaqzResult.Failure(GameError.Data(DataError.Forbidden)))
        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `cancel uses optimistic version and emits effect`() = runTest {
        val gateway = FakeGameGateway(
            lifecycleResult = SaqzResult.Success(VersionedGame(sampleCancelledGame(), GameVersionToken("etag-2"))),
        )
        val viewModel = GameDetailViewModel("group-1", "game-1", gateway, FakeGroupGateway())
        viewModel.onIntent(GameDetailIntent.RequestCancel)
        viewModel.onIntent(GameDetailIntent.ConfirmCancel)
        assertEquals(GameLifecycleAction.Cancel, gateway.lastLifecycleAction)
        assertEquals(GameDetailStatusTone.Cancelled, viewModel.state.value.header?.statusTone)
        assertFalse(viewModel.state.value.cancelDialogOpen)
        assertEquals(GameDetailEffect.Cancelled, viewModel.effects.first())
    }

}
