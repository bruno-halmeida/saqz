package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.inter_bold
import br.com.saqz.designsystem.resources.inter_light
import br.com.saqz.designsystem.resources.inter_regular
import br.com.saqz.designsystem.resources.inter_semibold
import org.jetbrains.compose.resources.Font

@Composable
actual fun saqzFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_light, FontWeight(300)),
    Font(Res.font.inter_regular, FontWeight(400)),
    Font(Res.font.inter_semibold, FontWeight(600)),
    Font(Res.font.inter_bold, FontWeight(700)),
)
