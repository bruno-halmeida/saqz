package br.com.saqz.groups.presentation.ui.components

import br.com.saqz.designsystem.SaqzDashedAddButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Controles próprios da jornada de grupos: o rótulo que quebra de linha sai centralizado. */
@OptIn(ExperimentalTestApi::class)
class ControlLabelAlignmentTest {
    @Test
    fun addSlotButtonLabelIsCentered() = runComposeUiTest {
        setContent { Phone(width = 150) { SaqzDashedAddButton("Adicionar dia e horário", {}) } }
        assertCentered(onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)[0].layout())
    }

    @Test
    fun venueActionLabelIsCentered() = runComposeUiTest {
        setContent { Phone(width = 260) { GroupVenueRow("CERET", "R. Canuto Abreu", "Trocar a quadra do grupo", {}) } }
        assertCentered(onNodeWithText("Trocar a quadra do grupo", useUnmergedTree = true).layout())
    }

    @Test
    fun scoreBoardLabelsAreCentered() = runComposeUiTest {
        setContent {
            Phone(width = 300, fontScale = 2f) {
                AttendanceScoreBoard(
                    going = 3,
                    out = 2,
                    waiting = 1,
                    pending = 5,
                    onClick = {},
                    tags = AttendanceScoreBoardTags(going = "going", out = "out", waiting = "waiting", pending = "pending"),
                )
            }
        }
        assertCentered(onNodeWithText("Sem resposta", useUnmergedTree = true).layout())
    }

    @Composable
    private fun Phone(width: Int, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
            SaqzTheme { Box(Modifier.width(width.dp)) { content() } }
        }
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    private fun assertCentered(layout: TextLayoutResult) {
        assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
        for (line in 0 until layout.lineCount) {
            val left = layout.getLineLeft(line)
            val right = layout.multiParagraph.width - layout.getLineRight(line)
            assertTrue(abs(left - right) <= 1f, "linha $line fora do centro: esquerda $left, direita $right")
        }
    }
}
