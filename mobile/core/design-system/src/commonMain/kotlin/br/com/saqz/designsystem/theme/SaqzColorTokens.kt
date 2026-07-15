package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class SaqzColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val surfacePearl: Color,
    val surfaceDark: Color,
    val onDark: Color,
    val textMutedOnDark: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val controlBorder: Color,
    val border: Color,
    val hairline: Color,
    val dividerSoft: Color,
    val infoSurface: Color,
    val infoForeground: Color,
    val successSurface: Color,
    val successForeground: Color,
    val warningSurface: Color,
    val warningForeground: Color,
    val errorSurface: Color,
    val errorForeground: Color,
    val disabledSurface: Color,
    val disabledForeground: Color,
) {
    companion object {
        val Light = SaqzColorTokens(
            background = Color(0xFFF5F5F7),
            surface = Color(0xFFFFFFFF),
            surfaceSubtle = Color(0xFFF4F8FB),
            surfacePearl = Color(0xFFFAFAFC),
            surfaceDark = Color(0xFF071025),
            onDark = Color(0xFFFFFFFF),
            textMutedOnDark = Color(0xFFCCCCCC),
            primary = Color(0xFF0638DF),
            onPrimary = Color(0xFFFFFFFF),
            accent = Color(0xFFC7F300),
            onAccent = Color(0xFF1D1D1F),
            textPrimary = Color(0xFF1D1D1F),
            textSecondary = Color(0xFF6E6E73),
            textMuted = Color(0xFF707075),
            controlBorder = Color(0xFF85858A),
            border = Color(0xFFD2D2D7),
            hairline = Color(0xFFE0E0E0),
            dividerSoft = Color(0xFFF0F0F0),
            infoSurface = Color(0xFFEAF0FF),
            infoForeground = Color(0xFF0638DF),
            successSurface = Color(0xFFE8F5EC),
            successForeground = Color(0xFF1D6B35),
            warningSurface = Color(0xFFFFF4D6),
            warningForeground = Color(0xFF7A4B00),
            errorSurface = Color(0xFFFDEBEC),
            errorForeground = Color(0xFFB42318),
            disabledSurface = Color(0xFFE8E8ED),
            disabledForeground = Color(0xFF6E6E73),
        )
    }
}
