package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BootstrapAccessScreenTest {
    @Test fun `bootstrap renders progress without login`() = runComposeUiTest {
        setContent { SaqzTheme { BootstrapAccessScreen(SessionAccessState.Bootstrapping, {}) } }
        onNodeWithTag("bootstrap-loading").assertExists()
        onNodeWithText("Entrar").assertDoesNotExist()
    }

    @Test fun `bootstrap error retries without returning to login`() = runComposeUiTest {
        var intent: SessionIntent? = null
        setContent { SaqzTheme { BootstrapAccessScreen(SessionAccessState.BootstrapError) { intent = it } } }
        onNodeWithText("Tentar novamente").performClick()
        assertEquals(SessionIntent.RetryBootstrap, intent)
        onNodeWithText("Entrar").assertDoesNotExist()
    }
}
