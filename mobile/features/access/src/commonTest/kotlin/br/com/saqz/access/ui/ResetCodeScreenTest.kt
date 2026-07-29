package br.com.saqz.access.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.presentation.resetcode.ResetCodeIntent
import br.com.saqz.access.presentation.resetcode.ResetCodeState
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class ResetCodeScreenTest {

    @Test fun `1e shows the running countdown and the way back to sign in`() = runComposeUiTest {
        content(state = ResetCodeState(email = EMAIL, resendSeconds = 42))

        onNodeWithTag(ResetCodeTags.Resend).assertTextEquals("Não chegou? Reenviar código · 0:42")
        onNodeWithText("Verificar código").assertExists()
        onNodeWithTag(ResetCodeTags.SignIn).assertExists()
        onNodeWithTag(ResetCodeTags.Resent).assertDoesNotExist()
        onNodeWithTag(ResetCodeTags.Expired).assertDoesNotExist()
    }

    @Test fun `1e keeps the resend untouchable while the countdown runs`() = runComposeUiTest {
        content(state = ResetCodeState(email = EMAIL, resendSeconds = 1))

        onNodeWithTag(ResetCodeTags.Resend).assertIsNotEnabled()
    }

    @Test fun `1e frees the resend when the countdown reaches zero`() = runComposeUiTest {
        var intent: ResetCodeIntent? = null
        content(state = ResetCodeState(email = EMAIL, resendSeconds = 0), onIntent = { intent = it })

        onNodeWithTag(ResetCodeTags.Resend).assertHasClickAction().assertIsEnabled().performClick()

        assertEquals(ResetCodeIntent.Resend, intent)
    }

    @Test fun `1f swaps the countdown wording and hides the sign in link`() = runComposeUiTest {
        content(state = ResetCodeState(email = EMAIL, resendSeconds = 59, resent = true))

        onNodeWithTag(ResetCodeTags.Resent).assertExists()
        onNodeWithTag(ResetCodeTags.Resend).assertTextEquals("Reenviar novamente em 0:59")
        onNodeWithTag(ResetCodeTags.SignIn).assertDoesNotExist()
        // O reenvio não muda o que o botão faz: o código novo ainda precisa ser conferido.
        onNodeWithText("Verificar código").assertExists()
    }

    @Test fun `1k invalid code keeps the digits and the verify action`() = runComposeUiTest {
        content(
            state = ResetCodeState(email = EMAIL, code = "1359", remainingAttempts = 2),
        )

        onNodeWithText(INVALID_CODE_LINE).assertExists()
        onNodeWithTag(ResetCodeTags.Expired).assertDoesNotExist()
        onNodeWithText("Verificar código").assertExists()
    }

    @Test fun `1k expired code turns the action into a resend`() = runComposeUiTest {
        var intent: ResetCodeIntent? = null
        content(
            state = ResetCodeState(email = EMAIL, code = "1359", resendSeconds = 0, expired = true),
            onIntent = { intent = it },
        )

        onNodeWithTag(ResetCodeTags.Expired).assertExists()
        // Expirado não conta mais janela: quem pede outro código é o botão.
        onNodeWithTag(ResetCodeTags.Resend).assertDoesNotExist()
        onNodeWithTag(ResetCodeTags.SignIn).assertDoesNotExist()
        onNodeWithText("Reenviar código").assertExists()
        onNodeWithTag(ResetCodeTags.Submit).performClick()

        assertEquals(ResetCodeIntent.Resend, intent)
    }

    @Test fun `1k draws both refusals at once without merging them`() = runComposeUiTest {
        content(
            state = ResetCodeState(
                email = EMAIL,
                code = "1359",
                resendSeconds = 0,
                remainingAttempts = 2,
                expired = true,
            ),
        )

        onNodeWithText(INVALID_CODE_LINE).assertExists()
        onNodeWithTag(ResetCodeTags.Expired).assertExists()
    }

    @Test fun `a refusal without a drawing of its own still says something`() = runComposeUiTest {
        content(
            state = ResetCodeState(
                email = EMAIL,
                failure = UiText.Raw("Verifique sua conexao e tente novamente"),
            ),
        )

        onNodeWithTag(ResetCodeTags.Failure).assertTextContains("Verifique sua conexao e tente novamente")
    }

    @Test fun `typing forwards the digits to the view model`() = runComposeUiTest {
        var intent: ResetCodeIntent? = null
        content(onIntent = { intent = it })

        onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput("1359")

        assertEquals(ResetCodeIntent.UpdateCode("1359"), intent)
    }

    @Test fun `the in flight request locks both actions`() = runComposeUiTest {
        var intent: ResetCodeIntent? = null
        content(
            state = ResetCodeState(email = EMAIL, code = "1359", resendSeconds = 0, verifying = true),
            onIntent = { intent = it },
        )

        onNodeWithTag(ResetCodeTags.Submit).assertIsNotEnabled()
        onNodeWithTag(ResetCodeTags.Resend).assertIsNotEnabled()
        assertNull(intent)
    }

    @Test fun `the verify window locks the button without touching the resend`() = runComposeUiTest {
        content(
            state = ResetCodeState(
                email = EMAIL,
                code = "1359",
                resendSeconds = 0,
                verifyRetrySeconds = 30,
            ),
        )

        onNodeWithTag(ResetCodeTags.Submit).assertIsNotEnabled()
        // O balde do reenvio é outro: o link continua vivo.
        onNodeWithTag(ResetCodeTags.Resend).assertIsEnabled()
    }

    @Test fun `the verify action stays reachable at rest`() = runComposeUiTest {
        var intent: ResetCodeIntent? = null
        content(state = ResetCodeState(email = EMAIL, code = "1359"), onIntent = { intent = it })

        onNodeWithTag(ResetCodeTags.Submit).assertIsEnabled().performClick()

        assertEquals(ResetCodeIntent.Verify, intent)
    }

    @Test fun `back and sign in leave the screen`() = runComposeUiTest {
        var backs = 0
        var signIns = 0
        content(onBack = { backs++ }, onSignIn = { signIns++ })

        onNodeWithTag(ResetCodeTags.Back).performClick()
        onNodeWithTag(ResetCodeTags.SignIn).performClick()

        assertEquals(1, backs)
        assertEquals(1, signIns)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.content(
        state: ResetCodeState = ResetCodeState(email = EMAIL),
        onIntent: (ResetCodeIntent) -> Unit = {},
        onBack: () -> Unit = {},
        onSignIn: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            ResetCodeScreen(state, onIntent, onBack, onSignIn, Modifier)
        }
    }

    private companion object {
        const val EMAIL = "ana@exemplo.com"
        const val INVALID_CODE_LINE = "Código incorreto. Restam 2 tentativas."
    }
}
