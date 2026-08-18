package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import br.com.saqz.access.presentation.register.RegisterEmailError
import br.com.saqz.access.presentation.register.RegisterIntent
import br.com.saqz.access.presentation.register.RegisterInviteContext
import br.com.saqz.access.presentation.register.RegisterPasswordError
import br.com.saqz.access.presentation.register.RegisterState
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
class RegisterScreenTest {

    // Um input é como o back entra no dispatcher; a superclasse guarda o disparo em
    // `protected`, então o teste precisa do próprio. Igual ao `SaqzBottomSheetBackTest`.
    private class TestBackInput : NavigationEventInput() {
        fun pressBack() = dispatchOnBackCompleted()
    }

    private class TestOwner(
        override val navigationEventDispatcher: NavigationEventDispatcher,
    ) : NavigationEventDispatcherOwner

    // Quatro campos, na ordem do export, cada um reportando o seu próprio intent. A tela é
    // sem estado, então o campo volta a vazio depois de cada tecla — o que interessa é o
    // primeiro intent, e é ele que diz se um campo foi ligado ao intent do vizinho.
    @Test fun `the four fields report their own value`() = runComposeUiTest {
        val intents = mutableListOf<RegisterIntent>()
        content(onIntent = intents::add)
        val fields = onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        assertEquals(4, fields.fetchSemanticsNodes().size)
        fields[0].performTextInput("A")
        fields[1].performTextInput("B")
        fields[2].performTextInput("1")
        fields[3].performTextInput("C")
        assertEquals(
            listOf<RegisterIntent>(
                RegisterIntent.UpdateName("A"),
                RegisterIntent.UpdateEmail("B"),
                RegisterIntent.UpdatePhone("1"),
                RegisterIntent.UpdatePassword("C"),
            ),
            intents.filter { it.typedValue != "" },
        )
    }

    private val RegisterIntent.typedValue: String?
        get() = when (this) {
            is RegisterIntent.UpdateName -> value
            is RegisterIntent.UpdateEmail -> value
            is RegisterIntent.UpdatePhone -> value
            is RegisterIntent.UpdatePassword -> value
            else -> null
        }

    @Test fun `the primary action submits`() = runComposeUiTest {
        var intent: RegisterIntent? = null
        content(onIntent = { intent = it })
        onNodeWithTag(RegisterTags.Submit).performScrollTo().performClick()
        assertEquals(RegisterIntent.Submit, intent)
    }

