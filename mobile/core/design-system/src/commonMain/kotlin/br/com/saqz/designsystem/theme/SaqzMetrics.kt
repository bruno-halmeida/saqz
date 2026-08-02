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
//
// Duas exceções, e só duas: `minimumTouchTarget` (o export pede 44) e
// `sectionVerticalPadding` (o export não tem). Ambas registradas em `_exceptions` no
// ui-contract.json, com motivo. O resto casa com o export e pode ser reconciliado direto.
//
// Nem todo número de tela vira token daqui. O fluxo 1 (autenticação) tem os seus no
// bloco `fluxo1` do ui-contract.json, porque são medidas de tela e não do inventário do
// fluxo 10 — o campo de 54, a caixa de código de 56×60, a onda de 130. Cinco deles,
// porém, são estes mesmos tokens reusados (`iconButtonSize`, `buttonHeight`,
// `inputRadius`, `blockGap`, `cardRadius`), e o SaqzFluxo1ContractTest amarra os dois
// lados: mexer aqui sem mexer lá reprova de propósito.
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
    val avatarSize: Dp,
    val photoBadgeSize: Dp,
    val photoIconSize: Dp,
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
            // Medido nas telas do fluxo 10: o export não declara esse espaçamento em
            // CSS nenhum. Export novo não tem o que dizer sobre ele.
            sectionVerticalPadding = 48.dp,
            inputRadius = 10.dp,
            cardRadius = 12.dp,
            blockRadius = 20.dp,
            sheetRadius = 28.dp,
            bottomNavHeight = 76.dp,
            // Piso de acessibilidade para linha e alvo genérico. O export pede 44 aqui
            // (`--touch-min` no spacing.css); 48 é override deliberado, não desatualização
            // — Material manda 48 e o design system não abaixa acessibilidade para casar
            // com o export. NÃO é a altura do botão nem a do IconButton, e os três
            // divergirem é o desenho, não bug:
            //   • buttonHeight 52 fica acima do piso;
            //   • iconButtonSize 44 é o número do próprio export (e o do HIG da Apple),
            //     e quem quiser 48 aumenta a área de toque em volta, não o desenho.
            // Não "conserte" nenhum dos três para igualar aos outros.
            minimumTouchTarget = 48.dp,
            buttonHeight = 52.dp,
            iconButtonSize = 44.dp,
            avatarSize = 80.dp,
            photoBadgeSize = 28.dp,
            photoIconSize = 16.dp,
            // Trilho 52×30 com knob de 24 e folga de 3 em volta: 3 + 24 + 3 = 30.
            switchTrackWidth = 52.dp,
            switchTrackHeight = 30.dp,
            switchThumbSize = 24.dp,
            switchThumbInset = 3.dp,
        )
    }
}
