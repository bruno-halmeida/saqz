package br.com.saqz.designsystem.theme

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

class SaqzMotionPolicyTest {
    private val normal = SaqzMotionPolicy.Normal
    private val reduced = SaqzMotionPolicy.Reduced

    private fun policyMap(policy: SaqzMotionPolicy): Map<String, Float> = mapOf(
        "pressScale" to policy.pressScale,
        "pressDurationMillis" to policy.pressDurationMillis.toFloat(),
        "focusDurationMillis" to policy.focusDurationMillis.toFloat(),
        "routeDurationMillis" to policy.routeDurationMillis.toFloat(),
        "maxTranslation" to policy.maxTranslation.value,
        "opacityFeedbackDurationMillis" to policy.opacityFeedbackDurationMillis.toFloat(),
    )

    private suspend fun contractMotion(variant: String): Map<String, Float> {
        val root = Json.parseToJsonElement(
            Res.readBytes("files/ui-contract.json").decodeToString(),
        ).jsonObject
        return root.getValue("motion").jsonObject.getValue(variant).jsonObject
            .mapValues { it.value.jsonPrimitive.float }
    }

    @Test
    fun normalValuesMatchContract() = runTest {
        assertEquals(contractMotion("normal"), policyMap(normal))
    }

    @Test
    fun pressFallsInsideSpecRange() {
        assertEquals(0.95f, normal.pressScale)
        assertTrue(normal.pressDurationMillis in 100..160, "press ${normal.pressDurationMillis}")
    }

    @Test
    fun focusFallsInsideSpecRange() {
        assertTrue(normal.focusDurationMillis in 150..200, "focus ${normal.focusDurationMillis}")
    }

    @Test
    fun routeFallsInsideSpecRange() {
        assertTrue(normal.routeDurationMillis in 180..250, "route ${normal.routeDurationMillis}")
        assertTrue(normal.maxTranslation.value <= 8f, "translate ${normal.maxTranslation}")
    }

    @Test
    fun reducedHasNoSpatialMotion() {
        assertEquals(1.0f, reduced.pressScale)
        assertEquals(0.dp, reduced.maxTranslation)
    }

    @Test
    fun reducedKeepsOpacityFeedback() = runTest {
        // Full reduced contract is mirrored by the registry, and feedback survives.
        assertEquals(contractMotion("reduced"), policyMap(reduced))
        assertEquals(120, reduced.opacityFeedbackDurationMillis)
    }
}
