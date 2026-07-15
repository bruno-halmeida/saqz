package br.com.saqz.designsystem.theme

import androidx.compose.ui.graphics.Color
import br.com.saqz.designsystem.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaqzColorTokensTest {
    private val tokens = SaqzColorTokens.Light

    // camelCase token name -> registry Color, enumerated so a renamed/omitted
    // field breaks compilation and a count change is caught below.
    private val registry: Map<String, Color> = mapOf(
        "background" to tokens.background,
        "surface" to tokens.surface,
        "surfaceSubtle" to tokens.surfaceSubtle,
        "surfacePearl" to tokens.surfacePearl,
        "surfaceDark" to tokens.surfaceDark,
        "onDark" to tokens.onDark,
        "textMutedOnDark" to tokens.textMutedOnDark,
        "primary" to tokens.primary,
        "onPrimary" to tokens.onPrimary,
        "accent" to tokens.accent,
        "onAccent" to tokens.onAccent,
        "textPrimary" to tokens.textPrimary,
        "textSecondary" to tokens.textSecondary,
        "textMuted" to tokens.textMuted,
        "controlBorder" to tokens.controlBorder,
        "border" to tokens.border,
        "hairline" to tokens.hairline,
        "dividerSoft" to tokens.dividerSoft,
        "infoSurface" to tokens.infoSurface,
        "infoForeground" to tokens.infoForeground,
        "successSurface" to tokens.successSurface,
        "successForeground" to tokens.successForeground,
        "warningSurface" to tokens.warningSurface,
        "warningForeground" to tokens.warningForeground,
        "errorSurface" to tokens.errorSurface,
        "errorForeground" to tokens.errorForeground,
        "disabledSurface" to tokens.disabledSurface,
        "disabledForeground" to tokens.disabledForeground,
    )

    private suspend fun contractColors(): Map<String, String> {
        val root = Json.parseToJsonElement(
            Res.readBytes("files/ui-contract.json").decodeToString(),
        ).jsonObject
        return root.getValue("colors").jsonObject
            .mapValues { it.value.jsonPrimitive.content }
    }

    @Test
    fun inventoryHasExactly28Tokens() = runTest {
        val colors = contractColors()
        assertEquals(28, registry.size)
        assertEquals(28, colors.size)
        assertEquals(colors.keys, registry.keys)
    }

    @Test
    fun valuesMatchSpec() = runTest {
        val colors = contractColors()
        for ((name, color) in registry) {
            assertEquals(parseHex(colors.getValue(name)), color, "token $name")
        }
    }

    @Test
    fun mutedOnBackgroundIsAA() {
        assertAtLeast(4.5, contrast(tokens.textMuted, tokens.background))
    }

    @Test
    fun mutedOnSurfaceIsAA() {
        assertAtLeast(4.5, contrast(tokens.textMuted, tokens.surface))
    }

    @Test
    fun controlBorderOnBackgroundIsThreeToOne() {
        assertAtLeast(3.0, contrast(tokens.controlBorder, tokens.background))
    }

    @Test
    fun controlBorderOnSurfaceIsThreeToOne() {
        assertAtLeast(3.0, contrast(tokens.controlBorder, tokens.surface))
    }

    @Test
    fun accentUsesOnAccent() {
        assertEquals(Color(0xFF1D1D1F), tokens.onAccent)
        // on-accent must be legible foreground on accent (AA text contrast).
        assertAtLeast(4.5, contrast(tokens.accent, tokens.onAccent))
    }

    @Test
    fun decorativeLinesAreNotControlIndicators() {
        // Decorative lines fall below the 3:1 needed to identify a control,
        // while control-border meets it — so they cannot be the sole indicator.
        assertBelow(3.0, contrast(tokens.border, tokens.background))
        assertBelow(3.0, contrast(tokens.hairline, tokens.background))
        assertBelow(3.0, contrast(tokens.dividerSoft, tokens.background))
        assertAtLeast(3.0, contrast(tokens.controlBorder, tokens.background))
    }

    private fun assertAtLeast(minimum: Double, actual: Double) =
        assertTrue(actual >= minimum, "expected >= $minimum but was $actual")

    private fun assertBelow(ceiling: Double, actual: Double) =
        assertTrue(actual < ceiling, "expected < $ceiling but was $actual")

    private fun parseHex(hex: String): Color {
        val rgb = hex.removePrefix("#").toLong(16)
        return Color(0xFF000000L or rgb)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
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
