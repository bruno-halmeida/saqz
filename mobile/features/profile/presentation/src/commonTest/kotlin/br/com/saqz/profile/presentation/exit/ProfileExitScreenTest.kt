package br.com.saqz.profile.presentation.exit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileExitScreenTest {
    @Test
    fun `folha de saida mostra as duas acoes e a zona de exclusao`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                ProfileExitScreen(
                    state = ProfileExitState(email = "person@example.com"),
                    onIntent = {},
                    onClose = {},
                    onLogout = {},
                )
            }
        }

        onNodeWithTag(ProfileExitTags.Logout).assertExists()
        onNodeWithTag(ProfileExitTags.Stay).assertExists()
        onNodeWithTag(ProfileExitTags.Delete).assertExists()
        onNodeWithText("Sair da conta?").assertExists()
    }

    @Test
    fun `segunda confirmacao mostra o e-mail e o cancelamento`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                ProfileExitScreen(
                    state = ProfileExitState(
                        email = "person@example.com",
                        sheet = ProfileExitSheet.ConfirmDelete,
                    ),
                    onIntent = {},
                    onClose = {},
                    onLogout = {},
                )
            }
        }

        onNodeWithTag(ProfileExitTags.ConfirmationEmail).assertExists()
        onNodeWithTag(ProfileExitTags.ConfirmDelete).assertExists()
        onNodeWithTag(ProfileExitTags.CancelDelete).assertExists()
        onNodeWithText("Excluir sua conta de vez?").assertExists()
    }

    @Test
    fun `done no email de confirmacao confirma a exclusao`() = runComposeUiTest {
        var intent: ProfileExitIntent? = null
        setContent {
            SaqzTheme {
                ProfileExitScreen(
                    state = ProfileExitState(
                        email = "person@example.com",
                        sheet = ProfileExitSheet.ConfirmDelete,
                    ),
                    onIntent = { intent = it },
                    onClose = {},
                    onLogout = {},
                )
            }
        }

        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performImeAction()

        assertEquals(ProfileExitIntent.ConfirmDelete, intent)
    }

    @Test
    fun `erro de exclusao fica visivel na segunda confirmacao`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                ProfileExitScreen(
                    state = ProfileExitState(
                        email = "person@example.com",
                        sheet = ProfileExitSheet.ConfirmDelete,
                        confirmationEmail = "person@example.com",
                        error = ProfileExitError.DeleteFailed,
                    ),
                    onIntent = {},
                    onClose = {},
                    onLogout = {},
                )
            }
        }

        onNodeWithText(
            "Não foi possível excluir sua conta agora. Sua sessão continua ativa. Tente de novo.",
        ).assertExists()
    }

    @Test
    fun `exclusao em voo ignora dispensa pelo scrim`() = runComposeUiTest {
        var closeCalls = 0
        setContent {
            SaqzTheme {
                ProfileExitScreen(
                    state = ProfileExitState(
                        email = "person@example.com",
                        sheet = ProfileExitSheet.ConfirmDelete,
                        confirmationEmail = "person@example.com",
                        isDeleting = true,
                    ),
                    onIntent = {},
                    onClose = { closeCalls += 1 },
                    onLogout = {},
                )
            }
        }

        onNodeWithContentDescription("Fechar").performClick()

        assertEquals(0, closeCalls)
    }
}
