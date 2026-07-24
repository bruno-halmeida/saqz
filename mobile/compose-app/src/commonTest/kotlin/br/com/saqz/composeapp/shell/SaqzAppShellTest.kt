package br.com.saqz.composeapp.shell

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SaqzAppShellTest {

    @Test
    fun rendersOnlyTheSignedInPlaceholderAndLogout() = runComposeUiTest {
        setContent { SaqzTheme { SaqzAppShell(onLogout = {}) } }
        onNodeWithTag(SaqzShellContentTag).assertIsDisplayed()
        onNodeWithText("Você está conectado.").assertIsDisplayed()
        onNodeWithText("Sair").assertIsDisplayed()
        // The reset removed the bottom nav: the shell exposes no tabs at all.
        assertEquals(
            0,
            onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun logoutActivatesOnce() = runComposeUiTest {
        var logouts = 0
        setContent { SaqzTheme { SaqzAppShell(onLogout = { logouts++ }) } }
        onNodeWithText("Sair").performClick()
        waitForIdle()
        assertEquals(1, logouts)
    }
}
