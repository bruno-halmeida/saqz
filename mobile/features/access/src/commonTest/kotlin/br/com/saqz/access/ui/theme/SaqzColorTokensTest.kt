package br.com.saqz.access.ui.theme

import androidx.compose.ui.graphics.Color
import br.com.saqz.access.resources.Res
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

    // camelCase token name -> registry Color, enumerado para que um campo renomeado
    // ou omitido quebre a compilação. Sem asserção de inventário fechado: o registry
    // encolhe conforme o design system novo nasce (VUL-36).
    private val registry: Map<String, Color> = mapOf(
        "background" to tokens.background,
        "surface" to tokens.surface,
        "primary" to tokens.primary,
        "onPrimary" to tokens.onPrimary,
        "accent" to tokens.accent,
        "textPrimary" to tokens.textPrimary,
        "textSecondary" to tokens.textSecondary,
        "textMuted" to tokens.textMuted,
        "controlBorder" to tokens.controlBorder,
        "border" to tokens.border,
        "hairline" to tokens.hairline,
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
    fun decorativeLinesAreNotControlIndicators() {
        // Decorative lines fall below the 3:1 needed to identify a control,
        // while control-border meets it — so they cannot be the sole indicator.
        assertBelow(3.0, contrast(tokens.border, tokens.background))
        assertBelow(3.0, contrast(tokens.hairline, tokens.background))
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
