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
        "heroDisplay" to typography.heroDisplay,
        "displayLarge" to typography.displayLarge,
        "displayMedium" to typography.displayMedium,
        "lead" to typography.lead,
        "body" to typography.body,
        "bodyStrong" to typography.bodyStrong,
        "caption" to typography.caption,
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
        assertEquals(spec.lineHeight.em, style.lineHeight, "$name lineHeight")
        assertEquals(spec.tracking.em, style.letterSpacing, "$name tracking")
    }

    @Test
    fun inventoryHasExactlyEightStyles() = runTest {
        val spec = contractTypography()
        assertEquals(8, registry.size)
        assertEquals(8, spec.size)
        assertEquals(spec.keys, registry.keys)
    }

    @Test
    fun heroDisplay() = runTest { assertStyleMatches("heroDisplay") }

    @Test
    fun displayLarge() = runTest { assertStyleMatches("displayLarge") }

    @Test
    fun displayMedium() = runTest { assertStyleMatches("displayMedium") }

    @Test
    fun lead() = runTest { assertStyleMatches("lead") }

    @Test
    fun body() = runTest { assertStyleMatches("body") }

    @Test
    fun bodyStrong() = runTest { assertStyleMatches("bodyStrong") }

    @Test
    fun caption() = runTest { assertStyleMatches("caption") }

    @Test
    fun navigation() = runTest { assertStyleMatches("navigation") }
}
