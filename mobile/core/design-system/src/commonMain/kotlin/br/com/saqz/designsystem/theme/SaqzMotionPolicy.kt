package br.com.saqz.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A curva enfática cobre sheet, thumb do segmented e toast, e as durações que a
// acompanham. `Reduced` zera o deslocamento; o tempo que o toast fica na tela não é
// animação e por isso não encolhe.
//
// Números do CSS dos componentes no _ds_bundle.js do export — design-tokens/README.md
// tem a linha de cada um. O press do export é `translateY(1px)` no botão somado ao
// `scale(.98)` do seletor de presença, e vale para todo alvo primário.
//
// **O switch é a exceção e tem par próprio** (`switchDurationMillis`/`switchEasing`).
// No export ele não compartilha nada com o segmented: `.18s ease` contra
// `.28s cubic-bezier(.22,1,.36,1)`. Igualar os dois é regressão, não limpeza.
@Immutable
data class SaqzMotionPolicy(
    val pressScale: Float,
    val pressOffset: Dp,
    val pressDurationMillis: Int,
    val opacityFeedbackDurationMillis: Int,
    val emphasized: Easing,
    val sheetDurationMillis: Int,
    // Thumb do **segmented**, não o do switch. Anda com `emphasized`.
    val thumbDurationMillis: Int,
    // Trilho e knob do **switch**, os dois no mesmo tempo e na mesma curva.
    val switchDurationMillis: Int,
    val switchEasing: Easing,
    val toastDwellMillis: Int,
) {
    companion object {
        private val Emphasized = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

        // O `ease` do CSS é literalmente cubic-bezier(.25,.1,.25,1), então ele entra
        // exato em vez de aproximado. `FastOutSlowInEasing` seria (.4,0,.2,1) — a curva
        // padrão do Material, que não é a que o export pede aqui, e custaria o mesmo.
        private val Standard = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

        val Normal = SaqzMotionPolicy(
            pressScale = 0.98f,
            pressOffset = 1.dp,
            pressDurationMillis = 120,
            opacityFeedbackDurationMillis = 120,
            emphasized = Emphasized,
            sheetDurationMillis = 320,
            thumbDurationMillis = 280,
            switchDurationMillis = 180,
            switchEasing = Standard,
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
            switchDurationMillis = 0,
            switchEasing = Standard,
            toastDwellMillis = 2_600,
        )
    }
}
