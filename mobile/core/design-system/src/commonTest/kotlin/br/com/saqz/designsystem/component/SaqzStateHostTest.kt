package br.com.saqz.designsystem.component

import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.core.common.state.SaqzUiState
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzStateHostTest {

    @Test
    fun rendersLoading() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzStateHost(state = SaqzUiState.Loading, content = {}) }
        }
        onNodeWithContentDescription("Carregando").assertExists()
    }

    @Test
    fun rendersContentValue() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzStateHost(
                    state = SaqzUiState.Content("Olá mundo"),
                    content = { value -> Text(value) },
                )
            }
        }
        onNodeWithText("Olá mundo").assertExists()
    }

    @Test
    fun rendersEmpty() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzStateHost(state = SaqzUiState.Empty, content = {}) }
        }
        onNodeWithText("Nada por aqui").assertExists()
    }

    @Test
    fun rendersError() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzStateHost(state = SaqzUiState.Error, content = {}) }
        }
        onNodeWithText("Não foi possível carregar").assertExists()
    }

    @Test
    fun retryActivatesOnce() = runComposeUiTest {
        var retries = 0
        setContent {
            SaqzTheme {
                SaqzStateHost(
                    state = SaqzUiState.Error,
                    content = {},
                    onRetry = { retries++ },
                )
            }
        }
        onNodeWithText("Tentar novamente").performClick()
        waitForIdle()
        assertEquals(1, retries)
    }

    @Test
    fun customSlotsReplaceDefaults() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzStateHost(
                    state = SaqzUiState.Loading,
                    loading = { Text("meu-loading") },
                    content = {},
                )
            }
        }
        onNodeWithText("meu-loading").assertExists()
        // The default spinner (with its accessible name) must not appear.
        onNodeWithContentDescription("Carregando").assertDoesNotExist()
    }

    @Test
    fun normalTransitionUses220ms() {
        assertEquals(220, saqzStateTransition(SaqzMotionPolicy.Normal).durationMillis)
    }

    @Test
    fun reducedTransitionHasNoTranslate() {
        assertEquals(0.dp, saqzStateTransition(SaqzMotionPolicy.Reduced).translation)
    }

    @Test
    fun semanticsOrderMatchesState() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzStateHost(state = SaqzUiState.Error, content = {}) }
        }
        // Reading order within the error state: message precedes the retry action.
        val messageTop = onNodeWithText("Não foi possível carregar").getUnclippedBoundsInRoot().top
        val retryTop = onNodeWithText("Tentar novamente").getUnclippedBoundsInRoot().top
        onNodeWithText("Tentar novamente").assertIsDisplayed()
        assertTrue(messageTop < retryTop, "error message must precede retry in reading order")
    }
}
