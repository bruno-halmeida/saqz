package br.com.saqz.groups.presentation.ui.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeUpcomingGameUi
import br.com.saqz.groups.presentation.home.HomeUpcomingStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HomeUpcomingScreenTest {
    @Test
    fun `upcoming rows render and open the game`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setContent { SaqzTheme { HomeScreen(state = state(listOf(pending(), going())), onIntent = intents::add) } }

        onNodeWithTag(HomeUpcomingTags.Section).assertIsDisplayed()
        onNodeWithText("Próximos jogos").assertIsDisplayed()
        onNodeWithText("Vôlei Pacaembu · 20h00").assertIsDisplayed()
        onNodeWithText("Sem resposta").assertIsDisplayed()
        onNodeWithText("Você vai").assertIsDisplayed()
        onNodeWithTag(HomeUpcomingTags.row("game-3")).performClick()

        assertEquals(listOf<HomeIntent>(HomeIntent.OpenGame("pacaembu", "game-3")), intents)
    }

    @Test
    fun `no upcoming games renders no section`() = runComposeUiTest {
        setContent { SaqzTheme { HomeScreen(state = state(emptyList()), onIntent = {}) } }

        onAllNodesWithTag(HomeUpcomingTags.Section).assertCountEquals(0)
    }

    private fun state(games: List<HomeUpcomingGameUi>) = HomeState(
        isLoading = false,
        displayName = "Bruna",
        member = HomeMemberUi(
            nextGame = null,
            groups = listOf(HomeGroupUi("ceret", "Vôlei do CERET", "26 pessoas · 18 jogos")),
            upcomingGames = games,
        ),
    )

    private fun pending() = HomeUpcomingGameUi(
        groupId = "pacaembu", gameId = "game-3", day = "30", month = "JUL",
        title = "Vôlei Pacaembu · 20h00", meta = "Quinta · 6 confirmados",
        status = HomeUpcomingStatus.Pending, statusLabel = "Sem resposta",
        contentDescription = "Vôlei Pacaembu, 30/07 às 20h00, Sem resposta",
    )

    private fun going() = HomeUpcomingGameUi(
        groupId = "ceret", gameId = "game-4", day = "4", month = "AGO",
        title = "Vôlei do CERET · 19h30", meta = "Terça · 3 confirmados",
        status = HomeUpcomingStatus.Going, statusLabel = "Você vai",
        contentDescription = "Vôlei do CERET, 04/08 às 19h30, Você vai",
    )
}
