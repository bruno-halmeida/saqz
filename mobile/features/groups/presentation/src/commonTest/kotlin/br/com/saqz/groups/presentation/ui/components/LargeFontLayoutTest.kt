package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** Fonte do sistema no máximo: o texto quebra por palavra e o que não cabe lado a lado empilha. */
@OptIn(ExperimentalTestApi::class)
class LargeFontLayoutTest {

    private val tags = AttendanceScoreBoardTags(going = "going", out = "out", waiting = "waiting", pending = "pending")

    @Composable
    private fun Phone(fontScale: Float, width: Int, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
            SaqzTheme { Box(Modifier.width(width.dp)) { content() } }
        }
    }

    @Test
    fun `cartao de jogo com fonte 2x desce o chip e deixa o texto com a largura toda`() = runComposeUiTest {
        setContent {
            Phone(fontScale = 2f, width = 360) {
                UpcomingGameRow(
                    day = "15",
                    month = "OUT",
                    title = "RV Vôlei B · 15h30",
                    meta = "Quinta · 2 confirmados",
                    contentDescription = "jogo",
                    onClick = {},
                    tag = "row",
                ) { Text("Sem resposta") }
            }
        }
        val meta = onNodeWithText("Quinta · 2 confirmados", useUnmergedTree = true).getBoundsInRoot()
        val chip = onNodeWithText("Sem resposta", useUnmergedTree = true).getBoundsInRoot()
        // Empilhado: o chip fica abaixo do texto, e o texto não é espremido numa coluna estreita.
        assertTrue(chip.top >= meta.bottom, "chip deveria ficar abaixo do texto: $chip / $meta")
        assertTrue(meta.width >= 150.dp, "texto espremido: ${meta.width}")
    }

    @Test
    fun `placar com fonte 2x fica em duas linhas`() = runComposeUiTest {
        setContent { Phone(fontScale = 2f, width = 360) { ScoreBoard() } }
        val going = onNodeWithTag("going", useUnmergedTree = true).getBoundsInRoot()
        val pending = onNodeWithTag("pending", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(pending.top >= going.bottom, "Sem resposta deveria ir para a segunda linha")
    }

    @Test
    fun `placar em tela estreita empilha mesmo com a fonte normal`() = runComposeUiTest {
        setContent { Phone(fontScale = 1f, width = 300) { ScoreBoard() } }
        val going = onNodeWithTag("going", useUnmergedTree = true).getBoundsInRoot()
        val pending = onNodeWithTag("pending", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(pending.top >= going.bottom, "largura decide, não só o fontScale")
    }

    @Test
    fun `placar em tela larga com fonte normal segue em uma linha`() = runComposeUiTest {
        setContent { Phone(fontScale = 1f, width = 402) { ScoreBoard() } }
        val going = onNodeWithTag("going", useUnmergedTree = true).getBoundsInRoot()
        val pending = onNodeWithTag("pending", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(pending.top < going.bottom, "quatro colunas lado a lado")
    }

    @Composable
    private fun ScoreBoard() = AttendanceScoreBoard(
        going = 0,
        out = 2,
        waiting = 0,
        pending = 5,
        onClick = {},
        tags = tags,
    )
}
