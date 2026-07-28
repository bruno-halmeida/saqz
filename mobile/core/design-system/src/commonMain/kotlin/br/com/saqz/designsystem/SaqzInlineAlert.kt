package br.com.saqz.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme

enum class SaqzInlineAlertTone { Error, Success, Warning }

/**
 * O aviso que fica **no fluxo do conteúdo** das telas do fluxo 1: acima dos campos, e
 * não some sozinho. Nada aqui é o [SaqzToast] (sobreposição temporária) nem o
 * [SaqzOfflineBanner] (faixa de conectividade) — quem apaga este bloco é a tela, tirando
 * a chamada da composição.
 *
 * [emphasis] é o trecho de [text] que sai em peso forte, e existe para que nenhuma das
 * cinco telas do fluxo monte `buildAnnotatedString` por conta própria: o 1i escreve
 * "**E-mail ou senha incorretos.** Confira os dados e tente de novo." e o 1j
 * "**Revise 3 campos** para criar sua conta.". O sucesso do 1f é a frase inteira em
 * negrito — nesse caso `emphasis` é o próprio [text]. Trecho que não aparece em [text]
 * é ignorado: a frase continua legível, sem o negrito.
 *
 * O anúncio para leitor de tela é o ponto do componente, não enfeite: sem ele quem toca
 * em "Entrar" não descobre o que houve. Erro e aviso interrompem (`Assertive`), sucesso
 * espera a vez (`Polite`).
 */
@Composable
fun SaqzInlineAlert(
    text: String,
    tone: SaqzInlineAlertTone,
    modifier: Modifier = Modifier,
    emphasis: String? = null,
) {
    val motion = SaqzTheme.motion
    val (foreground, container) = saqzInlineAlertColors(tone, SaqzTheme.colors)
    val offsetPx = with(LocalDensity.current) { saqzInlineAlertOffset(motion).toPx() }

    // A entrada roda quando o bloco *entra na composição*, que é literalmente "quando
    // aparece" — AnimatedVisibility com `visible` já ligado no primeiro quadro não
    // animaria, e é assim que as telas vão usar isto (`if (erro != null) { ... }`).
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(saqzInlineAlertDurationMillis(tone), easing = motion.emphasized),
        )
    }

    val shape = RoundedCornerShape(SaqzTheme.metrics.cardRadius)
    Row(
        modifier = modifier
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * offsetPx
            }
            .fillMaxWidth()
            .background(container, shape)
            .padding(horizontal = ALERT_PADDING_HORIZONTAL, vertical = ALERT_PADDING_VERTICAL)
            .semantics(mergeDescendants = true) {
                liveRegion = when (tone) {
                    SaqzInlineAlertTone.Success -> LiveRegionMode.Polite
                    else -> LiveRegionMode.Assertive
                }
            },
        // O texto do erro e do aviso quebra em duas linhas e o ícone acompanha o topo; o
        // sucesso é uma linha só e centraliza.
        verticalAlignment = when (tone) {
            SaqzInlineAlertTone.Success -> Alignment.CenterVertically
            else -> Alignment.Top
        },
        horizontalArrangement = Arrangement.spacedBy(ALERT_GAP),
    ) {
        when (tone) {
            // Único dos três com círculo sólido atrás; os outros dois são o glifo solto.
            SaqzInlineAlertTone.Success -> Box(
                modifier = Modifier.size(SUCCESS_BADGE).background(SaqzTheme.colors.success, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.onPrimary, size = SUCCESS_CHECK)
            }
            SaqzInlineAlertTone.Error -> AlertGlyph(SaqzIcons.CircleAlert, SaqzTheme.colors.errorForeground)
            SaqzInlineAlertTone.Warning -> AlertGlyph(SaqzIcons.Clock, SaqzTheme.colors.warning)
        }
        Text(
            text = saqzInlineAlertText(text, emphasis),
            style = SaqzTheme.typography.support.copy(
                fontSize = ALERT_TEXT_SIZE,
                lineHeight = ALERT_TEXT_SIZE * ALERT_LINE_HEIGHT,
            ),
            color = foreground,
        )
    }
}

// O glifo solto do erro e do aviso, 1px abaixo do topo como o export desenha.
@Composable
private fun AlertGlyph(icon: ImageVector, tint: Color) = SaqzIcon(
    icon = icon,
    tint = tint,
    size = ALERT_ICON,
    modifier = Modifier.padding(top = 1.dp),
)

