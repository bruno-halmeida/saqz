package br.com.saqz.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

// Uma curva para todo o design system (sheet, thumb do segmented, toast) e as
// durações que a acompanham. `Reduced` zera o deslocamento; o tempo que o toast
// fica na tela não é animação e por isso não encolhe.
@Immutable
data class SaqzMotionPolicy(
    val pressScale: Float,
    val pressDurationMillis: Int,
    val opacityFeedbackDurationMillis: Int,
    val emphasized: Easing,
    val sheetDurationMillis: Int,
    val thumbDurationMillis: Int,
    val toastDwellMillis: Int,
) {
    companion object {
        private val Emphasized = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

        val Normal = SaqzMotionPolicy(
            pressScale = 0.95f,
            pressDurationMillis = 120,
            opacityFeedbackDurationMillis = 120,
            emphasized = Emphasized,
            sheetDurationMillis = 320,
            thumbDurationMillis = 280,
            toastDwellMillis = 3_000,
        )
        val Reduced = SaqzMotionPolicy(
            pressScale = 1.0f,
            pressDurationMillis = 120,
            opacityFeedbackDurationMillis = 120,
            emphasized = Emphasized,
            sheetDurationMillis = 0,
            thumbDurationMillis = 0,
            toastDwellMillis = 3_000,
        )
    }
}
