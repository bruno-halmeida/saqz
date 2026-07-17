package br.com.saqz.access.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
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
        var calls = 0; verification(onConfirm = { calls++ })
        onNodeWithTag(IdentityTags.Verify).performClick()
        assertEquals(1, calls)
    }

    @Test fun `resend action invokes provider request`() = runComposeUiTest {
        var calls = 0; verification(onResend = { calls++ })
        onNodeWithTag(IdentityTags.Resend).performClick()
        assertEquals(1, calls)
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
        var value = ""; name(onNameChange = { value = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Person Name")
        assertEquals("Person Name", value)
    }

    @Test fun `invalid name exposes associated error`() = runComposeUiTest {
        name(SessionAccessState.CompletingName(user, invalidName = true))
        onNodeWithText("Informe um nome entre 2 e 80 caracteres").assertExists()
    }

    @Test fun `name completion invokes submit`() = runComposeUiTest {
        var calls = 0; name(SessionAccessState.CompletingName(user, name = "Person Name"), onSubmit = { calls++ })
        onNodeWithTag(IdentityTags.NameSubmit).performClick()
        assertEquals(1, calls)
    }

    @Test fun `name loading disables field and submit`() = runComposeUiTest {
        name(SessionAccessState.CompletingName(user, isLoading = true))
        onNodeWithTag(IdentityTags.NameSubmit).assertIsNotEnabled()
    }

    @Test fun `reset email emits controlled value`() = runComposeUiTest {
        var value = ""; reset(onEmailChange = { value = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("person@example.test")
        assertEquals("person@example.test", value)
    }

    @Test fun `reset accepts only syntactically valid email`() = runComposeUiTest {
        var state by mutableStateOf(AuthenticationState(email = "invalid"))
        var calls = 0
        setContent {
            SaqzTheme {
                PasswordResetScreen(state, { state = state.copy(email = it) }, { calls++ }, {})
            }
        }
        onNodeWithTag(IdentityTags.ResetSubmit).performClick()
        assertEquals(0, calls)
        onNodeWithText("Informe um e-mail valido").assertExists()
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextClearance()
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("person@example.test")
        onNodeWithTag(IdentityTags.ResetSubmit).performClick()
        assertEquals(1, calls)
    }

    @Test fun `reset confirmation stays neutral and returns through one callback`() = runComposeUiTest {
        var calls = 0
        reset(AuthenticationState(resetConfirmation = true, error = AuthUiError.UNKNOWN), onBack = { calls++ })
        onNodeWithText("Se o e-mail estiver cadastrado, voce recebera as instrucoes").assertExists()
        onNodeWithTag(IdentityTags.ResetBack).performClick()
        assertEquals(1, calls)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.verification(
        state: SessionAccessState.AwaitingVerification = SessionAccessState.AwaitingVerification(user),
        onConfirm: () -> Unit = {},
        onResend: () -> Unit = {},
    ) = setContent { SaqzTheme { VerificationScreen(state, onConfirm, onResend) } }

    private fun androidx.compose.ui.test.ComposeUiTest.name(
        state: SessionAccessState.CompletingName = SessionAccessState.CompletingName(user),
        onNameChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
    ) = setContent { SaqzTheme { NameCompletionScreen(state, onNameChange, onSubmit) } }

    private fun androidx.compose.ui.test.ComposeUiTest.reset(
        state: AuthenticationState = AuthenticationState(),
        onEmailChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = setContent { SaqzTheme { PasswordResetScreen(state, onEmailChange, onSubmit, onBack) } }

    private companion object {
        val user = NativeUser("subject", "person@example.test", false, "Person Name")
    }
}
