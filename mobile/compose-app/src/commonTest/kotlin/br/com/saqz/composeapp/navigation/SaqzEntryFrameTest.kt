package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** P3-4a: o rodapé fixo de uma tela empilhada não pode ficar sob a barra de navegação do Android. */
@OptIn(ExperimentalTestApi::class)
class SaqzEntryFrameTest {

    private val bar = WindowInsets(bottom = 48.dp)

    /** Distância entre o fim da tela e o fim do conteúdo. */
    private fun ComposeUiTest.folga() =
        onNodeWithTag("tela").getBoundsInRoot().bottom - onNodeWithTag("conteudo").getBoundsInRoot().bottom

    /** O conteúdo termina 48dp antes da borda: o botão do rodapé fica acima da barra. */
    @Test
    fun stackedScreenContentEndsAboveTheNavigationBar() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().testTag("tela")) {
                SaqzEntryFrame(background = Color.White, ownsNavigationInset = false, insets = bar) {
                    Box(Modifier.fillMaxSize().testTag("conteudo"))
                }
            }
        }
        assertEquals(48.dp, folga())
    }

    /** Shell e acesso (`ownsNavigationInset`) seguem até a borda: a barra de abas e a curva cuidam do deles. */
    @Test
    fun shellAndAccessKeepTheFullHeight() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().testTag("tela")) {
                SaqzEntryFrame(background = Color.White, ownsNavigationInset = true, insets = bar) {
                    Box(Modifier.fillMaxSize().testTag("conteudo"))
                }
            }
        }
        assertEquals(0.dp, folga())
    }

    /** Quem já aplica o inset (conversa, folha) não soma duas vezes: o quadro consome. */
    @Test
    fun screenThatAlreadyPadsDoesNotPadTwice() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().testTag("tela")) {
                SaqzEntryFrame(background = Color.White, ownsNavigationInset = false, insets = bar) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(bar).testTag("conteudo"))
                }
            }
        }
        assertEquals(48.dp, folga())
    }

    private val teclado = WindowInsets(bottom = 300.dp)

    /** P3-4c: com o teclado aberto, o conteúdo termina acima dele (o maior entre barra e teclado, sem somar). */
    @Test
    fun stackedScreenContentEndsAboveTheKeyboard() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().testTag("tela")) {
                SaqzEntryFrame(background = Color.White, ownsNavigationInset = false, insets = bar.union(teclado)) {
                    Box(Modifier.fillMaxSize().testTag("conteudo"))
                }
            }
        }
        assertEquals(300.dp, folga())
    }

    /** Tela que já aplica o teclado (editor de jogo, conversa) não soma: o quadro consome. */
    @Test
    fun screenThatAlreadyPadsForTheKeyboardDoesNotPadTwice() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().testTag("tela")) {
                SaqzEntryFrame(background = Color.White, ownsNavigationInset = false, insets = bar.union(teclado)) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(teclado).testTag("conteudo"))
                }
            }
        }
        assertEquals(300.dp, folga())
    }

    /** Shell e acesso seguem até a borda mesmo com o teclado: o acesso tem o `imePadding` dele. */
    @Test
    fun shellAndAccessIgnoreTheKeyboardInTheFrame() = runComposeUiTest {
        setContent {
            Box(Modifier.fillMaxSize().testTag("tela")) {
                SaqzEntryFrame(background = Color.White, ownsNavigationInset = true, insets = bar.union(teclado)) {
                    Box(Modifier.fillMaxSize().testTag("conteudo"))
                }
            }
        }
        assertEquals(0.dp, folga())
    }
}
