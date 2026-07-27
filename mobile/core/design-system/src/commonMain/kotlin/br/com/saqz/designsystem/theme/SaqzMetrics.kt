package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Grade de 4 do fluxo 10c. Botões e chips são pílula (CircleShape), por isso não há
// raio de botão aqui — o raio nomeado só existe onde a forma é retangular.
//
// Espaçamento e raio vêm de design-tokens/spacing.css e design-tokens/radius.css. As
// alturas de componente (botão, ícone, switch) não são token CSS: saem do CSS dos
// componentes no _ds_bundle.js do export — design-tokens/README.md tem a linha de cada um.
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
    val buttonHeight: Dp,
    val iconButtonSize: Dp,
    val switchTrackWidth: Dp,
    val switchTrackHeight: Dp,
    val switchThumbSize: Dp,
    val switchThumbInset: Dp,
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
            // Piso de acessibilidade para linha e alvo genérico — NÃO é a altura do
            // botão nem a do IconButton, e os três divergirem é o desenho, não bug:
            //   • buttonHeight 52 fica acima do piso;
            //   • iconButtonSize 44 é o número do próprio export (e o do HIG da Apple),
            //     e quem quiser 48 aumenta a área de toque em volta, não o desenho.
            // Não "conserte" nenhum dos três para igualar aos outros.
            minimumTouchTarget = 48.dp,
            buttonHeight = 52.dp,
            iconButtonSize = 44.dp,
            // Trilho 52×30 com knob de 24 e folga de 3 em volta: 3 + 24 + 3 = 30.
            switchTrackWidth = 52.dp,
            switchTrackHeight = 30.dp,
            switchThumbSize = 24.dp,
            switchThumbInset = 3.dp,
        )
    }
}
