package br.com.saqz.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Uma curva para todo o design system (sheet, thumb do segmented, toast) e as
// durações que a acompanham. `Reduced` zera o deslocamento; o tempo que o toast
// fica na tela não é animação e por isso não encolhe.
//
// Números do CSS dos componentes no _ds_bundle.js do export — design-tokens/README.md
// tem a linha de cada um. O press do export é `translateY(1px)` no botão somado ao
// `scale(.98)` do seletor de presença, e vale para todo alvo primário.
@Immutable
data class SaqzMotionPolicy(
    val pressScale: Float,
    val pressOffset: Dp,
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
            pressScale = 0.98f,
            pressOffset = 1.dp,
            pressDurationMillis = 120,
            opacityFeedbackDurationMillis = 120,
            emphasized = Emphasized,
            sheetDurationMillis = 320,
            thumbDurationMillis = 280,
            toastDwellMillis = 2_600,
        )
        val Reduced = SaqzMotionPolicy(
            pressScale = 1.0f,
            pressOffset = 0.dp,
            pressDurationMillis = 120,
            opacityFeedbackDurationMillis = 120,
            emphasized = Emphasized,
            sheetDurationMillis = 0,
            thumbDurationMillis = 0,
            toastDwellMillis = 2_600,
        )
    }
}
