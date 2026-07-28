package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.verification.VerificationIntent
import br.com.saqz.access.presentation.verification.VerificationState
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Só a tela de verificação sobrou aqui (VUL-84): nome e telefone se fundiram na 1c e as
 * duas telas antigas saíram com o estado que as alimentava. Esta continua coberta enquanto
 * existir — o VUL-91 apaga tela e teste juntos.
 */
@OptIn(ExperimentalTestApi::class)
class IdentityCompletionScreensTest {
    @Test fun `verification identifies pending email`() = runComposeUiTest {
        verification()
        onNodeWithText("person@example.test").assertExists()
    }

    @Test fun `already verified action invokes confirmation`() = runComposeUiTest {
        var intent: VerificationIntent? = null; verification(onIntent = { intent = it })
        onNodeWithTag(IdentityTags.Verify).performClick()
        assertEquals(VerificationIntent.Confirm, intent)
    }

    @Test fun `resend action invokes provider request`() = runComposeUiTest {
        var intent: VerificationIntent? = null; verification(onIntent = { intent = it })
        onNodeWithTag(IdentityTags.Resend).performClick()
        assertEquals(VerificationIntent.Resend, intent)
    }

    @Test fun `sent verification reports cooldown and disables resend`() = runComposeUiTest {
        verification(VerificationState(email = "person@example.test", verificationSent = true))
        onNodeWithText("E-mail enviado. Aguarde antes de reenviar").assertExists()
        onNodeWithTag(IdentityTags.Resend).assertIsNotEnabled()
    }

    @Test fun `verification loading disables duplicate actions`() = runComposeUiTest {
        verification(VerificationState(isLoading = true))
        onNodeWithTag(IdentityTags.Verify).assertIsNotEnabled()
        onNodeWithTag(IdentityTags.Resend).assertIsNotEnabled()
    }

    @Test fun `verification failure is actionable`() = runComposeUiTest {
        verification(VerificationState(error = AuthUiError.NETWORK_UNAVAILABLE))
        onNodeWithText("Verifique sua conexao e tente novamente").assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.verification(
        state: VerificationState = VerificationState(email = "person@example.test"),
        onIntent: (VerificationIntent) -> Unit = {},
    ) = setContent { SaqzTheme { VerificationScreen(state, onIntent) } }
}
