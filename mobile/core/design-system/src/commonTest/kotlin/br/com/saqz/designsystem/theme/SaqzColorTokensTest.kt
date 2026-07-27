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

    // camelCase token name -> registry Color, enumerado para que um campo renomeado
    // ou omitido quebre a compilação. `scrim` fica de fora: é o único token com
    // alfa, e o contrato guarda hex opaco.
    private val registry: Map<String, Color> = mapOf(
        "background" to tokens.background,
        "surface" to tokens.surface,
        "surfaceSoft" to tokens.surfaceSoft,
        "primary" to tokens.primary,
        "primaryPressed" to tokens.primaryPressed,
        "onPrimary" to tokens.onPrimary,
        "accent" to tokens.accent,
        "textPrimary" to tokens.textPrimary,
        "textSecondary" to tokens.textSecondary,
        "textPlaceholder" to tokens.textPlaceholder,
        "border" to tokens.border,
        "success" to tokens.success,
        "warning" to tokens.warning,
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
    fun inventoryIsClosed() = runTest {
        // Token novo no contrato sem par no registry (ou o contrário) falha aqui, e
        // não seis meses depois numa tela que copiou o hex à mão.
        assertEquals(contractColors().keys, registry.keys)
    }

    @Test
    fun bodyTextIsAA() {
        assertAtLeast(4.5, contrast(tokens.textPrimary, tokens.background))
        assertAtLeast(4.5, contrast(tokens.textPrimary, tokens.surface))
        assertAtLeast(4.5, contrast(tokens.textPrimary, tokens.surfaceSoft))
    }

    @Test
    fun secondaryTextIsAA() {
        assertAtLeast(4.5, contrast(tokens.textSecondary, tokens.background))
        assertAtLeast(4.5, contrast(tokens.textSecondary, tokens.surface))
    }

    @Test
    fun primaryFillCarriesWhiteText() {
        // O botão preenchido é o alvo mais comum do app: o rótulo tem de passar AA.
        assertAtLeast(4.5, contrast(tokens.onPrimary, tokens.primary))
        assertAtLeast(4.5, contrast(tokens.onPrimary, tokens.primaryPressed))
    }

    @Test
    fun focusRingIsThreeToOne() {
        // O anel de foco pinta em primary e precisa de 3:1 contra as duas superfícies
        // sobre as quais aparece.
        assertAtLeast(3.0, contrast(tokens.primary, tokens.background))
        assertAtLeast(3.0, contrast(tokens.primary, tokens.surface))
    }

    @Test
    fun accentIsNeverText() {
        // Lime é micro-acento. O teste registra que ele NÃO passa como texto, para
        // ninguém "corrigir" a paleta transformando lime em cor de rótulo.
        assertBelow(4.5, contrast(tokens.accent, tokens.surface))
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
