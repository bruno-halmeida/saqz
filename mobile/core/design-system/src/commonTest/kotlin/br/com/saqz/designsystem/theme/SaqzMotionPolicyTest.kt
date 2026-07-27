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
import kotlin.test.assertNotEquals

class SaqzMotionPolicyTest {
    private val motion = SaqzMotionPolicy.Normal

    // As curvas ficam fora do mapa porque não são número — estão cobertas por
    // `emphasizedCurveIsTheExportCurve` e `switchRidesTheStandardCurve`.
    private val registry: Map<String, Float> = mapOf(
        "pressScale" to motion.pressScale,
        "pressOffset" to motion.pressOffset.value,
        "pressDurationMillis" to motion.pressDurationMillis.toFloat(),
        "opacityFeedbackDurationMillis" to motion.opacityFeedbackDurationMillis.toFloat(),
        "sheetDurationMillis" to motion.sheetDurationMillis.toFloat(),
        "thumbDurationMillis" to motion.thumbDurationMillis.toFloat(),
        "switchDurationMillis" to motion.switchDurationMillis.toFloat(),
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
    fun switchRidesTheStandardCurve() {
        // `ease` do CSS é cubic-bezier(.25,.1,.25,1) — não é a enfática nem a padrão
        // do Material (.4,0,.2,1).
        assertEquals(CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f), motion.switchEasing)
    }

    @Test
    fun switchAndSegmentedDoNotShareMovement() {
        // O export dá `.18s ease` ao switch e `.28s` enfático ao thumb do segmented.
        // Achatar os dois num token só foi o bug que este par de tokens desfez: se
        // alguém igualar de novo, quebra aqui.
        assertNotEquals(motion.thumbDurationMillis, motion.switchDurationMillis)
        assertNotEquals(motion.emphasized, motion.switchEasing)
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

    @Test
    fun reducedStopsBothThumbs() {
        // Os dois movimentos param, não só o do segmented.
        val reduced = SaqzMotionPolicy.Reduced
        assertEquals(0, reduced.thumbDurationMillis)
        assertEquals(0, reduced.switchDurationMillis)
    }
}
