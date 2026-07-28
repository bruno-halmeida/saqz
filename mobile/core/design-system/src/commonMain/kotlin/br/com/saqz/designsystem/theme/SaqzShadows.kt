package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// Sombra não é `Dp` e não é cor: é deslocamento + blur + cor com alfa, os quatro juntos.
// Por isso não mora nem em SaqzMetrics nem em SaqzColorTokens — é registry próprio, com
// bloco próprio no ui-contract.json.
//
// Também não é `elevation`. `Modifier.shadow` do Compose desenha elevação Material: sem
// deslocamento e sem cor com alfa própria. O `-12px` do `--shadow-sheet` é justamente o
// que ele não faz, e é ele que dá ao painel o ar de estar *subindo* em vez de flutuando.
@Immutable
data class SaqzShadow(
    val offsetX: Dp,
    val offsetY: Dp,
    val blur: Dp,
    val color: Color,
)

// Só as duas do export que alguma peça usa. `--shadow-device` é a moldura de telefone da
// página de apresentação e `--shadow-none` é a ausência do modificador: nenhum dos dois
// vira token de app.
@Immutable
data class SaqzShadows(
    val sheet: SaqzShadow,
    val toast: SaqzShadow,
) {
    companion object {
        val Default = SaqzShadows(
            // `--shadow-sheet: 0 -12px 40px rgba(14,23,56,.12)` — sobe, não cai.
            sheet = SaqzShadow(
                offsetX = 0.dp,
                offsetY = (-12).dp,
                blur = 40.dp,
                color = SHADOW_NAVY.copy(alpha = 0.12f),
            ),
            // `--shadow-toast: 0 12px 40px rgba(14,23,56,.20)`.
            toast = SaqzShadow(
                offsetX = 0.dp,
                offsetY = 12.dp,
                blur = 40.dp,
                color = SHADOW_NAVY.copy(alpha = 0.20f),
            ),
        )
    }
}

// O `rgba(14,23,56,…)` das duas sombras é o navy de `colors.textPrimary` (#0E1738). Fica
// literal aqui em vez de referenciar o token de cor porque sombra do export nunca segue a
// paleta de texto: se um dia o navy do texto mudar, a sombra continua a mesma.
private val SHADOW_NAVY = Color(0xFF0E1738)

/**
 * Desenha [shadow] atrás do conteúdo, na [shape] do próprio conteúdo.
 *
 * SPEC_DEVIATION: o VUL-64 previa `drawBehind` à mão.
 * Reason: `Modifier.dropShadow` (Compose 1.9+, commonMain, estável) já é exatamente
 * offset + blur + cor com alfa em cima de uma `Shape` — a razão de não usar Compose era o
 * `Modifier.shadow`, que continua não servindo. Um blur gaussiano escrito à mão em
 * `drawBehind` seria reimplementar o que a biblioteca entrega.
 *
 * ponytail: `blur` entra direto como raio do `Shadow`. O raio do CSS e o do
 * `BlurMaskFilter` convertem para sigma por constantes diferentes (b/2 contra 0,577b), o
 * que deixa a sombra ~15% mais aberta que a do navegador. Se algum dia isso importar, o
 * fator é `blur * 0.866`.
 */
fun Modifier.saqzShadow(shadow: SaqzShadow, shape: Shape): Modifier = dropShadow(
    shape = shape,
    shadow = Shadow(
        radius = shadow.blur,
        color = shadow.color,
        offset = DpOffset(shadow.offsetX, shadow.offsetY),
    ),
)
