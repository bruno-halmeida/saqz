package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rótulo que quebra de linha sai centralizado, não colado na esquerda da pílula. */
@OptIn(ExperimentalTestApi::class)
class SaqzLabelAlignmentTest {
    private val long = "Abrir o jogo que já existe"

    @Test
    fun buttonLabelThatWrapsIsCentered() = runComposeUiTest {
        setContent { SaqzTheme { SaqzButton(long, {}, Modifier.width(150.dp), size = SaqzButtonSize.Sm) } }
        assertWrappedAndCentered(long)
    }

    @Test
    fun buttonLabelWithLeadingIconIsCentered() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzButton(
                    label = long,
                    onClick = {},
                    modifier = Modifier.width(170.dp),
                    size = SaqzButtonSize.Sm,
                    leadingContent = { SaqzIcon(SaqzIcons.Plus, tint = it) },
                )
            }
        }
        assertWrappedAndCentered(long)
    }

    @Test
    fun loadingButtonLabelIsCentered() = runComposeUiTest {
        setContent { SaqzTheme { SaqzButton("Criando o grupo e chamando todo mundo…", {}, Modifier.width(190.dp), loading = true) } }
        assertWrappedAndCentered("Criando o grupo e chamando todo mundo…")
    }

    @Test
    fun singleLineLabelStaysCentered() = runComposeUiTest {
        setContent { SaqzTheme { SaqzButton("Entrar", {}, Modifier.width(240.dp)) } }
        val layout = layoutOf("Entrar")
        assertEquals(1, layout.lineCount)
        assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
        assertCentered(layout)
    }

    @Test
    fun statusChipLabelThatWrapsIsCentered() = runComposeUiTest {
        val text = "Administrador do grupo e do financeiro"
        setContent { SaqzTheme { Row(Modifier.width(160.dp)) { SaqzStatusChip(text = text) } } }
        assertWrappedAndCentered(text)
    }

    @Test
    fun stepperValueIsCentered() = runComposeUiTest {
        setContent { SaqzTheme { SaqzStepper(value = 12, onValueChange = {}, min = 1, max = 40) } }
        assertEquals(TextAlign.Center, layoutOf("12").layoutInput.style.textAlign)
    }

    @Test
    fun dashedAddButtonLabelThatWrapsIsCentered() = runComposeUiTest {
        setContent { SaqzTheme { SaqzDashedAddButton(long, {}, Modifier.width(150.dp)) } }
        assertWrappedAndCentered(long)
    }

    @Test
    fun textLinkWithTouchTargetThatWrapsIsCentered() = runComposeUiTest {
        setContent { SaqzTheme { SaqzTextLink("Trocar a quadra do grupo", {}, Modifier.width(110.dp), touchTarget = true) } }
        assertWrappedAndCentered("Trocar a quadra do grupo")
    }

    @Test
    fun shortcutCardLabelThatWrapsIsCentered() = runComposeUiTest {
        setContent { SaqzTheme { SaqzShortcutCard(SaqzIcons.Plus, long, "Atalho", {}, Modifier.width(100.dp)) } }
        assertWrappedAndCentered(long)
    }

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    private fun ComposeUiTest.assertWrappedAndCentered(text: String) {
        val layout = layoutOf(text)
        assertTrue(layout.lineCount > 1, "o teste precisa de um rótulo que quebre: ${layout.lineCount} linha(s)")
        assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
        assertCentered(layout)
    }

    // Folga igual dos dois lados de cada linha, dentro da largura do próprio texto.
    private fun assertCentered(layout: TextLayoutResult) {
        for (line in 0 until layout.lineCount) {
            val left = layout.getLineLeft(line)
            val right = layout.multiParagraph.width - layout.getLineRight(line)
            assertTrue(abs(left - right) <= 1f, "linha $line fora do centro: esquerda $left, direita $right")
        }
    }
}
