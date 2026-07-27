package br.com.saqz.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SaqzTypographyTest {
    private val typography = SaqzTypography.Default

    private val registry: Map<String, TextStyle> = mapOf(
        "headline" to typography.headline,
        "title" to typography.title,
        "subtitle" to typography.subtitle,
        "body" to typography.body,
        "support" to typography.support,
        "label" to typography.label,
        "caption" to typography.caption,
        "eyebrow" to typography.eyebrow,
        "navigation" to typography.navigation,
    )

    private data class StyleSpec(
        val size: Float,
        val weight: Int,
        val lineHeight: Float,
        val tracking: Float,
    )

    private suspend fun contractTypography(): Map<String, StyleSpec> {
        val root = Json.parseToJsonElement(
            Res.readBytes("files/ui-contract.json").decodeToString(),
        ).jsonObject
        return root.getValue("typography").jsonObject.mapValues { (_, element) ->
            val style = element.jsonObject
            StyleSpec(
                size = style.getValue("size").jsonPrimitive.float,
                weight = style.getValue("weight").jsonPrimitive.int,
                lineHeight = style.getValue("lineHeight").jsonPrimitive.float,
                tracking = style.getValue("tracking").jsonPrimitive.float,
            )
        }
    }

    private suspend fun assertStyleMatches(name: String) {
        val spec = contractTypography().getValue(name)
        val style = registry.getValue(name)
        assertEquals(spec.size.sp, style.fontSize, "$name size")
        assertEquals(FontWeight(spec.weight), style.fontWeight, "$name weight")
        assertEquals(spec.lineHeight.sp, style.lineHeight, "$name lineHeight")
        assertEquals(spec.tracking.em, style.letterSpacing, "$name tracking")
    }

    @Test
    fun headline() = runTest { assertStyleMatches("headline") }

    @Test
    fun title() = runTest { assertStyleMatches("title") }

    @Test
    fun subtitle() = runTest { assertStyleMatches("subtitle") }

    @Test
    fun body() = runTest { assertStyleMatches("body") }

    @Test
    fun support() = runTest { assertStyleMatches("support") }

    @Test
    fun label() = runTest { assertStyleMatches("label") }

    @Test
    fun caption() = runTest { assertStyleMatches("caption") }

    @Test
    fun eyebrow() = runTest { assertStyleMatches("eyebrow") }

    @Test
    fun navigation() = runTest { assertStyleMatches("navigation") }

    @Test
    fun inventoryIsClosed() = runTest {
        assertEquals(contractTypography().keys, registry.keys)
    }

    @Test
    fun onlyEyebrowTracksPositive() {
        // Caixa alta é exclusividade do eyebrow, e é o único lugar em que o tracking
        // abre em vez de fechar.
        registry.forEach { (name, style) ->
            if (name != "eyebrow") {
                val tracking = style.letterSpacing.value
                assertEquals(true, tracking <= 0f, "$name deveria ter tracking <= 0, tem $tracking")
            }
        }
    }
}
