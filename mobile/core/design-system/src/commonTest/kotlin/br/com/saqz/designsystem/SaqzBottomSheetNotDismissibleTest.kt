package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A folha que não fecha (DEB-117): sem X e sem alça, o scrim engole o toque sem chamar `onClose`, e o
 * back é consumido sem efeito — nem a folha fecha nem a tela de baixo volta.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
class SaqzBottomSheetNotDismissibleTest {

    private class TestBackInput : NavigationEventInput() {
        fun pressBack() = dispatchOnBackCompleted()
    }

    private class TestOwner(
        override val navigationEventDispatcher: NavigationEventDispatcher,
    ) : NavigationEventDispatcherOwner

    @Composable
    private fun Sheet(onClose: () -> Unit) {
        Text("Tela hospedeira")
        SaqzBottomSheet(
            open = true,
            onClose = onClose,
            title = "Atualize o Saqz",
            description = "Para continuar usando, é só atualizar.",
            dismissible = false,
        )
    }

    @Test
    fun hasNoCloseButtonAndTheScrimDoesNotClose() = runComposeUiTest {
        var closed = false
        setContent { SaqzTheme { Box(Modifier.fillMaxSize()) { Sheet { closed = true } } } }

        onNodeWithContentDescriptionCount("Fechar", 0)
        // Canto de cima: só scrim ali.
        onRoot().performTouchInput { click(Offset(10f, 10f)) }
        waitForIdle()

        assertFalse(closed)
        onNodeWithText("Atualize o Saqz").assertIsDisplayed()
    }

    @Test
    fun theBackIsConsumedWithoutClosing() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)
        var closed = false
        setContent {
            CompositionLocalProvider(LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher)) {
                SaqzTheme { Box(Modifier.fillMaxSize()) { Sheet { closed = true } } }
            }
        }

        input.pressBack()
        waitForIdle()

        assertFalse(closed)
        assertEquals(0, unhandledBacks)
        onNodeWithText("Atualize o Saqz").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onNodeWithContentDescriptionCount(label: String, count: Int) {
        assertEquals(count, onAllNodes(androidx.compose.ui.test.hasContentDescription(label)).fetchSemanticsNodes().size)
    }
}
