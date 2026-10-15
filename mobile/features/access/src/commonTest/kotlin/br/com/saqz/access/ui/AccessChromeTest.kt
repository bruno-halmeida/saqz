package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getBoundsInRoot
import kotlin.test.assertEquals
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test

/**
 * Os critérios do VUL-79 contra o bloco `fluxo1` do ui-contract.json: a onda de 130 que
 * estica na largura, os dois tamanhos de marca e a quebra fixa do título.
 */
@OptIn(ExperimentalTestApi::class)
class AccessChromeTest {
    private companion object {
        const val Probe = "probe"
    }

    // preserveAspectRatio="none": a largura acompanha o contêiner e a altura fica nos 130
    // do export. Onda proporcional numa tela larga subiria junto — é o defeito que este
    // caso tranca.
    @Test fun `wave stretches on width and keeps the export height`() = runComposeUiTest {
        scaffold(width = 800.dp)
        onNodeWithTag(AccessChromeTags.Wave).assertWidthIsEqualTo(800.dp)
        onNodeWithTag(AccessChromeTags.Wave).assertHeightIsEqualTo(130.dp)
    }

    @Test fun `wave keeps the export height on the narrow frame`() = runComposeUiTest {
        scaffold(width = 390.dp)
        onNodeWithTag(AccessChromeTags.Wave).assertWidthIsEqualTo(390.dp)
        onNodeWithTag(AccessChromeTags.Wave).assertHeightIsEqualTo(130.dp)
    }

    @Test fun `column pads 26 on the sides and 20 on top`() = runComposeUiTest {
        scaffold()
        onNodeWithTag(Probe).assertLeftPositionInRootIsEqualTo(26.dp)
        onNodeWithTag(Probe).assertTopPositionInRootIsEqualTo(20.dp)
    }

    // 1a e 1i: o único desvio do esqueleto é o topo de 36.
    @Test fun `spacious column pads 36 on top`() = runComposeUiTest {
        scaffold(spacious = true)
        onNodeWithTag(Probe).assertTopPositionInRootIsEqualTo(36.dp)
        onNodeWithTag(Probe).assertLeftPositionInRootIsEqualTo(26.dp)
    }

    @Test fun `large brand is the 86 square with lettering`() = runComposeUiTest {
        setContent { SaqzTheme { AccessBrandMark(large = true) } }
        onNodeWithTag(AccessChromeTags.Brand).assertWidthIsEqualTo(86.dp)
        onNodeWithTag(AccessChromeTags.Brand).assertHeightIsEqualTo(86.dp)
        onNodeWithTag(AccessChromeTags.Lettering).assertHeightIsEqualTo(30.dp)
    }

    @Test fun `small brand is the 68 square without lettering`() = runComposeUiTest {
        setContent { SaqzTheme { AccessBrandMark() } }
        onNodeWithTag(AccessChromeTags.Brand).assertWidthIsEqualTo(68.dp)
        onNodeWithTag(AccessChromeTags.Brand).assertHeightIsEqualTo(68.dp)
        onNodeWithTag(AccessChromeTags.Lettering).assertDoesNotExist()
    }

    // A quebra é conteúdo: "Organize seu grupo. / Jogue junto." quebra onde o desenho
    // quebra, não onde a largura obrigar.
    @Test fun `title keeps the designed break and joins the emphasis`() = runComposeUiTest {
        setContent {
            SaqzTheme { AccessHeader(title = "Organize seu grupo.\nJogue", emphasis = "junto.") }
        }
        onNodeWithTag(AccessChromeTags.Title).assertTextEquals("Organize seu grupo.\nJogue junto.")
    }

    // "Esqueceu a senha? / Sem stress." quebra antes do destaque, e aí o espaço de junção
    // viraria um recuo de um caractere na segunda linha centralizada.
    @Test fun `title broken before the emphasis takes no joining space`() = runComposeUiTest {
        setContent {
            SaqzTheme { AccessHeader(title = "Esqueceu a senha?\n", emphasis = "Sem stress.") }
        }
        onNodeWithTag(AccessChromeTags.Title).assertTextEquals("Esqueceu a senha?\nSem stress.")
    }

    @Test fun `subtitle renders when the screen has one`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                AccessHeader(
                    title = "Digite o",
                    emphasis = "código.",
                    subtitle = "Enviamos um código de 4 dígitos para ana@exemplo.com.",
                )
            }
        }
        onNodeWithTag(AccessChromeTags.Subtitle)
            .assertTextEquals("Enviamos um código de 4 dígitos para ana@exemplo.com.")
    }

    // 1i e 1j trocam o subtítulo pelo alerta: sem subtítulo, nada ocupa o espaço dele.
    @Test fun `subtitle is absent on the error screens`() = runComposeUiTest {
        setContent { SaqzTheme { AccessHeader(title = "Digite o", emphasis = "código.") } }
        onNodeWithTag(AccessChromeTags.Subtitle).assertDoesNotExist()
    }

    // P3-4c: teclado numérico do iPhone não tem tecla de confirmar; tocar no vazio fecha (tira o foco).
    @Test fun `tapping the empty area clears the focus of a field`() = runComposeUiTest {
        scaffold(content = {
            BasicTextField("", {}, Modifier.fillMaxWidth().height(40.dp).testTag("campo"))
            Box(Modifier.fillMaxWidth().height(200.dp).testTag("vazio"))
        })
        onNodeWithTag("campo").requestFocus()
        onNodeWithTag("campo").assertIsFocused()
        onNodeWithTag("vazio").performClick()
        onNodeWithTag("campo").assertIsNotFocused()
    }

    // Tocar no próprio campo não pode fechar o teclado que acabou de abrir.
    @Test fun `tapping the field itself keeps the focus`() = runComposeUiTest {
        scaffold(content = {
            BasicTextField("", {}, Modifier.fillMaxWidth().height(40.dp).testTag("campo"))
        })
        onNodeWithTag("campo").performClick()
        onNodeWithTag("campo").assertIsFocused()
    }

    // P3-4c: com o teclado aberto, o botão do fim do formulário fica logo acima dele (a tela rola até lá
    // e a onda sai), mesmo quando o formulário é mais alto que a área que sobra.
    @Test fun `keyboard open scrolls the last item right above it and hides the wave`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                Box(Modifier.size(390.dp, 844.dp)) {
                    AccessScaffold(keyboard = WindowInsets(bottom = 300.dp)) {
                        Box(Modifier.fillMaxWidth().height(700.dp))
                        Box(Modifier.fillMaxWidth().height(52.dp).testTag("botao"))
                    }
                }
            }
        }
        waitForIdle()
        val bottom = onNodeWithTag("botao").getBoundsInRoot().bottom
        assertEquals(onRoot().getBoundsInRoot().bottom - 300.dp, bottom)
        onAllNodesWithTag(AccessChromeTags.Wave).assertCountEquals(0)
    }

    private fun ComposeUiTest.scaffold(
        width: Dp = 390.dp,
        spacious: Boolean = false,
        content: @Composable ColumnScope.() -> Unit = {
            Box(Modifier.fillMaxWidth().height(10.dp).testTag(Probe))
        },
    ) = setContent {
        SaqzTheme {
            Box(Modifier.size(width, 844.dp)) {
                AccessScaffold(spacious = spacious, content = content)
            }
        }
    }
}
