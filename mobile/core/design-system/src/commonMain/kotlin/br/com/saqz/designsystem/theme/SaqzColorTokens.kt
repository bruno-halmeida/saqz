package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Paleta do fluxo 10a, conferida hex a hex contra design-tokens/colors.css do export
// oficial. Uma única cor de linha (`border`) atende card, input e divisória — o design
// system não mantém três cinzas para o mesmo traço de 1px.
//
// `scrim` e `chrome` são os dois tokens com alfa, e por isso ficam fora do
// contrato em ui-contract.json, que guarda hex opaco. `scrim` é o `--scrim` do
// export; `chrome` (branco a 96%) é nosso, o export não tem equivalente.
//
// `warningForeground` é o único hex que não sai de colors.css: o export o escreve
// literal em `.saqz-chip--warning` (_ds_bundle.js l.393), porque o âmbar da paleta
// sobre o próprio âmbar a 14% dá 1,9:1 e some. Está anotado em _exceptions do
// contrato para nenhuma reconciliação futura tentar buscá-lo em colors.css.
//
// ponytail: os fills translúcidos do colors.css (--brand-fill-08/11, --lime-fill-32,
// --error-fill-10) ficam fora até um componente precisar deles; hoje o chip resolve o
// alfa a partir da cor sólida, no próprio componente.
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
    val warningForeground: Color,
    val errorForeground: Color,
    val successForeground: Color,
    val disabledSurface: Color,
    val disabledForeground: Color,
    val scrim: Color,
    val chrome: Color,
    /**
     * Vermelho claro das saídas sobre o azul da marca (DEB-148, pedido de 29/10): o número do Saiu no placar e o
     * botão Saída do caixa. 4,7:1 sobre o `primary`.
     */
    val outOnPrimary: Color = Color(0xFFFFB3B8),
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
            // SPEC_DEVIATION: warningForeground #B26B00 -> #8F5600 e errorForeground #E5484D -> #C9363B.
            // Reason: contraste AA (decisão do dono em 20/10/2026) — o texto do chip Warning e o texto branco
            // do botão Danger não passavam de 4,5:1 com os valores do export.
            warningForeground = Color(0xFF8F5600),
            errorForeground = Color(0xFFC9363B),
            // SPEC_DEVIATION: successForeground #0E7A4B não existe no export.
            // Reason: contraste AA (decisão do dono em 20/10/2026) — o verde da paleta (#17B26A) fica abaixo de
            // 3:1 no branco; este é o texto do chip Success e do resultado confirmado, com 5,4:1.
            successForeground = Color(0xFF0E7A4B),
            disabledSurface = Color(0xFFC9CED8),
            disabledForeground = Color(0xFF7A8291),
            scrim = Color(0xFF0E1738).copy(alpha = 0.46f),
            chrome = Color(0xFFFFFFFF).copy(alpha = 0.96f),
        )
    }
}
