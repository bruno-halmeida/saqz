package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.anybody_expanded_extrabold
import org.jetbrains.compose.resources.Font

/**
 * Família de display do hero da Home nova: Anybody (instância estática wdth 110 / wght 800),
 * a fonte dos títulos da landing. Vive em commonMain porque vale para Android e iOS; a Inter
 * de [saqzFontFamily] continua só no Android.
 *
 * SPEC_DEVIATION: fonte fora do export oficial.
 * Reason: redesenho da Home aprovado pelo usuário em 2026-09-16 (VUL-217).
 */
@Composable
fun saqzDisplayFontFamily(): FontFamily = FontFamily(Font(Res.font.anybody_expanded_extrabold, FontWeight(800)))
