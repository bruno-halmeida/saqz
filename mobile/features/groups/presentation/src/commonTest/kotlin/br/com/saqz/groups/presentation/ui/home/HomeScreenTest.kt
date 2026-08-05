package br.com.saqz.groups.presentation.ui.home

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @Test
    fun `loading state renders the home skeleton`() = runComposeUiTest {
        setScreen(HomeState())

        onNodeWithTag(HomeTags.Loading).assertIsDisplayed()
    }

    @Test
    fun `error state renders the standard retry action`() = runComposeUiTest {
        val intents = mutableListOf<HomeIntent>()
        setScreen(HomeState(isLoading = false, loadFailed = true), intents::add)

        onNodeWithTag(HomeTags.Error).assertIsDisplayed()
        onNodeWithText("Não foi possível carregar sua Home").assertIsDisplayed()
        onNodeWithText("Tentar novamente").performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.Retry), intents)
    }

    @Test
    fun `loaded state renders the profile greeting`() = runComposeUiTest {
        setScreen(HomeState(isLoading = false, displayName = "Bruna"))

        onNodeWithTag(HomeTags.Content).assertIsDisplayed()
        onNodeWithText("Fala, Bruna!").assertIsDisplayed()
        onNodeWithText("Os conteúdos da sua Home aparecem aqui.").assertIsDisplayed()
    }

    private fun ComposeUiTest.setScreen(
        state: HomeState,
        onIntent: (HomeIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            HomeScreen(state = state, onIntent = onIntent)
        }
    }
}
