package br.com.saqz.access.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Registry reduzido ao que o Login renderiza (VUL-36): o design system antigo foi
// apagado e o novo nasce componente a componente na primeira jornada vertical.
@Immutable
data class SaqzColorTokens(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val controlBorder: Color,
    val border: Color,
    val hairline: Color,
    val errorForeground: Color,
    val disabledSurface: Color,
    val disabledForeground: Color,
) {
    companion object {
        val Light = SaqzColorTokens(
            background = Color(0xFFF5F5F7),
            surface = Color(0xFFFFFFFF),
            primary = Color(0xFF0638DF),
            onPrimary = Color(0xFFFFFFFF),
            accent = Color(0xFFC7F300),
            textPrimary = Color(0xFF1D1D1F),
            textSecondary = Color(0xFF6E6E73),
            textMuted = Color(0xFF707075),
            controlBorder = Color(0xFF85858A),
            border = Color(0xFFD2D2D7),
            hairline = Color(0xFFE0E0E0),
            errorForeground = Color(0xFFB42318),
            disabledSurface = Color(0xFFE8E8ED),
            disabledForeground = Color(0xFF6E6E73),
        )
    }
}
