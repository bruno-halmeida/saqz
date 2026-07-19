package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RegistrationScreenTest {
    @Test fun `name input emits controlled value`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null; content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Person Name")
        assertEquals(AuthenticationIntent.UpdateName("Person Name"), intent)
    }

    @Test fun `email input emits controlled value`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null; content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("person@example.test")
        assertEquals(AuthenticationIntent.UpdateEmail("person@example.test"), intent)
    }

    @Test fun `password input emits controlled value`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null; content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[2].performTextInput("secret")
        assertEquals(AuthenticationIntent.UpdatePassword("secret"), intent)
    }

    @Test fun `valid form submits`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null
        content(AuthenticationState(name = "Person Name", email = "person@example.test"), onIntent = { intent = it })
        onNodeWithTag(RegistrationTags.Submit).performClick()
        assertEquals(AuthenticationIntent.SubmitRegistration, intent)
    }

    @Test fun `google action invokes the only social provider flow`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null
        content(onIntent = { intent = it })

        onNodeWithTag(RegistrationTags.Google).performClick()

        assertEquals(AuthenticationIntent.SubmitGoogleLogin, intent)
        onNodeWithText("Telefone").assertDoesNotExist()
        onNodeWithText("Continuar com Apple").assertDoesNotExist()
        onNodeWithText("Continuar com Facebook").assertDoesNotExist()
    }

    @Test fun `reference hierarchy exposes complete registration journey`() = runComposeUiTest {
        content()

        onNodeWithTag(RegistrationTags.Title).assertTextContains("Criar conta")
        onNodeWithText("Preencha seus dados para criar sua conta.").assertExists()
        onNodeWithText("Nome").assertExists()
        onNodeWithText("E-mail").assertExists()
        onNodeWithText("Senha").assertExists()
        onNodeWithText("ou continue com").assertExists()
        onNodeWithText("Continuar com Google").assertExists()
        onNodeWithText("Voltar para entrar").assertExists()
    }

    @Test fun `registration actions retain minimum touch targets`() = runComposeUiTest {
        content()

        onNodeWithTag(RegistrationTags.Submit).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(RegistrationTags.Google).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(RegistrationTags.Back).assertHeightIsAtLeast(48.dp)
    }

    @Test fun `blank name validation state exposes field error`() = runComposeUiTest {
        content(AuthenticationState(email = "person@example.test", validationAttempted = true))
        onNodeWithText("Informe seu nome").assertExists()
    }

    @Test fun `invalid email validation state exposes field error`() = runComposeUiTest {
        content(AuthenticationState(name = "Person Name", email = "invalid", validationAttempted = true))
        onNodeWithText("Informe um e-mail valido").assertExists()
    }

    @Test fun `loading disables submit and back actions`() = runComposeUiTest {
        content(AuthenticationState(isLoading = true))
        onNodeWithTag(RegistrationTags.Submit).assertIsNotEnabled()
        onNodeWithTag(RegistrationTags.Google).assertIsNotEnabled()
        onNodeWithTag(RegistrationTags.Back).assertIsNotEnabled()
    }

    @Test fun `back action emits login intent`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null
        content(onIntent = { intent = it })

        onNodeWithTag(RegistrationTags.Back).performClick()

        assertEquals(AuthenticationIntent.ShowLogin, intent)
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

    @Test fun `compact viewport at maximum font scale keeps registration actions reachable`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.size(280.dp, 320.dp)) {
                        RegistrationScreen(AuthenticationState(), {})
                    }
                }
            }
        }

        onNodeWithTag(RegistrationTags.Google).performScrollTo().assertExists()
        onNodeWithTag(RegistrationTags.Back).performScrollTo().assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.content(
        state: AuthenticationState = AuthenticationState(),
        onIntent: (AuthenticationIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            RegistrationScreen(state, onIntent)
        }
    }
}
