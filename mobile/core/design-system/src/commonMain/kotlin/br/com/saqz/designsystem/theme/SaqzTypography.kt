package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class SaqzTypography(
    val heroDisplay: TextStyle,
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val lead: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val navigation: TextStyle,
) {
    companion object {
        val Default = SaqzTypography(
            heroDisplay = TextStyle(
                fontSize = 34.sp,
                fontWeight = FontWeight(600),
                lineHeight = 1.07.em,
                letterSpacing = (-0.005).em,
            ),
            displayLarge = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight(600),
                lineHeight = 1.10.em,
                letterSpacing = (-0.009).em,
            ),
            displayMedium = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight(600),
                lineHeight = 1.20.em,
                letterSpacing = (-0.011).em,
            ),
            lead = TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight(400),
                lineHeight = 1.14.em,
                letterSpacing = 0.007.em,
            ),
            body = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight(400),
                lineHeight = 1.47.em,
                letterSpacing = (-0.022).em,
            ),
            bodyStrong = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight(600),
                lineHeight = 1.24.em,
                letterSpacing = (-0.022).em,
            ),
            caption = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight(400),
                lineHeight = 1.43.em,
                letterSpacing = (-0.016).em,
            ),
            navigation = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight(400),
                lineHeight = 1.00.em,
                letterSpacing = (-0.010).em,
            ),
        )
    }
}
