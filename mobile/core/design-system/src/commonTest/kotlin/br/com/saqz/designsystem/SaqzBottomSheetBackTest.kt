package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O sheet trata o próprio back (VUL-53): aberto, ele fecha e a tela hospedeira fica;
 * fechado, ele não intercepta nada; e quando a hospedeira tem o handler dela, o do sheet
 * ganha por estar mais interno na composição.
 *
 * Os testes montam o próprio [NavigationEventDispatcher] em vez de usar o do harness,
 * porque é a única forma de *disparar* o back daqui e de observar o que acontece quando
 * ninguém intercepta — o `onBackCompletedFallback` é o "o app trata" que o sheet só pode
 * roubar enquanto está aberto.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
class SaqzBottomSheetBackTest {

    // Um input é como um evento de back entra no dispatcher; a superclasse guarda o
    // disparo em `protected`, então o teste precisa do próprio.
    private class TestBackInput : NavigationEventInput() {
        fun pressBack() = dispatchOnBackCompleted()
    }

    private class TestOwner(
        override val navigationEventDispatcher: NavigationEventDispatcher,
    ) : NavigationEventDispatcherOwner

    @Composable
    private fun WithDispatcher(dispatcher: NavigationEventDispatcher, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher),
        ) {
            SaqzTheme { Box(Modifier.fillMaxSize()) { content() } }
        }
    }

    @Test
    fun theSystemBackClosesTheOpenSheetAndKeepsTheHost() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)
        var closed = false

        setContent {
            WithDispatcher(dispatcher) {
                Text("Tela hospedeira")
                SaqzBottomSheet(open = true, onClose = { closed = true }, title = "Sair da conta?") {}
            }
        }
        onNodeWithText("Sair da conta?").assertIsDisplayed()

        input.pressBack()
        waitForIdle()

        assertTrue(closed)
        // Consumido pelo sheet: a tela por baixo não recebeu nada.
        assertEquals(0, unhandledBacks)
        onNodeWithText("Tela hospedeira").assertIsDisplayed()
    }

    @Test
    fun theClosedSheetDoesNotInterceptBack() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)
        var closed = false

        setContent {
            WithDispatcher(dispatcher) {
                SaqzBottomSheet(open = false, onClose = { closed = true }, title = "Sair da conta?") {}
            }
        }

        input.pressBack()
        waitForIdle()

        // Um sheet fechado que engolisse o back travaria a tela inteira.
        assertEquals(1, unhandledBacks)
        assertFalse(closed)
    }

    @Test
    fun theSheetTakesTheBackBeforeTheHostHandler() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)

        setContent {
            WithDispatcher(dispatcher) {
                var hostOpen by remember { mutableStateOf(true) }
                var sheetOpen by remember { mutableStateOf(true) }
                BackHandler(enabled = hostOpen) { hostOpen = false }
                if (hostOpen) {
                    Text("Tela hospedeira")
                    SaqzBottomSheet(
                        open = sheetOpen,
                        onClose = { sheetOpen = false },
                        title = "Sair da conta?",
                    ) {}
                }
            }
        }
        onNodeWithText("Sair da conta?").assertIsDisplayed()

        input.pressBack()
        waitForIdle()

        // Primeiro back: só o sheet. A hospedeira continua de pé.
        onNodeWithText("Sair da conta?").assertDoesNotExist()
        onNodeWithText("Tela hospedeira").assertIsDisplayed()

        input.pressBack()
        waitForIdle()

        // Segundo back: agora o handler da hospedeira, que voltou a ser o mais interno.
        onNodeWithText("Tela hospedeira").assertDoesNotExist()
        assertEquals(0, unhandledBacks)
    }
}
