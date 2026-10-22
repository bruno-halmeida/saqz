package br.com.saqz.groups.presentation.pastgames

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.PastGame
import br.com.saqz.groups.domain.game.PastGamesGateway
import br.com.saqz.groups.domain.game.PastGamesPage
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-128 · "Jogos anteriores": meses, paginação de 20, cancelado, acerto pendente e erro. */
@OptIn(ExperimentalCoroutinesApi::class)
class PastGamesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `primeira pagina monta as linhas com mes meta selo e acerto pendente`() = runTest {
        val gateway = FakePastGames(
            pages = mapOf(
                null to PastGamesPage(
                    listOf(
                        game("g1", "2026-10-20T23:00:00Z", confirmed = 11, pendingCount = 3, pendingCents = 7_500),
                        game("g2", "2026-10-13T23:00:00Z", status = GameStatus.Cancelled, confirmed = 8),
                        game("g3", "2026-10-06T23:00:00Z", confirmed = 12, venue = "Arena Leblon"),
                        game("g4", "2026-09-29T23:00:00Z", confirmed = 1, status = GameStatus.Cancelled),
                    ),
                    nextCursor = null,
                ),
            ),
        )
        val vm = PastGamesViewModel("group-1", gateway, FakeGroupGateway())
        val rows = vm.state.value.rows.associateBy { it.gameId }

