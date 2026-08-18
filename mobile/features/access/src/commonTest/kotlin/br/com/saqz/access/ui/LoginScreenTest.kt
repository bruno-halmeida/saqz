package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import br.com.saqz.access.presentation.login.LoginIntent
import br.com.saqz.access.presentation.login.LoginState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.login_error_credentials
import br.com.saqz.access.resources.login_error_email_invalid
import br.com.saqz.access.resources.login_error_password
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest {

    private companion object {
        // Espelham `fluxo1.campo` e `fluxo1.gapDosCampos.padrao` do ui-contract.json —
        // 54 → 74 de altura e 12 entre os campos. `exportFieldMetricsAreTheContract`
        // amarra os dois: mexer no export sem mexer aqui reprova.
        val FieldErrorDelta = 20.dp
        val FieldGap = 12.dp
    }

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

    @Test fun `done on the password field submits password login`() = runComposeUiTest {
        var intent: LoginIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performImeAction()
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

    // O VUL-35 exigia a ausência destes links, porque o reset tinha tirado os destinos.
    // O VUL-84 os devolve: `Register` (1b) e `ForgotPassword` (1d) existem de novo, e o
    // export desenha as duas saídas na 1a.
    @Test fun `registration and password reset links are offered again`() = runComposeUiTest {
        content()
        onNodeWithText("Esqueci minha senha").assertExists()
        onNodeWithText("Ainda não tem uma conta?").assertExists()
        onNodeWithText("Criar conta ›").assertExists()
    }

    @Test fun `forgot password link leaves for the reset flow`() = runComposeUiTest {
        var left = false
        content(onForgotPassword = { left = true })
        onNodeWithTag(LoginTags.ForgotPassword).performClick()
        assertTrue(left)
    }

    @Test fun `create account link leaves for registration`() = runComposeUiTest {
        var left = false
        content(onCreateAccount = { left = true })
        onNodeWithTag(LoginTags.CreateAccount).performClick()
        assertTrue(left)
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

    @Test fun `password starts with accessible reveal control`() = runComposeUiTest {
        content(state = LoginState(password = "secret"))
        onNodeWithContentDescription("Mostrar senha").assertHasClickAction()
    }

    @Test fun `email and password expose associated labels`() = runComposeUiTest {
        content()
        // Com rótulo embutido o campo não desenha o texto do label — o placeholder do
        // export ocupa a linha —, então o nome acessível é a descrição de conteúdo.
        onNodeWithContentDescription("E-mail", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Senha", useUnmergedTree = true).assertExists()
        assertEquals(2, onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `compact viewport at maximum font scale keeps actions reachable`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.size(280.dp, 320.dp)) {
                        LoginScreen(LoginState(), {}, {}, {})
                    }
                }
            }
        }
        onNodeWithTag(LoginTags.Submit).performScrollTo().assertExists()
        onNodeWithTag(LoginTags.Google).performScrollTo().assertExists()
    }

    // ---- 1i ----

    // A primeira diferença do 1i: o subtítulo some e o alerta ocupa o lugar dele. Os dois
    // juntos empurrariam os campos para fora do primeiro dobra.
    @Test fun `the refusal alert takes the place of the subtitle`() = runComposeUiTest {
        content(state = refused())
        onNodeWithTag(LoginTags.Alert).assertExists()
        onNodeWithText("E-mail ou senha incorretos.", substring = true).assertExists()
        onNodeWithText("Entre na sua conta e mantenha sua galera sempre alinhada.").assertDoesNotExist()
    }

    @Test fun `each field carries its own refusal`() = runComposeUiTest {
        content(state = refused())
        onNodeWithText("Digite um e-mail válido.").assertExists()
        onNodeWithText("A senha não confere.").assertExists()
    }

    // O campo com erro cresce exatamente a linha de mensagem que o export desenha: 54 → 74.
    // A altura absoluta é do `SaqzInput`, do design system; o que esta tela responde por é
    // o crescimento, e é ele que morre se alguém trocar `errorText` por um `Text` solto.
    @Test fun `the field grows by the export delta when it carries an error`() = runComposeUiTest {
        // Só a senha recusada: o e-mail limpo ao lado é a régua.
        content(state = LoginState(passwordError = UiText.Res(Res.string.login_error_password)))
        val clean = onNodeWithTag(LoginTags.Email).getUnclippedBoundsInRoot().height
        val wrong = onNodeWithTag(LoginTags.Password).getUnclippedBoundsInRoot().height
        assertEquals(FieldErrorDelta.value, (wrong - clean).value, absoluteTolerance = 0.5f)
    }

    @Test fun `the gap between the two fields is the one the export writes`() = runComposeUiTest {
        content()
        val email = onNodeWithTag(LoginTags.Email).getUnclippedBoundsInRoot()
        val password = onNodeWithTag(LoginTags.Password).getUnclippedBoundsInRoot()
        assertEquals(FieldGap.value, (password.top - email.bottom).value, absoluteTolerance = 0.5f)
    }

    // A amarra das duas medidas acima com a chave `fluxo1` do ui-contract.json, do mesmo
    // jeito que o `AccessMetricsTest` amarra o chrome.
    @Test fun `export field metrics are the contract`() = runTest {
        val fluxo1 = fluxo1()
        val campo = fluxo1.getValue("campo").jsonObject
        assertEquals(
            campo.getValue("alturaComErro").jsonPrimitive.float - campo.getValue("altura").jsonPrimitive.float,
            FieldErrorDelta.value,
        )
        assertEquals(
            fluxo1.getValue("gapDosCampos").jsonObject.getValue("padrao").jsonPrimitive.float,
            FieldGap.value,
        )
    }

    // O contador é cosmético e só existe quando há tentativa contada; o bloqueio real do
    // provedor zera o contador na ViewModel, e é assim que a frase some para dar lugar à
    // mensagem de conta bloqueada.
    @Test fun `the attempt counter only shows once an attempt was counted`() = runComposeUiTest {
        content(state = refused(attempts = 2))
        onNodeWithText("Errou 2 de 5 tentativas.", substring = true).assertExists()
    }

    @Test fun `no counter is drawn before the first counted attempt`() = runComposeUiTest {
        content(state = refused())
        onNodeWithTag(LoginTags.Attempts).assertDoesNotExist()
    }

    // Nada garante que o provedor barre na quinta — o limiar dele não é conhecido. Se ele
    // aceitar a sexta, a frase some em vez de escrever "Errou 6 de 5 tentativas.".
    @Test fun `the counter survives up to the announced limit and no further`() = runComposeUiTest {
        content(state = refused(attempts = LoginState.ANNOUNCED_ATTEMPT_LIMIT))
        onNodeWithTag(LoginTags.Attempts).assertExists()
    }

    @Test fun `past the announced limit the sentence disappears instead of lying`() = runComposeUiTest {
        content(state = refused(attempts = LoginState.ANNOUNCED_ATTEMPT_LIMIT + 1))
        onNodeWithTag(LoginTags.Attempts).assertDoesNotExist()
        onNodeWithText("de 5 tentativas", substring = true).assertDoesNotExist()
    }

    // Só a primeira frase sai em negrito; alerta de uma frase só fica sem destaque, senão
    // o `SaqzInlineAlert` engrossaria a mensagem inteira.
    @Test fun `only the leading sentence of an alert is emphasised`() {
        assertEquals(
            "E-mail ou senha incorretos.",
            alertEmphasis("E-mail ou senha incorretos. Confira os dados e tente de novo."),
        )
        assertNull(alertEmphasis("Verifique sua conexao e tente novamente"))
    }

    private suspend fun fluxo1(): JsonObject = Json.parseToJsonElement(
        Res.readBytes("files/ui-contract.json").decodeToString(),
    ).jsonObject.getValue("fluxo1").jsonObject

    private fun refused(attempts: Int = 0) = LoginState(
        email = "ana@exemplo",
        password = "12345678",
        error = UiText.Res(Res.string.login_error_credentials),
        emailError = UiText.Res(Res.string.login_error_email_invalid),
        passwordError = UiText.Res(Res.string.login_error_password),
        failedAttempts = attempts,
    )

    private fun androidx.compose.ui.test.ComposeUiTest.content(
        state: LoginState = LoginState(),
        onIntent: (LoginIntent) -> Unit = {},
        onCreateAccount: () -> Unit = {},
        onForgotPassword: () -> Unit = {},
    ) = setContent {
        SaqzTheme {
            LoginScreen(state, onIntent, onCreateAccount, onForgotPassword)
        }
    }
}
