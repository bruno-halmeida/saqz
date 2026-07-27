package br.com.saqz.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SaqzMotionPolicyTest {
    private val motion = SaqzMotionPolicy.Normal

    // `emphasized` fica fora do mapa porque não é número — está coberto por
    // `emphasizedCurveIsTheExportCurve`.
    private val registry: Map<String, Float> = mapOf(
        "pressScale" to motion.pressScale,
        "pressOffset" to motion.pressOffset.value,
        "pressDurationMillis" to motion.pressDurationMillis.toFloat(),
        "opacityFeedbackDurationMillis" to motion.opacityFeedbackDurationMillis.toFloat(),
        "sheetDurationMillis" to motion.sheetDurationMillis.toFloat(),
        "thumbDurationMillis" to motion.thumbDurationMillis.toFloat(),
        "toastDwellMillis" to motion.toastDwellMillis.toFloat(),
    )

    private suspend fun contractMotion(): Map<String, Float> {
        val root = Json.parseToJsonElement(
            Res.readBytes("files/ui-contract.json").decodeToString(),
        ).jsonObject
        return root.getValue("motion").jsonObject
            .mapValues { it.value.jsonPrimitive.float }
    }

    @Test
    fun valuesMatchSpec() = runTest {
        val contract = contractMotion()
        for ((name, value) in registry) {
            assertEquals(contract.getValue(name), value, "motion $name")
        }
    }

    @Test
    fun inventoryIsClosed() = runTest {
        assertEquals(contractMotion().keys, registry.keys)
    }

    @Test
    fun emphasizedCurveIsTheExportCurve() {
        assertEquals(CubicBezierEasing(0.22f, 1f, 0.36f, 1f), motion.emphasized)
    }

    @Test
    fun reducedDropsSpatialMovement() {
        // Reduzir movimento zera escala e deslocamento; o toast some no mesmo tempo,
        // porque permanência de leitura não é animação.
        val reduced = SaqzMotionPolicy.Reduced
        assertEquals(1f, reduced.pressScale)
        assertEquals(0.dp, reduced.pressOffset)
        assertEquals(motion.toastDwellMillis, reduced.toastDwellMillis)
    }
}