    @Test fun `done on the password field submits`() = runComposeUiTest {
        var intent: RegisterIntent? = null
        content(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[3].performImeAction()
        assertEquals(RegisterIntent.Submit, intent)
    }

    // 1b: subtítulo presente, helper da senha presente, nenhum alerta.
    @Test fun `the quiet screen shows the subtitle and the password hint`() = runComposeUiTest {
        content()
        onNodeWithText("Crie sua conta em menos de um minuto.").assertExists()
        onNodeWithTag(RegisterTags.PasswordHint).assertExists()
        onNodeWithTag(RegisterTags.Alert).assertDoesNotExist()
    }

    @Test fun `invite registration shows preview context and group follow-up`() = runComposeUiTest {
        content(
            inviteContext = RegisterInviteContext.preview(
                groupName = "Vôlei do CERET",
                inviterName = "Ana",
                entryRequiresApproval = true,
            ),
        )

        onNodeWithText("Entrando no Vôlei do CERET").assertExists()
        onNodeWithText("Convite de Ana · precisa de aprovação").assertExists()
        onNodeWithText("Depois disso você escolhe apelido, posição e nível no grupo.").assertExists()
        onNodeWithText("Crie sua conta em menos de um minuto.").assertDoesNotExist()
    }

    @Test fun `invite registration falls back to generic header without preview`() = runComposeUiTest {
        content(inviteContext = RegisterInviteContext.Generic)

        onNodeWithText("Você foi convidado para um grupo").assertExists()
        onNodeWithText("Depois disso você escolhe apelido, posição e nível no grupo.").assertExists()
    }

    // 1j: o alerta ocupa o lugar do subtítulo, e a contagem é a dos campos acesos — não o
    // "3" literal do mockup, que desenha quatro campos errados.
    @Test fun `the refused screen swaps the subtitle for a counted alert`() = runComposeUiTest {
        content(state = refused())
        onNodeWithText("Crie sua conta em menos de um minuto.").assertDoesNotExist()
        onNodeWithText("Revise 4 campos para criar sua conta.").assertExists()
        onNodeWithTag(RegisterTags.PasswordHint).assertDoesNotExist()
    }

    @Test fun `the alert counts only what is lit`() = runComposeUiTest {
        content(state = RegisterState(invalidPhone = true))
        onNodeWithText("Revise 1 campos para criar sua conta.").assertExists()
    }

    // A pergunta do e-mail duplicado tem de ter resposta: a linha inteira leva à 1a.
    @Test fun `the taken email message answers its own question`() = runComposeUiTest {
        var intent: RegisterIntent? = null
        content(state = RegisterState(emailError = RegisterEmailError.Taken), onIntent = { intent = it })
        onNodeWithTag(RegisterTags.EmailTaken).assertHasClickAction().performScrollTo().performClick()
        assertEquals(RegisterIntent.SignInWithTakenEmail, intent)
    }

    // A outra recusa do mesmo campo é como as das outras três: mensagem no slot, sem
    // pergunta e sem clique — não há para onde levar quem digitou o e-mail errado.
    @Test fun `the malformed email message is not an offer to sign in`() = runComposeUiTest {
        content(state = RegisterState(emailError = RegisterEmailError.Invalid))
        onNodeWithText("Digite um e-mail válido.").assertExists()
        onNodeWithTag(RegisterTags.EmailTaken).assertDoesNotExist()
    }

    @Test fun `the footer link goes to the login without carrying anything`() = runComposeUiTest {
        var signedIn = false
        var intent: RegisterIntent? = null
        content(onIntent = { intent = it }, onSignIn = { signedIn = true })
        onNodeWithTag(RegisterTags.SignIn).performScrollTo().performClick()
        assertEquals(true, signedIn)
        assertNull(intent)
    }

    @Test fun `the back button pops`() = runComposeUiTest {
        var back = false
        content(onBack = { back = true })
        onNodeWithTag(RegisterTags.Back).performClick()
        assertEquals(true, back)
    }

    /**
     * As saídas fecham junto com os campos enquanto o cadastro está em curso. O
     * `createAccount` não tem cancelamento: sair no meio deixaria a resposta chegando a uma
     * tela que já saiu, criando conta e trocando sessão pelas costas de quem desistiu.
     */
    @Test fun `no exit is live while the account is being created`() = runComposeUiTest {
        var left = false
        var intent: RegisterIntent? = null
        content(state = RegisterState(isLoading = true), onIntent = { intent = it }, onBack = { left = true }, onSignIn = { left = true })

        onNodeWithTag(RegisterTags.Back).performClick()
        onNodeWithTag(RegisterTags.SignIn).performScrollTo().performClick()
        onNodeWithTag(RegisterTags.Submit).performScrollTo().performClick()

        assertEquals(false, left, "a 1b não tem saída enquanto envia")
        assertNull(intent, "nem o enviar responde durante o envio")
    }

    /**
     * A quarta saída, a que não se vê. O `NavDisplay.onBack` do host é um `pop`
     * incondicional e o login está embaixo, então sem esta trava o back do sistema removeria
     * a 1b no meio do `createAccount`.
     *
     * O teste monta o próprio `NavigationEventDispatcher` porque é a única forma de disparar
     * o back daqui e de ver quem **não** o consumiu — o `onBackCompletedFallback` é o "o
     * host trata", que é exatamente o que a 1b não pode deixar acontecer enquanto envia.
     * Mesmo arranjo do `SaqzBottomSheetBackTest`.
     */
    @Test fun `the system back is swallowed while the account is being created`() = runComposeUiTest {
        var reachedTheHost = 0
        val dispatcher = NavigationEventDispatcher { reachedTheHost++ }
        val input = TestBackInput().also(dispatcher::addInput)
        content(state = RegisterState(isLoading = true), dispatcher = dispatcher)

        input.pressBack()
        waitForIdle()

        assertEquals(0, reachedTheHost, "o back saiu da 1b e chegaria ao pop do host")
    }

    // E em repouso ela não pode roubar o back: a 1b engolindo sempre travaria o voltar.
    @Test fun `the quiet screen leaves the system back to the host`() = runComposeUiTest {
        var reachedTheHost = 0
        val dispatcher = NavigationEventDispatcher { reachedTheHost++ }
        val input = TestBackInput().also(dispatcher::addInput)
        content(dispatcher = dispatcher)

        input.pressBack()
        waitForIdle()

        assertEquals(1, reachedTheHost)
    }

    // O negrito do alerta sai da própria frase formatada, e não de um literal em PT-BR.
    @Test fun `the summary emphasis stops after the counted noun`() {
        assertEquals(
            "Revise 12 campos",
            registerSummaryEmphasis("Revise 12 campos para criar sua conta.", 12),
        )
        assertNull(registerSummaryEmphasis("frase sem numero nenhum", 3))
    }

    private fun refused() = RegisterState(
        email = "rafa@galera.com",
        phone = "(11) 9999",
        password = "12345",
        invalidName = true,
        emailError = RegisterEmailError.Taken,
        invalidPhone = true,
        passwordError = RegisterPasswordError.TooShort,
    )

    private fun ComposeUiTest.content(
        state: RegisterState = RegisterState(),
        onIntent: (RegisterIntent) -> Unit = {},
        onBack: () -> Unit = {},
        onSignIn: () -> Unit = {},
        dispatcher: NavigationEventDispatcher? = null,
        inviteContext: RegisterInviteContext? = null,
    ) = setContent {
        WithDispatcher(dispatcher) {
            SaqzTheme {
                RegisterScreen(
                    state = state,
                    onIntent = onIntent,
                    onBack = onBack,
                    onSignIn = onSignIn,
                    inviteContext = inviteContext,
                )
            }
        }
    }

    @Composable
    private fun WithDispatcher(dispatcher: NavigationEventDispatcher?, content: @Composable () -> Unit) {
        if (dispatcher == null) {
            content()
        } else {
            CompositionLocalProvider(
                LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher),
                content = content,
            )
        }
    }
}
