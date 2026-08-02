package br.com.saqz.composeapp.catalog

import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    // VUL-72: o shell abre na aba Grupos, e o placeholder que carrega "Sair" e a entrada do
    // catálogo passou a ser o conteúdo da aba Perfil.
    private fun ComposeUiTest.openProfileTab() {
        onNodeWithText("Perfil").performClick()
        waitForIdle()
    }

    @Test
    fun theSystemBackClosesTheCatalogAndKeepsTheShell() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)

        setContent {
            CompositionLocalProvider(
                LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher),
            ) {
                SaqzTheme {
                    SaqzAppShell(
                        catalogEnabled = true,
                        profileTab = { Text("Você está conectado.") },
                    )
                }
            }
        }
        openProfileTab()
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

    /**
     * O espécime de sheet fica *dentro* do catálogo, que por sua vez fica dentro do shell.
     * O `SaqzBottomSheet` trata o próprio back (VUL-53), então o catálogo não precisa saber
     * que o sheet existe: o handler mais interno habilitado consome.
     *
     * Uma camada por back: sheet primeiro, catálogo depois. Este teste é a prova de que a
     * pilha dos três handlers desempilha na ordem certa de verdade, e não só na unidade.
     */
    @Test
    fun theSystemBackClosesTheSheetSpecimenBeforeTheCatalog() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)

        setContent {
            CompositionLocalProvider(
                LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher),
            ) {
                SaqzTheme {
                    SaqzAppShell(
                        catalogEnabled = true,
                        profileTab = { Text("Você está conectado.") },
                    )
                }
            }
        }
        openProfileTab()
        onNodeWithTag(SaqzShellCatalogTag).performClick()
        waitForIdle()
        onNodeWithTag(SaqzCatalogTags.SheetTrigger).performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Sair da conta?").assertIsDisplayed()

        input.pressBack()
        waitForIdle()

        // Primeiro back: só o sheet foi embora.
        onNodeWithText("Sair da conta?").assertDoesNotExist()
        onNodeWithTag(SaqzCatalogTags.Root).assertIsDisplayed()

        input.pressBack()
        waitForIdle()

        // Segundo back: agora sim o catálogo.
        onNodeWithTag(SaqzCatalogTags.Root).assertDoesNotExist()
        onNodeWithText("Você está conectado.").assertIsDisplayed()
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
                SaqzTheme {
                    SaqzAppShell(
                        catalogEnabled = true,
                        profileTab = { Text("Você está conectado.") },
                    )
                }
            }
        }
        openProfileTab()
        input.pressBack()
        waitForIdle()

        // Ninguém interceptou: o back segue para quem estiver acima do shell.
        assertEquals(1, unhandledBacks)
        onNodeWithText("Você está conectado.").assertIsDisplayed()
    }
}
