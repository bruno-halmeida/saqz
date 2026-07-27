package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Escala mobile do fluxo 10b. Sentence case em tudo; `eyebrow` é a única caixa alta
// e a única com tracking positivo. Tamanhos grandes levam tracking negativo.
@Immutable
data class SaqzTypography(
    val headline: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val support: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val eyebrow: TextStyle,
    val navigation: TextStyle,
) {
    companion object {
        val Default = SaqzTypography(
            headline = TextStyle(
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight(700),
                letterSpacing = (-0.03).em,
            ),
            title = TextStyle(
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight(700),
                letterSpacing = (-0.02).em,
            ),
            subtitle = TextStyle(
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight(600),
                letterSpacing = 0.em,
            ),
            body = TextStyle(
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight(400),
                letterSpacing = 0.em,
            ),
            support = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight(400),
                letterSpacing = 0.em,
            ),
            label = TextStyle(
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight(600),
                letterSpacing = 0.em,
            ),
            caption = TextStyle(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight(400),
                letterSpacing = 0.em,
            ),
            eyebrow = TextStyle(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight(600),
                letterSpacing = 0.08.em,
            ),
            navigation = TextStyle(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight(600),
                letterSpacing = 0.em,
            ),
        )
    }
}
