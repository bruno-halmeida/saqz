package br.com.saqz.profile.presentation.exit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test

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
}
