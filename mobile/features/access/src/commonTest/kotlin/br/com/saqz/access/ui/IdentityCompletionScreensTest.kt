package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.namecompletion.NameCompletionIntent
import br.com.saqz.access.presentation.namecompletion.NameCompletionState
import br.com.saqz.access.presentation.passwordreset.PasswordResetIntent
import br.com.saqz.access.presentation.passwordreset.PasswordResetState
import br.com.saqz.access.presentation.verification.VerificationIntent
import br.com.saqz.access.presentation.verification.VerificationState
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun `name input emits controlled value`() = runComposeUiTest {
        var intent: NameCompletionIntent? = null; name(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Person Name")
        assertEquals(NameCompletionIntent.UpdateName("Person Name"), intent)
    }

    @Test fun `invalid name exposes associated error`() = runComposeUiTest {
        name(NameCompletionState(invalidName = true))
        onNodeWithText("Informe um nome entre 2 e 80 caracteres").assertExists()
    }

    @Test fun `name completion invokes submit`() = runComposeUiTest {
        var intent: NameCompletionIntent? = null
        name(NameCompletionState(name = "Person Name"), onIntent = { intent = it })
        onNodeWithTag(IdentityTags.NameSubmit).performClick()
        assertEquals(NameCompletionIntent.Complete, intent)
    }

    @Test fun `name loading disables field and submit`() = runComposeUiTest {
        name(NameCompletionState(isLoading = true))
        onNodeWithTag(IdentityTags.NameSubmit).assertIsNotEnabled()
    }

    @Test fun `reset email emits controlled value`() = runComposeUiTest {
        var intent: PasswordResetIntent? = null; reset(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("person@example.test")
        assertEquals(PasswordResetIntent.UpdateEmail("person@example.test"), intent)
    }

    @Test fun `reset validation state exposes invalid email`() = runComposeUiTest {
        reset(PasswordResetState(email = "invalid", validationAttempted = true))
        onNodeWithText("Informe um e-mail valido").assertExists()
    }

    @Test fun `reset submit emits exact typed intent`() = runComposeUiTest {
        var intent: PasswordResetIntent? = null
        reset(PasswordResetState(email = "person@example.test"), onIntent = { intent = it })
        onNodeWithTag(IdentityTags.ResetSubmit).performClick()
        assertEquals(PasswordResetIntent.SubmitPasswordReset, intent)
    }

    @Test fun `reset confirmation stays neutral and returns through one callback`() = runComposeUiTest {
        var intent: PasswordResetIntent? = null
        reset(PasswordResetState(resetConfirmation = true), onIntent = { intent = it })
        onNodeWithText("Se o e-mail estiver cadastrado, voce recebera as instrucoes").assertExists()
        onNodeWithTag(IdentityTags.ResetBack).performClick()
        assertEquals(PasswordResetIntent.ShowLogin, intent)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.verification(
        state: VerificationState = VerificationState(email = "person@example.test"),
        onIntent: (VerificationIntent) -> Unit = {},
    ) = setContent { SaqzTheme { VerificationScreen(state, onIntent) } }

    private fun androidx.compose.ui.test.ComposeUiTest.name(
        state: NameCompletionState = NameCompletionState(),
        onIntent: (NameCompletionIntent) -> Unit = {},
    ) = setContent { SaqzTheme { NameCompletionScreen(state, onIntent) } }

    private fun androidx.compose.ui.test.ComposeUiTest.reset(
        state: PasswordResetState = PasswordResetState(),
        onIntent: (PasswordResetIntent) -> Unit = {},
    ) = setContent { SaqzTheme { PasswordResetScreen(state, onIntent) } }

}
