package br.com.saqz.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzBadgeTest {
    private val tokens = SaqzColorTokens.Light

    @Test
    fun inventoryHasSixVariants() {
        assertEquals(6, SaqzBadgeVariant.entries.size)
        assertEquals(
            listOf("Neutral", "Accent", "Info", "Success", "Warning", "Error"),
            SaqzBadgeVariant.entries.map { it.name },
        )
    }

    @Test
    fun neutralUsesPairAndIsAA() = assertVariant(
        SaqzBadgeVariant.Neutral, tokens.surfaceSubtle, tokens.textPrimary, "Neutro",
    )

    @Test
    fun accentUsesOnAccentAndIsAA() = assertVariant(
        SaqzBadgeVariant.Accent, tokens.accent, tokens.onAccent, "Destaque",
    )

    @Test
    fun infoUsesPairAndIsAA() = assertVariant(
        SaqzBadgeVariant.Info, tokens.infoSurface, tokens.infoForeground, "Info",
    )

    @Test
    fun successUsesPairAndIsAA() = assertVariant(
        SaqzBadgeVariant.Success, tokens.successSurface, tokens.successForeground, "Sucesso",
    )

    @Test
    fun warningUsesPairAndIsAA() = assertVariant(
        SaqzBadgeVariant.Warning, tokens.warningSurface, tokens.warningForeground, "Alerta",
    )

    @Test
    fun errorUsesPairAndIsAA() = assertVariant(
        SaqzBadgeVariant.Error, tokens.errorSurface, tokens.errorForeground, "Erro",
    )

    private fun assertVariant(
        variant: SaqzBadgeVariant,
        expectedSurface: Color,
        expectedForeground: Color,
        label: String,
    ) {
        val pair = tokens.badgeColors(variant)
        assertEquals(expectedSurface, pair.surface, "$variant surface")
        assertEquals(expectedForeground, pair.foreground, "$variant foreground")
        // Semantic foreground on its surface must meet WCAG AA text contrast.
        assertTrue(contrast(pair.foreground, pair.surface) >= 4.5, "$variant AA")

        // The label (not colour alone) carries meaning, and a badge is never clickable.
        runComposeUiTest {
            setContent { SaqzTheme { SaqzBadge(label, variant, Modifier.testTag("badge")) } }
            onNodeWithText(label).assertExists()
            val onClick = onNodeWithTag("badge").fetchSemanticsNode().config
                .getOrElseNullable(SemanticsActions.OnClick) { null }
            assertNull(onClick, "$variant badge must not be clickable")
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
