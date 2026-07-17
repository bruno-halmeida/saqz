package br.com.saqz.access.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RegistrationScreenTest {
    @Test fun `name input emits controlled value`() = runComposeUiTest {
        var value = ""; content(onNameChange = { value = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Person Name")
        assertEquals("Person Name", value)
    }

    @Test fun `email input emits controlled value`() = runComposeUiTest {
        var value = ""; content(onEmailChange = { value = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("person@example.test")
        assertEquals("person@example.test", value)
    }

    @Test fun `password input emits controlled value`() = runComposeUiTest {
        var value = ""; content(onPasswordChange = { value = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[2].performTextInput("secret")
        assertEquals("secret", value)
    }

    @Test fun `valid form submits`() = runComposeUiTest {
        var calls = 0
        content(AuthenticationState(name = "Person Name", email = "person@example.test"), onSubmit = { calls++ })
        onNodeWithTag(RegistrationTags.Submit).performClick()
        assertEquals(1, calls)
    }

    @Test fun `blank name blocks submit and exposes field error`() = runComposeUiTest {
        var calls = 0
        content(AuthenticationState(email = "person@example.test"), onSubmit = { calls++ })
        onNodeWithTag(RegistrationTags.Submit).performClick()
        onNodeWithText("Informe seu nome").assertExists()
        assertEquals(0, calls)
    }

    @Test fun `invalid email blocks submit and exposes field error`() = runComposeUiTest {
        content(AuthenticationState(name = "Person Name", email = "invalid"))
        onNodeWithTag(RegistrationTags.Submit).performClick()
        onNodeWithText("Informe um e-mail valido").assertExists()
    }

    @Test fun `loading disables submit and back actions`() = runComposeUiTest {
        content(AuthenticationState(isLoading = true))
        onNodeWithTag(RegistrationTags.Submit).assertIsNotEnabled()
        onNodeWithTag(RegistrationTags.Back).assertIsNotEnabled()
    }

    @Test fun `firebase password error is associated with password field`() = runComposeUiTest {
        content(AuthenticationState(error = AuthUiError.WEAK_PASSWORD))
        onNodeWithTag(RegistrationTags.Password).assertTextContains("Escolha uma senha mais forte")
    }

    @Test fun `fields and password reveal control are accessible`() = runComposeUiTest {
        content()
        assertEquals(3, onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithContentDescription("Mostrar senha").assertHasClickAction()
    }

    @Test fun `restoration snapshot excludes password`() {
        val saved = AuthenticationState(name = "Person Name", email = "person@example.test", password = "secret").registrationSavedState()
        assertEquals(
            AuthenticationState(screen = AuthScreen.REGISTRATION, name = "Person Name", email = "person@example.test"),
            saved.restore(),
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.content(
        state: AuthenticationState = AuthenticationState(),
        onNameChange: (String) -> Unit = {},
        onEmailChange: (String) -> Unit = {},
        onPasswordChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {},
        onBack: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            RegistrationScreen(state, onNameChange, onEmailChange, onPasswordChange, onSubmit, onBack)
        }
    }
}
