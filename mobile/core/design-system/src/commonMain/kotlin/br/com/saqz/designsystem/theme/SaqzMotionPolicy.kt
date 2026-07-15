package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SaqzMotionPolicy(
    val pressScale: Float,
    val pressDurationMillis: Int,
    val focusDurationMillis: Int,
    val routeDurationMillis: Int,
    val maxTranslation: Dp,
    val opacityFeedbackDurationMillis: Int,
) {
    companion object {
        val Normal = SaqzMotionPolicy(
            pressScale = 0.95f,
            pressDurationMillis = 120,
            focusDurationMillis = 180,
            routeDurationMillis = 220,
            maxTranslation = 8.dp,
            opacityFeedbackDurationMillis = 120,
        )
        val Reduced = SaqzMotionPolicy(
            pressScale = 1.0f,
            pressDurationMillis = 120,
            focusDurationMillis = 180,
            routeDurationMillis = 220,
            maxTranslation = 0.dp,
            opacityFeedbackDurationMillis = 120,
        )
    }
}
