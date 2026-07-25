package br.com.saqz.access.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.access.resources.Res
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
        "sectionVerticalPadding" to metrics.sectionVerticalPadding,
        "primaryButtonRadius" to metrics.primaryButtonRadius,
        "compactControlRadius" to metrics.compactControlRadius,
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
    fun gridDerivedValuesAreMultiplesOfFour() {
        for ((name, dp) in registry) {
            assertTrue(dp.value % 4f == 0f, "metric $name is not a multiple of 4dp: ${dp.value}")
        }
    }

    @Test
    fun minimumTouchTargetIs48Dp() {
        assertEquals(48.dp, metrics.minimumTouchTarget)
    }
}
