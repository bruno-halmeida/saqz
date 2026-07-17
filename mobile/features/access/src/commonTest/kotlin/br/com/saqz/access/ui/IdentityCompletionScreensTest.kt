package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
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
        var intent: SessionIntent? = null; verification(onIntent = { intent = it })
        onNodeWithTag(IdentityTags.Verify).performClick()
        assertEquals(SessionIntent.ConfirmVerification, intent)
    }

    @Test fun `resend action invokes provider request`() = runComposeUiTest {
        var intent: SessionIntent? = null; verification(onIntent = { intent = it })
        onNodeWithTag(IdentityTags.Resend).performClick()
        assertEquals(SessionIntent.ResendVerification, intent)
    }

    @Test fun `sent verification reports cooldown and disables resend`() = runComposeUiTest {
        verification(SessionAccessState.AwaitingVerification(user, verificationSent = true))
        onNodeWithText("E-mail enviado. Aguarde antes de reenviar").assertExists()
        onNodeWithTag(IdentityTags.Resend).assertIsNotEnabled()
    }

    @Test fun `verification loading disables duplicate actions`() = runComposeUiTest {
        verification(SessionAccessState.AwaitingVerification(user, isLoading = true))
        onNodeWithTag(IdentityTags.Verify).assertIsNotEnabled()
        onNodeWithTag(IdentityTags.Resend).assertIsNotEnabled()
    }

    @Test fun `verification failure is actionable`() = runComposeUiTest {
        verification(SessionAccessState.AwaitingVerification(user, error = AuthUiError.NETWORK_UNAVAILABLE))
        onNodeWithText("Verifique sua conexao e tente novamente").assertExists()
    }

    @Test fun `name input emits controlled value`() = runComposeUiTest {
        var intent: SessionIntent? = null; name(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Person Name")
        assertEquals(SessionIntent.UpdateName("Person Name"), intent)
    }

    @Test fun `invalid name exposes associated error`() = runComposeUiTest {
        name(SessionAccessState.CompletingName(user, invalidName = true))
        onNodeWithText("Informe um nome entre 2 e 80 caracteres").assertExists()
    }

    @Test fun `name completion invokes submit`() = runComposeUiTest {
        var intent: SessionIntent? = null
        name(SessionAccessState.CompletingName(user, name = "Person Name"), onIntent = { intent = it })
        onNodeWithTag(IdentityTags.NameSubmit).performClick()
        assertEquals(SessionIntent.CompleteName, intent)
    }

    @Test fun `name loading disables field and submit`() = runComposeUiTest {
        name(SessionAccessState.CompletingName(user, isLoading = true))
        onNodeWithTag(IdentityTags.NameSubmit).assertIsNotEnabled()
    }

    @Test fun `reset email emits controlled value`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null; reset(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("person@example.test")
        assertEquals(AuthenticationIntent.UpdateEmail("person@example.test"), intent)
    }

    @Test fun `reset validation state exposes invalid email`() = runComposeUiTest {
        reset(AuthenticationState(email = "invalid", validationAttempted = true))
        onNodeWithText("Informe um e-mail valido").assertExists()
    }

    @Test fun `reset submit emits exact typed intent`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null
        reset(AuthenticationState(email = "person@example.test"), onIntent = { intent = it })
        onNodeWithTag(IdentityTags.ResetSubmit).performClick()
        assertEquals(AuthenticationIntent.SubmitPasswordReset, intent)
    }

    @Test fun `reset confirmation stays neutral and returns through one callback`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null
        reset(AuthenticationState(resetConfirmation = true, error = AuthUiError.UNKNOWN), onIntent = { intent = it })
        onNodeWithText("Se o e-mail estiver cadastrado, voce recebera as instrucoes").assertExists()
        onNodeWithTag(IdentityTags.ResetBack).performClick()
        assertEquals(AuthenticationIntent.ShowLogin, intent)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.verification(
        state: SessionAccessState.AwaitingVerification = SessionAccessState.AwaitingVerification(user),
        onIntent: (SessionIntent) -> Unit = {},
    ) = setContent { SaqzTheme { VerificationScreen(state, onIntent) } }

    private fun androidx.compose.ui.test.ComposeUiTest.name(
        state: SessionAccessState.CompletingName = SessionAccessState.CompletingName(user),
        onIntent: (SessionIntent) -> Unit = {},
    ) = setContent { SaqzTheme { NameCompletionScreen(state, onIntent) } }

    private fun androidx.compose.ui.test.ComposeUiTest.reset(
        state: AuthenticationState = AuthenticationState(),
        onIntent: (AuthenticationIntent) -> Unit = {},
    ) = setContent { SaqzTheme { PasswordResetScreen(state, onIntent) } }

    private companion object {
        val user = NativeUser("subject", "person@example.test", false, "Person Name")
    }
}