        assertFalse(vm.state.value.isLoading)
        assertFalse(vm.state.value.canLoadMore)
        assertEquals("OUTUBRO DE 2026", rows.getValue("g1").monthHeader)
        assertEquals("SETEMBRO DE 2026", rows.getValue("g4").monthHeader)
        assertEquals("20", rows.getValue("g1").day)
        assertEquals("OUT", rows.getValue("g1").month)
        assertEquals("Terça · 20h00", rows.getValue("g1").title)
        assertEquals("11 de 12 foram", rows.getValue("g1").meta)
        assertEquals("Encerrado", rows.getValue("g1").statusLabel)
        assertEquals("Acerto pendente · 3 pessoas, R$ 75,00", rows.getValue("g1").pending)
        assertTrue(rows.getValue("g2").cancelled)
        assertEquals("Cancelado", rows.getValue("g2").statusLabel)
        assertEquals("Cancelado com 8 confirmados", rows.getValue("g2").meta)
        assertNull(rows.getValue("g2").pending)
        assertEquals("Cancelado com 1 confirmado", rows.getValue("g4").meta)
        assertEquals("12 de 12 foram · Arena Leblon", rows.getValue("g3").meta)
    }

    @Test
    fun `carrega 20 mais 20 e 5 pelo cursor sem repetir e uma pagina por vez`() = runTest {
        val all = (0 until 45).map { game("g$it", "2026-10-${(20 - it / 3).coerceAtLeast(1).toString().padStart(2, '0')}T23:00:00Z") }
        val gate = CompletableDeferred<Unit>()
        val gateway = FakePastGames(
            pages = mapOf(
                null to PastGamesPage(all.take(20), "c1"),
                "c1" to PastGamesPage(all.subList(20, 40), "c2"),
                "c2" to PastGamesPage(all.drop(40), null),
            ),
            gateOn = "c1",
            gate = gate,
        )
        val vm = PastGamesViewModel("group-1", gateway, FakeGroupGateway())
        assertEquals(20, vm.state.value.rows.size)
        assertTrue(vm.state.value.canLoadMore)

        vm.onIntent(PastGamesIntent.LoadMore)
        vm.onIntent(PastGamesIntent.LoadMore)
        assertTrue(vm.state.value.isLoadingMore)
        gate.complete(Unit)
        assertEquals(listOf(null, "c1"), gateway.cursors)
        assertEquals(40, vm.state.value.rows.size)

        vm.onIntent(PastGamesIntent.LoadMore)
        assertEquals(45, vm.state.value.rows.size)
        assertEquals(45, vm.state.value.rows.map { it.gameId }.toSet().size)
        assertFalse(vm.state.value.canLoadMore)

        vm.onIntent(PastGamesIntent.LoadMore)
        assertEquals(listOf(null, "c1", "c2"), gateway.cursors)
    }

    @Test
    fun `falha na primeira carga mostra erro e tentar de novo recarrega`() = runTest {
        val gateway = FakePastGames(pages = emptyMap(), failure = GameError.Data(DataError.Connectivity))
        val vm = PastGamesViewModel("group-1", gateway, FakeGroupGateway())

        assertTrue(vm.state.value.loadFailed)
        assertEquals(GroupUiError.Network, vm.state.value.error)

        gateway.failure = null
        gateway.pages = mapOf(null to PastGamesPage(listOf(game("g1", "2026-10-20T23:00:00Z")), null))
        vm.onIntent(PastGamesIntent.Retry)
        assertFalse(vm.state.value.loadFailed)
        assertEquals(1, vm.state.value.rows.size)
    }

    @Test
    fun `falha ao carregar mais mantem a lista e deixa tentar de novo`() = runTest {
        val gateway = FakePastGames(pages = mapOf(null to PastGamesPage(listOf(game("g1", "2026-10-20T23:00:00Z")), "c1")))
        val vm = PastGamesViewModel("group-1", gateway, FakeGroupGateway())
        gateway.failure = GameError.Data(DataError.Connectivity)

        vm.onIntent(PastGamesIntent.LoadMore)

        assertTrue(vm.state.value.loadMoreFailed)
        assertEquals(1, vm.state.value.rows.size)
        gateway.failure = null
        gateway.pages = gateway.pages + ("c1" to PastGamesPage(listOf(game("g2", "2026-10-13T23:00:00Z")), null))
        vm.onIntent(PastGamesIntent.LoadMore)
        assertEquals(2, vm.state.value.rows.size)
        assertFalse(vm.state.value.loadMoreFailed)
    }

    @Test
    fun `atualizar por cima nao tira a lista quando falha`() = runTest {
        val gateway = FakePastGames(pages = mapOf(null to PastGamesPage(listOf(game("g1", "2026-10-20T23:00:00Z")), null)))
        val vm = PastGamesViewModel("group-1", gateway, FakeGroupGateway())
        gateway.failure = GameError.Data(DataError.Connectivity)

        vm.onIntent(PastGamesIntent.Refresh)

        assertFalse(vm.state.value.loadFailed)
        assertFalse(vm.state.value.isRefreshing)
        assertEquals(1, vm.state.value.rows.size)
    }

    @Test
    fun `toque abre o jogo`() = runTest {
        val gateway = FakePastGames(pages = mapOf(null to PastGamesPage(emptyList(), null)))
        val vm = PastGamesViewModel("group-1", gateway, FakeGroupGateway())

        vm.onIntent(PastGamesIntent.OpenGame("g1"))

        assertEquals(PastGamesEffect.OpenGame("g1"), vm.effects.first())
    }

    private fun game(
        id: String,
        startsAt: String,
        status: GameStatus = GameStatus.Completed,
        confirmed: Int = 10,
        venue: String = "CERET",
        pendingCount: Int = 0,
        pendingCents: Long = 0,
    ) = PastGame(id, startsAt.take(10), "20:00", "America/Sao_Paulo", startsAt, status, confirmed, 12, venue, pendingCount, pendingCents)

    private class FakePastGames(
        var pages: Map<String?, PastGamesPage>,
        var failure: GameError? = null,
        val gateOn: String? = null,
        val gate: CompletableDeferred<Unit>? = null,
    ) : PastGamesGateway {
        val cursors = mutableListOf<String?>()

        override suspend fun page(groupId: GroupId, cursor: String?): SaqzResult<PastGamesPage, GameError> {
            cursors += cursor
            if (cursor == gateOn) gate?.await()
            failure?.let { return SaqzResult.Failure(it) }
            return SaqzResult.Success(pages.getValue(cursor))
        }

        override suspend fun setNoShow(groupId: GroupId, gameId: String, memberId: String, guestSeq: Int, marked: Boolean) =
            SaqzResult.Success(Unit)
    }
}
