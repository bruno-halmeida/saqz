package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
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
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest {
    @Test fun `email input emits controlled value`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("person@example.test")
        assertEquals(AuthenticationIntent.UpdateEmail("person@example.test"), intent)
    }

    @Test fun `password input emits controlled value`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("secret")
        assertEquals(AuthenticationIntent.UpdatePassword("secret"), intent)
    }

    @Test fun `primary action submits password login`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null; content(onIntent = { intent = it })
        onNodeWithTag(LoginTags.Submit).performClick()
        assertEquals(AuthenticationIntent.SubmitPasswordLogin, intent)
    }

    @Test fun `google action invokes provider flow`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null; content(onIntent = { intent = it })
        onNodeWithText("Entrar com Google").performClick()
        assertEquals(AuthenticationIntent.SubmitGoogleLogin, intent)
    }

    @Test fun `approved visual hierarchy exposes the complete login journey`() = runComposeUiTest {
        content()
        onNodeWithText("Organize seu grupo.", substring = true).assertExists()
        onNodeWithText("Jogue junto.", substring = true).assertExists()
        onNodeWithText("Entre na sua conta e mantenha sua galera sempre alinhada.").assertExists()
        onNodeWithText("Esqueci minha senha").assertExists()
        onNodeWithText("ou continue com").assertExists()
        onNodeWithText("Entrar com Google").assertExists()
        onNodeWithText("Ainda não tem uma conta?").assertExists()
        onNodeWithText("Criar conta").assertExists()
    }

    @Test fun `phone apple and facebook remain outside the login surface`() = runComposeUiTest {
        content()
        onNodeWithText("E-mail ou telefone").assertDoesNotExist()
        onNodeWithText("Entrar com Apple").assertDoesNotExist()
        onNodeWithText("Entrar com Facebook").assertDoesNotExist()
    }

    @Test fun `login controls retain minimum touch targets`() = runComposeUiTest {
        content()
        onNodeWithTag(LoginTags.Email).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(LoginTags.Password).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(LoginTags.Submit).assertHeightIsAtLeast(48.dp)
        onNodeWithTag(LoginTags.Google).assertHeightIsAtLeast(48.dp)
    }

    @Test fun `registration action is reachable`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null; content(onIntent = { intent = it })
        onNodeWithText("Criar conta").performClick()
        assertEquals(AuthenticationIntent.ShowRegistration, intent)
    }

    @Test fun `password reset action is reachable`() = runComposeUiTest {
        var intent: AuthenticationIntent? = null; content(onIntent = { intent = it })
        onNodeWithText("Esqueci minha senha").performClick()
        assertEquals(AuthenticationIntent.ShowPasswordReset, intent)
    }

    @Test fun `loading disables all submit actions`() = runComposeUiTest {
        content(state = AuthenticationState(isLoading = true))
        onNodeWithTag(LoginTags.Submit).assertIsNotEnabled()
        onNodeWithTag(LoginTags.Google).assertIsNotEnabled()
    }

    @Test fun `stable actionable error is rendered`() = runComposeUiTest {
        content(state = AuthenticationState(error = AuthUiError.INVALID_CREDENTIALS))
        onNodeWithText("E-mail ou senha invalidos").assertExists()
    }

    @Test fun `password starts with accessible reveal control`() = runComposeUiTest {
        content(state = AuthenticationState(password = "secret"))
        onNodeWithContentDescription("Mostrar senha").assertHasClickAction()
    }

    @Test fun `email and password expose associated labels`() = runComposeUiTest {
        content()
        onNodeWithTag(LoginTags.Email).assertTextContains("E-mail")
        onNodeWithTag(LoginTags.Password).assertTextContains("Senha")
        assertEquals(2, onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `compact viewport at maximum font scale keeps actions reachable`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.size(280.dp, 320.dp)) {
                        LoginScreen(AuthenticationState(), {})
                    }
                }
            }
        }
        onNodeWithText("Criar conta").performScrollTo().assertExists()
        onNodeWithText("Esqueci minha senha").performScrollTo().assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.content(
        state: AuthenticationState = AuthenticationState(),
        onIntent: (AuthenticationIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            LoginScreen(state, onIntent)
        }
    }
}
