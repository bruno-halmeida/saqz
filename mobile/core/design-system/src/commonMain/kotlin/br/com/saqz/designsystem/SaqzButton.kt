package br.com.saqz.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.state_loading
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

enum class SaqzButtonVariant { Primary, Secondary, Danger, Ghost }

enum class SaqzButtonSize { Sm, Md }

// Container/content/border a variant paints, all pulled from the token registry.
// accent/on-accent never appear here: accent is a non-clickable status hue.
@Immutable
internal data class SaqzButtonColors(
    val container: Color,
    val content: Color,
    val border: Color?,
)

internal fun SaqzColorTokens.buttonColors(variant: SaqzButtonVariant): SaqzButtonColors =
    when (variant) {
        SaqzButtonVariant.Primary -> SaqzButtonColors(primary, onPrimary, border = null)
        // Linha azul, não cinza: `.saqz-btn--secondary` do export é
        // `border-color:var(--saqz-blue)` sobre branco, com o rótulo no mesmo azul.
        SaqzButtonVariant.Secondary -> SaqzButtonColors(surface, primary, border = primary)
        SaqzButtonVariant.Danger -> SaqzButtonColors(errorForeground, onPrimary, border = null)
        SaqzButtonVariant.Ghost -> SaqzButtonColors(Color.Transparent, primary, border = null)
    }

// Spatial press response: shrinks to the policy scale while held, 1f at rest.
// Reduced motion pins pressScale at 1f, so this returns 1f and only opacity moves.
internal fun saqzPressScale(pressed: Boolean, motion: SaqzMotionPolicy): Float =
    if (pressed) motion.pressScale else 1f

// ponytail: live press feedback is published to a custom semantics key because the
// suite runs on iosSimulatorArm64Test, which has no screenshot capture — this is the
// only way a black-box test can observe scale/opacity/offset feedback on press.
// `offsetY` viaja em dp crus porque o teste não tem Density para converter px.
@Immutable
internal data class SaqzPressFeedback(val scale: Float, val alpha: Float, val offsetY: Float)

internal val SaqzPressFeedbackKey = SemanticsPropertyKey<SaqzPressFeedback>("SaqzPressFeedback")
internal var SemanticsPropertyReceiver.saqzPressFeedback by SaqzPressFeedbackKey

@Composable
fun SaqzButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SaqzButtonVariant = SaqzButtonVariant.Primary,
    size: SaqzButtonSize = SaqzButtonSize.Md,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    labelStyle: TextStyle? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    leadingContent: (@Composable (Color) -> Unit)? = null,
    trailingContent: (@Composable (Color) -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val motion = SaqzTheme.motion
    // Pílula em toda variante e tamanho (10d): não existe botão de canto reto.
    val shape = CircleShape
    val resolved = colors.buttonColors(variant)
    val active = enabled && !loading

    // Alturas do export: `.saqz-btn--md{min-height:52px}` e `.saqz-btn--sm{min-height:44px}`.
    // ponytail: Sm reusa `iconButtonSize` porque 44 é literalmente o mesmo número que o
    // export dá aos dois; se algum dia divergirem, nasce um `buttonHeightSm` no VUL-44.
    val minHeight = if (size == SaqzButtonSize.Sm) metrics.iconButtonSize else metrics.buttonHeight
    val horizontalPadding = if (size == SaqzButtonSize.Sm) 16.dp else 20.dp
    val defaultStyle =
        if (size == SaqzButtonSize.Sm) SaqzTheme.typography.support.copy(fontWeight = SaqzTheme.typography.label.fontWeight)
        else SaqzTheme.typography.label

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = saqzPressScale(pressed, motion),
        animationSpec = tween(motion.pressDurationMillis),
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(motion.opacityFeedbackDurationMillis),
        label = "pressAlpha",
    )
    // `translateY(1px)` do `:active` do export, no mesmo tempo do scale. Reduced
    // motion zera o token e o botão só perde opacidade.
    val offsetY by animateFloatAsState(
        targetValue = if (pressed) motion.pressOffset.value else 0f,
        animationSpec = tween(motion.pressDurationMillis),
        label = "pressOffset",
    )

    val loadingLabel = stringResource(Res.string.state_loading)
    // Carregando não é desabilitado: o export pinta o botão ocupado na cor da variante
    // com `opacity:.85`, e só o `:disabled` cai para a paleta cinza. Quem decide a cor
    // aqui é `enabled`; `active` (que soma o loading) segue mandando no clique.
    val container = if (enabled) resolved.container else colors.disabledSurface
    val content = if (enabled) contentColor ?: resolved.content else colors.disabledForeground
    val loadingDim = if (loading) 0.85f else 1f
    // A borda acompanha `enabled` pelo mesmo motivo do container: `primary` desenha a
    // linha azul do secondary no export, e mantê-la desabilitado deixaria contorno de
    // botão clicável num botão que não responde.
    val outline = if (enabled) borderColor ?: resolved.border else null

    Box(
        // clickable fica depois de clip/background para a área de toque respeitar a
        // forma; graphicsLayer segue antes do clip para o feedback escalar o fundo junto.
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offsetY.dp.toPx()
                this.alpha = alpha * loadingDim
            }
            .semantics {
                saqzPressFeedback = SaqzPressFeedback(scale, alpha, offsetY)
                if (loading) stateDescription = loadingLabel
            }
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .sizeIn(minWidth = minHeight, minHeight = minHeight)
            .clip(shape)
            .background(container, shape)
            .then(outline?.let { Modifier.border(1.dp, it, shape) } ?: Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = active,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = metrics.subGrid),
        contentAlignment = Alignment.Center,
    ) {
        // Carregando é `◌ Criando grupo…`: o spinner entra no lugar do ícone da frente e
        // o rótulo continua visível. Quem chama escreve o gerúndio — o componente não
        // conjuga nada. O botão fica mais largo que o ocioso, e é assim que o export
        // desenha: reservar o vão do spinner desde o repouso deixaria um buraco em todo
        // botão parado.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = content,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                leadingContent?.invoke(content)
            }
            Text(
                text = label,
                color = content,
                style = labelStyle ?: defaultStyle,
            )
            trailingContent?.invoke(content)
        }
    }
}

