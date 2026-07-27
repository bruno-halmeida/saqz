package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Paleta do fluxo 10a, conferida hex a hex contra design-tokens/colors.css do export
// oficial. Uma única cor de linha (`border`) atende card, input e divisória — o design
// system não mantém três cinzas para o mesmo traço de 1px.
//
// `scrim` e `chrome` são os dois tokens com alfa, e por isso ficam fora do
// contrato em ui-contract.json, que guarda hex opaco.
//
// ponytail: os fills translúcidos do colors.css (--brand-fill-08/11, --lime-fill-32,
// --error-fill-10) ficam fora até um componente precisar deles; hoje ninguém pinta
// anel de foco ou chip com alfa.
@Immutable
data class SaqzColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val primary: Color,
    val primaryPressed: Color,
    val onPrimary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textPlaceholder: Color,
    val border: Color,
    val success: Color,
    val warning: Color,
    val errorForeground: Color,
    val disabledSurface: Color,
    val disabledForeground: Color,
    val scrim: Color,
    val chrome: Color,
) {
    companion object {
        val Light = SaqzColorTokens(
            background = Color(0xFFF5F5F7),
            surface = Color(0xFFFFFFFF),
            surfaceSoft = Color(0xFFF4F8FB),
            primary = Color(0xFF0638DF),
            primaryPressed = Color(0xFF052BB3),
            onPrimary = Color(0xFFFFFFFF),
            accent = Color(0xFFC7F300),
            textPrimary = Color(0xFF0E1738),
            textSecondary = Color(0xFF667085),
            textPlaceholder = Color(0xFF98A2B3),
            border = Color(0xFFD8DDE8),
            success = Color(0xFF17B26A),
            warning = Color(0xFFF5A623),
            errorForeground = Color(0xFFE5484D),
            disabledSurface = Color(0xFFC9CED8),
            disabledForeground = Color(0xFF7A8291),
            scrim = Color(0xFF0E1738).copy(alpha = 0.46f),
            chrome = Color(0xFFFFFFFF).copy(alpha = 0.96f),
        )
    }
}
