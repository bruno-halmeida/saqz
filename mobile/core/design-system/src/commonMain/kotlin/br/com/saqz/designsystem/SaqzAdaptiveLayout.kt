package br.com.saqz.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A partir daqui o texto do sistema já está grande o bastante para empilhar o que ficava lado a lado. */
const val SaqzLargeFontScale = 1.3f

/**
 * Decide se [columns] colunas lado a lado cabem em [availableWidth] sem partir o rótulo mais longo.
 *
 * Decidir só por `fontScale` falhou no iPhone: o mesmo "Texto Maior" chega com escalas diferentes por
 * aparelho e a largura da tela muda (o 17e é mais estreito que o 17). A largura de cada coluna é o que
 * decide: cada caractere do rótulo ocupa ~7 dp na escala 1 e cresce com a fonte.
 */
fun saqzColumnsFit(availableWidth: Dp, columns: Int, longestLabelChars: Int, fontScale: Float): Boolean {
    if (columns <= 1) return true
    val needed = CharWidth * longestLabelChars * fontScale
    return availableWidth / columns >= needed
}

private val CharWidth = 7.dp
