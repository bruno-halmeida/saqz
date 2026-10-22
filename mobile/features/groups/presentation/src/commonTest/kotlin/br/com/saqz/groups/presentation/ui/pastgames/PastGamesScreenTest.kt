package br.com.saqz.groups.presentation.ui.pastgames

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.pastgames.PastGameRowUi
import br.com.saqz.groups.presentation.pastgames.PastGamesIntent
import br.com.saqz.groups.presentation.pastgames.PastGamesState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DEB-128 · tela "Jogos anteriores". */
@OptIn(ExperimentalTestApi::class)
class PastGamesScreenTest {
    private fun row(id: String, month: String, cancelled: Boolean = false, pending: String? = null) = PastGameRowUi(
        gameId = id,
        monthHeader = month,
        day = "20",
        month = "OUT",
        title = "Terça · 20h00",
        meta = if (cancelled) "Cancelado com 8 confirmados" else "11 de 12 foram",
        cancelled = cancelled,
        statusLabel = if (cancelled) "Cancelado" else "Encerrado",
        pending = pending,
        contentDescription = "Terça, 20/10 às 20h00, Encerrado",
    )

    private val loaded = PastGamesState(
        isLoading = false,
        rows = listOf(
            row("g1", "OUTUBRO DE 2026", pending = "Acerto pendente · 3 pessoas, R$ 75,00"),
            row("g2", "OUTUBRO DE 2026", cancelled = true),
            row("g3", "SETEMBRO DE 2026"),
        ),
    )

    @Test
    fun mesesLinhasSelosEAcertoPendente() = runComposeUiTest {
        setScreen(loaded)

        onNodeWithText("Jogos anteriores").assertExists()
        onNodeWithTag(PastGamesTags.month("OUTUBRO DE 2026")).assertExists()
        onNodeWithTag(PastGamesTags.month("SETEMBRO DE 2026")).assertExists()
        onNodeWithText("Acerto pendente · 3 pessoas, R$ 75,00").assertExists()
        onNodeWithText("Cancelado com 8 confirmados").assertExists()
        onNodeWithText("Cancelado").assertExists()
    }

    @Test
    fun toqueNaLinhaAbreOJogo() = runComposeUiTest {
        val intents = mutableListOf<PastGamesIntent>()
        setScreen(loaded) { intents += it }

        onNodeWithTag(PastGamesTags.game("g2")).performClick()

        assertEquals(PastGamesIntent.OpenGame("g2"), intents.last())
    }

    @Test
    fun fimDaListaComMaisPaginasPedeAProxima() = runComposeUiTest {
        val intents = mutableListOf<PastGamesIntent>()
        setScreen(loaded.copy(canLoadMore = true)) { intents += it }

        onNodeWithTag(PastGamesTags.List).performScrollToNode(hasTestTag(PastGamesTags.game("g3")))
        waitForIdle()

        assertTrue(PastGamesIntent.LoadMore in intents)
    }

    @Test
    fun carregandoMaisEFalhaAoCarregarMais() = runComposeUiTest {
        val intents = mutableListOf<PastGamesIntent>()
        var state by androidx.compose.runtime.mutableStateOf(loaded.copy(isLoadingMore = true))
        setContent { SaqzTheme { PastGamesScreen(state, { intents += it }, {}) } }

        onNodeWithTag(PastGamesTags.List).performScrollToNode(hasTestTag(PastGamesTags.LoadingMore))
        onNodeWithText("Carregando mais jogos").assertExists()

        state = loaded.copy(loadMoreFailed = true)
        onNodeWithTag(PastGamesTags.List).performScrollToNode(hasTestTag(PastGamesTags.LoadMoreRetry))
        onNodeWithTag(PastGamesTags.LoadMoreRetry).performClick()
        assertEquals(PastGamesIntent.LoadMore, intents.last())
    }

    @Test
    fun erroNaPrimeiraCargaMostraTentarDeNovo() = runComposeUiTest {
        setScreen(PastGamesState(isLoading = false, loadFailed = true, error = GroupUiError.Network))

        onNodeWithTag(PastGamesTags.LoadFailure).assertExists()
        onAllNodesWithTag(PastGamesTags.List).assertCountEquals(0)
    }

    private fun ComposeUiTest.setScreen(state: PastGamesState, onIntent: (PastGamesIntent) -> Unit = {}) = setContent {
        SaqzTheme { PastGamesScreen(state, onIntent, {}) }
    }
}
