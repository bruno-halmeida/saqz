package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Grade de 4 do fluxo 10c. Botões e chips são pílula (CircleShape), por isso não há
// raio de botão aqui — o raio nomeado só existe onde a forma é retangular.
@Immutable
data class SaqzMetrics(
    val grid: Dp,
    val subGrid: Dp,
    val horizontalPadding: Dp,
    val blockGap: Dp,
    val sectionGap: Dp,
    val sectionVerticalPadding: Dp,
    val inputRadius: Dp,
    val cardRadius: Dp,
    val blockRadius: Dp,
    val sheetRadius: Dp,
    val bottomNavHeight: Dp,
    val minimumTouchTarget: Dp,
) {
    companion object {
        val Default = SaqzMetrics(
            grid = 8.dp,
            subGrid = 4.dp,
            horizontalPadding = 16.dp,
            blockGap = 12.dp,
            sectionGap = 24.dp,
            sectionVerticalPadding = 48.dp,
            inputRadius = 10.dp,
            cardRadius = 12.dp,
            blockRadius = 20.dp,
            sheetRadius = 28.dp,
            bottomNavHeight = 76.dp,
            // O mock pede 44 (número do iOS); 48 é o mínimo acessível do Android e
            // o design system não abaixa acessibilidade para casar com o mock.
            minimumTouchTarget = 48.dp,
        )
    }
}
