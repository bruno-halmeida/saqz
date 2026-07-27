package br.com.saqz.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaqzMetricsTest {
    private val metrics = SaqzMetrics.Default

    private val registry: Map<String, Dp> = mapOf(
        "grid" to metrics.grid,
        "subGrid" to metrics.subGrid,
        "horizontalPadding" to metrics.horizontalPadding,
        "blockGap" to metrics.blockGap,
        "sectionGap" to metrics.sectionGap,
        "sectionVerticalPadding" to metrics.sectionVerticalPadding,
        "inputRadius" to metrics.inputRadius,
        "cardRadius" to metrics.cardRadius,
        "blockRadius" to metrics.blockRadius,
        "sheetRadius" to metrics.sheetRadius,
        "bottomNavHeight" to metrics.bottomNavHeight,
        "minimumTouchTarget" to metrics.minimumTouchTarget,
    )

    private suspend fun contractMetrics(): Map<String, Float> {
        val root = Json.parseToJsonElement(
            Res.readBytes("files/ui-contract.json").decodeToString(),
        ).jsonObject
        return root.getValue("metrics").jsonObject
            .mapValues { it.value.jsonPrimitive.float }
    }

    @Test
    fun valuesMatchSpec() = runTest {
        val contract = contractMetrics()
        for ((name, dp) in registry) {
            assertEquals(contract.getValue(name).dp, dp, "metric $name")
        }
    }

    @Test
    fun inventoryIsClosed() = runTest {
        assertEquals(contractMetrics().keys, registry.keys)
    }

    @Test
    fun spacingIsOnTheGridOfFour() {
        // Raio não é espaço: 10 (input) e 76 (nav) não são múltiplos de 4 e não devem
        // ser. A grade vale para o que separa conteúdo.
        val spacing = listOf("grid", "subGrid", "horizontalPadding", "blockGap", "sectionGap", "sectionVerticalPadding")
        spacing.forEach { name ->
            val dp = registry.getValue(name)
            assertTrue(dp.value % 4f == 0f, "metric $name is not a multiple of 4dp: ${dp.value}")
        }
    }

    @Test
    fun minimumTouchTargetIs48Dp() {
        assertEquals(48.dp, metrics.minimumTouchTarget)
    }
}
