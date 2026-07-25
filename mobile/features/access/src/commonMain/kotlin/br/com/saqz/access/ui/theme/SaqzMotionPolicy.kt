package br.com.saqz.access.ui.theme

import androidx.compose.runtime.Immutable

// Só o feedback de pressão sobrevive: as durações de foco/rota e o deslocamento
// espacial serviam ao SaqzStateHost, apagado junto com o design system (VUL-36).
@Immutable
data class SaqzMotionPolicy(
    val pressScale: Float,
    val pressDurationMillis: Int,
    val opacityFeedbackDurationMillis: Int,
) {
    companion object {
        val Normal = SaqzMotionPolicy(
            pressScale = 0.95f,
            pressDurationMillis = 120,
            opacityFeedbackDurationMillis = 120,
        )
        val Reduced = SaqzMotionPolicy(
            pressScale = 1.0f,
            pressDurationMillis = 120,
            opacityFeedbackDurationMillis = 120,
        )
    }
}
