package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A linha da espera do gestor carrega "Colocar no jogo" e "Tirar" lado a lado (DEB-017): em tela estreita e com
 * fonte grande os dois botões têm de aparecer inteiros e sem cobrir o nome.
 */
@OptIn(ExperimentalTestApi::class)
class GameWaitlistRowLayoutTest {
    @Test
    fun linhaDaEsperaEmTelaEstreitaComFonteNormal() = checkRow(fontScale = 1f)

    @Test
    fun linhaDaEsperaEmTelaEstreitaComFonte1ponto3() = checkRow(fontScale = 1.3f)

    @Test
    fun linhaDaEsperaEmTelaEstreitaComFonte2() = checkRow(fontScale = 2f)

    // "1º na fila" quebra na coluna de 44 dp; cada linha sai centralizada, não colada na esquerda.
    @Test
    fun posicaoNaFilaSaiCentralizada() = runComposeUiTest {
        setContent { SaqzTheme { Box(Modifier.width(320.dp)) { GameWaitlistSection(GameDetailPreviewData.admin, {}) } } }
        val results = mutableListOf<TextLayoutResult>()
        onAllNodes(hasText("na fila", substring = true), useUnmergedTree = true)[0].fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.single()
        assertTrue(layout.lineCount > 1, "a posição precisa quebrar para o teste valer: ${layout.lineCount} linha(s)")
        assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
        for (line in 0 until layout.lineCount) {
            val left = layout.getLineLeft(line)
            val right = layout.multiParagraph.width - layout.getLineRight(line)
            assertTrue(abs(left - right) <= 1f, "linha $line fora do centro: esquerda $left, direita $right")
        }
    }

    private fun checkRow(fontScale: Float) = runComposeUiTest {
        val screen = 320.dp
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                SaqzTheme {
                    Box(Modifier.width(screen)) { GameWaitlistSection(GameDetailPreviewData.admin, {}) }
                }
            }
        }
        val promote = onNodeWithTag(GameWaitlistTags.promote("wait-1")).getBoundsInRoot()
        val remove = onNodeWithTag(GameOrganizerTags.remove("wait-1")).getBoundsInRoot()
        val name = onNodeWithText("Caio Lima", useUnmergedTree = true).getBoundsInRoot()
        val promoteLabel = onNode(hasText("Colocar no jogo") and hasAnyAncestor(hasTestTag(GameWaitlistTags.promote("wait-1"))), useUnmergedTree = true).getBoundsInRoot()
        val removeLabel = onNode(hasText("Tirar") and hasAnyAncestor(hasTestTag(GameOrganizerTags.remove("wait-1"))), useUnmergedTree = true).getBoundsInRoot()
        val label = "fonte $fontScale"

        listOf(promote, remove).forEach {
            assertTrue(it.left >= 0.dp && it.right <= screen, "$label: botão fora da tela: $it")
        }
        assertTrue(promoteLabel.inside(promote), "$label: texto de 'Colocar no jogo' cortado: $promoteLabel / $promote")
        assertTrue(removeLabel.inside(remove), "$label: texto de 'Tirar' cortado: $removeLabel / $remove")
        assertTrue(!promote.overlaps(remove), "$label: os botões se sobrepõem: $promote / $remove")
        assertTrue(!name.overlaps(promote), "$label: 'Colocar no jogo' cobre o nome: $name / $promote")
        assertTrue(!name.overlaps(remove), "$label: 'Tirar' cobre o nome: $name / $remove")
    }

    private fun DpRect.overlaps(other: DpRect) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun DpRect.inside(outer: DpRect) =
        left >= outer.left && right <= outer.right && top >= outer.top && bottom <= outer.bottom
}