/**
 * Primeiro plano e fundo de cada tom. Os três textos **não** são os tokens de feedback:
 * `--saqz-error` sobre o próprio vermelho a 8% dá 3,5:1, `--saqz-success` sobre o verde
 * a 10% dá 2,5:1 e `--saqz-warning` sobre o âmbar a 12% dá 1,9:1 — nenhum passa AA. O
 * export escurece o texto e mantém o fundo tintado, a mesma saída do chip warning do
 * VUL-59. Trocar por token é o defeito que `SaqzInlineAlertTest` tranca.
 */
internal fun saqzInlineAlertColors(
    tone: SaqzInlineAlertTone,
    colors: SaqzColorTokens,
): Pair<Color, Color> = when (tone) {
    SaqzInlineAlertTone.Error -> ERROR_TEXT to colors.errorForeground.copy(alpha = 0.08f)
    SaqzInlineAlertTone.Success -> SUCCESS_TEXT to colors.success.copy(alpha = 0.10f)
    SaqzInlineAlertTone.Warning -> WARNING_TEXT to colors.warning.copy(alpha = 0.12f)
}

// `fluxo1.entrada` do contrato: .28s no erro, .32s no sucesso. O aviso não tem duração
// própria no export e anda com o erro — os dois são a mesma interrupção.
internal fun saqzInlineAlertDurationMillis(tone: SaqzInlineAlertTone): Int = when (tone) {
    SaqzInlineAlertTone.Success -> SUCCESS_DURATION_MILLIS
    else -> ALERT_DURATION_MILLIS
}

// Com Reduce Motion o bloco aparece sem deslocamento; a opacidade fica, porque é ela
// que diz "isto é novo" para quem não quer movimento.
internal fun saqzInlineAlertOffset(motion: SaqzMotionPolicy) =
    if (motion == SaqzMotionPolicy.Reduced) 0.dp else ENTRANCE_OFFSET

// O `buildAnnotatedString` mora aqui, e é esse o ponto: cinco telas repetiriam o mesmo
// boilerplate para pôr negrito no meio de uma frase.
internal fun saqzInlineAlertText(text: String, emphasis: String?): AnnotatedString {
    val start = if (emphasis.isNullOrEmpty()) -1 else text.indexOf(emphasis)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + emphasis!!.length)
    }
}

// Versões escurecidas de `--saqz-error`, `--saqz-success` e `--saqz-warning`, escolhidas
// pelo export para ler sobre o fundo tintado. Ficam neste arquivo, e não em
// SaqzColorTokens: só o alerta os usa, e o contrato os guarda em `fluxo1.alertaInline`.
private val ERROR_TEXT = Color(0xFFA3262A)
private val SUCCESS_TEXT = Color(0xFF0A7A47)
private val WARNING_TEXT = Color(0xFF8A5A05)

private val ALERT_PADDING_VERTICAL = 12.dp
private val ALERT_PADDING_HORIZONTAL = 14.dp
private val ALERT_GAP = 10.dp
private val ALERT_ICON = 20.dp
private val SUCCESS_BADGE = 24.dp
private val SUCCESS_CHECK = 14.dp
private val ENTRANCE_OFFSET = 8.dp
private val ALERT_TEXT_SIZE = 13.5.sp
private const val ALERT_LINE_HEIGHT = 1.45f
private const val ALERT_DURATION_MILLIS = 280
private const val SUCCESS_DURATION_MILLIS = 320

@Preview
@Composable
private fun SaqzInlineAlertPreview() = SaqzTheme {
    // Os três tons do 1i, 1f e 1k. Tom que não está aqui não está sendo conferido.
    SaqzPreviewGrid {
        SaqzInlineAlert(
            text = "E-mail ou senha incorretos. Confira os dados e tente de novo.",
            emphasis = "E-mail ou senha incorretos.",
            tone = SaqzInlineAlertTone.Error,
        )
        SaqzInlineAlert(
            text = "Enviamos um novo código para o seu e-mail.",
            emphasis = "Enviamos um novo código para o seu e-mail.",
            tone = SaqzInlineAlertTone.Success,
        )
        SaqzInlineAlert(
            text = "Esse código expirou. Peça um novo para continuar.",
            tone = SaqzInlineAlertTone.Warning,
        )
    }
}
