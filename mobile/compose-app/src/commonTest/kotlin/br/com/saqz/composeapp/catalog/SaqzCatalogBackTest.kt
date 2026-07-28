package br.com.saqz.composeapp.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import br.com.saqz.composeapp.shell.SaqzAppShell
import br.com.saqz.composeapp.shell.SaqzShellCatalogTag
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O back do sistema fecha o catálogo — botão no Android, gesto no iOS. Sem isto o back
 * agiria no shell por baixo, ou sairia do app, com o catálogo ainda na tela.
 *
 * O teste monta o próprio [NavigationEventDispatcher] em vez de usar o que o harness
 * fornece, porque é a única forma de *disparar* o back daqui e de observar o que acontece
 * quando ninguém o intercepta: o `onBackCompletedFallback` é exatamente o "o app trata"
 * que o catálogo precisa roubar enquanto está aberto.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class SaqzCatalogBackTest {

    // Um input é como um evento de back entra no dispatcher; a superclasse guarda o
    // disparo em `protected`, então o teste precisa do próprio.
    private class TestBackInput : NavigationEventInput() {
        fun pressBack() = dispatchOnBackCompleted()
    }

    private class TestOwner(
        override val navigationEventDispatcher: NavigationEventDispatcher,
    ) : NavigationEventDispatcherOwner

    @Test
    fun theSystemBackClosesTheCatalogAndKeepsTheShell() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)

        setContent {
            CompositionLocalProvider(
                LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher),
            ) {
                SaqzTheme { SaqzAppShell(onLogout = {}, catalogEnabled = true) }
            }
        }
        onNodeWithTag(SaqzShellCatalogTag).performClick()
        waitForIdle()
        onNodeWithTag(SaqzCatalogTags.Root).assertIsDisplayed()

        input.pressBack()
        waitForIdle()

        onNodeWithText("Você está conectado.").assertIsDisplayed()
        onNodeWithTag(SaqzCatalogTags.Root).assertDoesNotExist()
        // O back foi consumido pelo catálogo, não repassado.
        assertEquals(0, unhandledBacks)
    }

    @Test
    fun theShellDoesNotInterceptBackWithTheCatalogClosed() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)

        setContent {
            CompositionLocalProvider(
                LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher),
            ) {
                SaqzTheme { SaqzAppShell(onLogout = {}, catalogEnabled = true) }
            }
        }
        input.pressBack()
        waitForIdle()

        // Ninguém interceptou: o back segue para quem estiver acima do shell.
        assertEquals(1, unhandledBacks)
        onNodeWithText("Você está conectado.").assertIsDisplayed()
    }
}