// Fundo do alvo de 44 por variante. Sem fundo (`null`) é o repouso: o glifo sobre o que
// estiver atrás. A ordem resolve a combinação absurda (`filled = true, outlined = true`)
// sem precisar proibi-la em runtime.
internal fun SaqzColorTokens.iconButtonBackground(
    soft: Boolean,
    filled: Boolean,
    outlined: Boolean,
): Color? = when {
    filled -> primary
    soft -> surfaceSoft
    outlined -> surface
    else -> null
}

/**
 * 10e — alvo quadrado de 44 ("44×44 sempre", diz o export), o glifo desenhado em
 * [content]. Quatro fundos, mutuamente exclusivos na prática: nada (transparente),
 * `soft` (ice), `filled` (azul sólido — o "+" de ação) e `outlined` (branco com a linha
 * de 1px). `dot` marca pendência com o ponto lime, e combina com qualquer um deles.
 *
 * [outlined] é o voltar solto do fluxo 1: círculo branco de 44 com borda `border`, sem
 * barra em volta, em oito das onze telas de autenticação. O glifo continua vindo do
 * chamador, e o `tint` padrão de `SaqzIcon` já é o navy que o export pede ali — a seta
 * azul do VUL-61 é da `SaqzTopAppBar` e só dela.
 *
 * O glifo vem do chamador com o próprio `tint`, como já acontece em `soft`: em
 * `filled` passe `SaqzTheme.colors.onPrimary`, senão o traço navy some no azul.
 *
 * ponytail: o componente não recolore [content] porque `SaqzIcon` recebe `tint`
 * explícito; se o esquecimento virar bug recorrente, o caminho é `SaqzIcon` ler uma
 * cor de conteúdo do tema — mudança em SaqzIcons.kt, não aqui.
 */
@Composable
fun SaqzIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    soft: Boolean = false,
    filled: Boolean = false,
    outlined: Boolean = false,
    dot: Boolean = false,
    enabled: Boolean = true,
    size: Dp = SaqzTheme.metrics.iconButtonSize,
    content: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    val background = colors.iconButtonBackground(soft = soft, filled = filled, outlined = outlined)
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    // Duas caixas de propósito: o desenho do export manda no círculo (44dp), o piso de
    // acessibilidade manda no alvo (48dp). O toque e a semântica vivem na caixa externa,
    // o clip, o fundo e o glifo na interna — encolher o alvo para casar com o visual é
    // regressão, e igualar o visual ao alvo é desobedecer o design.
    Box(
        modifier = modifier
            .sizeIn(minWidth = SaqzTheme.metrics.minimumTouchTarget, minHeight = SaqzTheme.metrics.minimumTouchTarget)
            .clickable(
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = {
                    dismissKeyboard()
                    onClick()
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // O ponto se ancora no círculo visual, não no alvo de toque: preso à caixa de 48
        // ele descolaria da borda do desenho por 2dp de cada lado.
        Box(modifier = Modifier.size(size)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .then(background?.let { Modifier.background(it, CircleShape) } ?: Modifier)
                    // A linha entra depois do fundo e no mesmo círculo: `border` de 1px é o
                    // que separa o voltar branco do fluxo 1 do canvas, também branco.
                    .then(if (outlined) Modifier.border(1.dp, colors.border, CircleShape) else Modifier)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) { content() }
            if (dot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .size(8.dp)
                        .background(colors.accent, CircleShape),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SaqzButtonPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzButton(label = "Confirmar presença", onClick = {})
        SaqzButton(label = "Editar", onClick = {}, variant = SaqzButtonVariant.Secondary)
        SaqzButton(label = "Excluir grupo", onClick = {}, variant = SaqzButtonVariant.Danger)
        SaqzButton(label = "Cancelar", onClick = {}, variant = SaqzButtonVariant.Ghost)
        SaqzButton(label = "Criar jogo", onClick = {}, size = SaqzButtonSize.Sm)
        SaqzButton(label = "Criando grupo…", onClick = {}, loading = true, fullWidth = true)
        SaqzButton(label = "Criar grupo", onClick = {}, enabled = false, fullWidth = true)
    }
}

@Preview
@Composable
private fun SaqzIconButtonPreview() = SaqzTheme {
    SaqzPreviewGrid {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzIconButton(onClick = {}, contentDescription = "Voltar") { SaqzIcon(SaqzIcons.ChevronLeft) }
            SaqzIconButton(onClick = {}, contentDescription = "Voltar", outlined = true) {
                SaqzIcon(SaqzIcons.ChevronLeft)
            }
            SaqzIconButton(onClick = {}, contentDescription = "Notificações", dot = true) {
                SaqzIcon(SaqzIcons.Bell)
            }
            SaqzIconButton(onClick = {}, contentDescription = "Buscar", soft = true) {
                SaqzIcon(SaqzIcons.Search)
            }
            SaqzIconButton(onClick = {}, contentDescription = "Criar jogo", filled = true) {
                SaqzIcon(SaqzIcons.Plus, tint = SaqzTheme.colors.onPrimary)
            }
        }
    }
}
