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
import br.com.saqz.access.presentation.login.LoginIntent
import br.com.saqz.access.presentation.login.LoginState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_invalid_credentials
import br.com.saqz.access.presentation.UiText
import br.com.saqz.access.ui.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest {
    @Test fun `email input emits controlled value`() = runComposeUiTest {
        var intent: LoginIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("person@example.test")
        assertEquals(LoginIntent.UpdateEmail("person@example.test"), intent)
    }

    @Test fun `password input emits controlled value`() = runComposeUiTest {
        var intent: LoginIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("secret")
        assertEquals(LoginIntent.UpdatePassword("secret"), intent)
    }

    @Test fun `primary action submits password login`() = runComposeUiTest {
        var intent: LoginIntent? = null; content(onIntent = { intent = it })
        onNodeWithTag(LoginTags.Submit).performClick()
        assertEquals(LoginIntent.SubmitPasswordLogin, intent)
    }

    @Test fun `google action invokes provider flow`() = runComposeUiTest {
        var intent: LoginIntent? = null; content(onIntent = { intent = it })
        onNodeWithText("Entrar com Google").performClick()
        assertEquals(LoginIntent.SubmitGoogleLogin, intent)
    }

    @Test fun `approved visual hierarchy exposes the complete login journey`() = runComposeUiTest {
        content()
        onNodeWithText("Organize seu grupo.", substring = true).assertExists()
        onNodeWithText("Jogue junto.", substring = true).assertExists()
        onNodeWithText("Entre na sua conta e mantenha sua galera sempre alinhada.").assertExists()
        onNodeWithText("ou continue com").assertExists()
        onNodeWithText("Entrar com Google").assertExists()
    }

    // VUL-35: registration and password reset are gone; Google is the only account-creation
    // path left, so the login surface must not offer a link with nowhere to go.
    @Test fun `registration and password reset links are absent`() = runComposeUiTest {
        content()
        onNodeWithText("Criar conta").assertDoesNotExist()
        onNodeWithText("Ainda não tem uma conta?").assertDoesNotExist()
        onNodeWithText("Esqueci minha senha").assertDoesNotExist()
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

    @Test fun `loading disables all submit actions`() = runComposeUiTest {
        content(state = LoginState(isLoading = true))
        onNodeWithTag(LoginTags.Submit).assertIsNotEnabled()
        onNodeWithTag(LoginTags.Google).assertIsNotEnabled()
    }

    @Test fun `stable actionable error is rendered`() = runComposeUiTest {
        content(state = LoginState(error = UiText.Res(Res.string.auth_error_invalid_credentials)))
        onNodeWithText("E-mail ou senha invalidos").assertExists()
    }

    @Test fun `password starts with accessible reveal control`() = runComposeUiTest {
        content(state = LoginState(password = "secret"))
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
                        LoginScreen(LoginState(), {})
                    }
                }
            }
        }
        onNodeWithTag(LoginTags.Submit).performScrollTo().assertExists()
        onNodeWithTag(LoginTags.Google).performScrollTo().assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.content(
        state: LoginState = LoginState(),
        onIntent: (LoginIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            LoginScreen(state, onIntent)
        }
    }
}
