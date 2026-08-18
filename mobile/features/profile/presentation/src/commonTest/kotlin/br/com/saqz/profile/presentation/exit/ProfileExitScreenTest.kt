package br.com.saqz.profile.presentation.exit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileExitScreenTest {
    @Test
    fun `folha de saida mostra so logout e ficar`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                ProfileExitScreen(onClose = {}, onLogout = {})
            }
        }

        onNodeWithTag(ProfileExitTags.Logout).assertExists()
        onNodeWithTag(ProfileExitTags.Stay).assertExists()
        onNodeWithText("Sair da conta?").assertExists()
        onNodeWithText("Excluir conta").assertDoesNotExist()
        onNodeWithText("Excluir minha conta").assertDoesNotExist()
    }

    @Test
    fun `sair da conta dispara o logout`() = runComposeUiTest {
        var logoutCalls = 0
        setContent {
            SaqzTheme {
                ProfileExitScreen(
                    onClose = {},
                    onLogout = { logoutCalls += 1 },
                )
            }
        }

        onNodeWithTag(ProfileExitTags.Logout).performClick()

        assertEquals(1, logoutCalls)
    }

    @Test
    fun `ficar fecha a folha sem sair`() = runComposeUiTest {
        var closeCalls = 0
        var logoutCalls = 0
        setContent {
            SaqzTheme {
                ProfileExitScreen(
                    onClose = { closeCalls += 1 },
                    onLogout = { logoutCalls += 1 },
                )
            }
        }

        onNodeWithTag(ProfileExitTags.Stay).performClick()

        assertEquals(1, closeCalls)
        assertEquals(0, logoutCalls)
    }
}
