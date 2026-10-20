package br.com.saqz.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_decrease
import br.com.saqz.designsystem.resources.action_increase
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

/**
 * 10h — trilho pílula com polegar branco. Com [label], a linha inteira é o alvo
 * de toque e o TalkBack anuncia um único switch. Sem [label], [contentDescription]
 * passa a ser obrigatório: um switch sem nome só anuncia o estado, nunca o que
 * ele controla.
 *
 * Movimento próprio (`switchDurationMillis`/`switchEasing`): no export o switch é
 * `.18s ease` e o segmented é `.28s` enfático. Igualar os dois é regressão.
 */
@Composable
fun SaqzSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val motion = SaqzTheme.motion
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> colors.disabledSurface
            checked -> colors.primary
            else -> colors.border
        },
        animationSpec = tween(motion.switchDurationMillis, easing = motion.switchEasing),
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) {
            metrics.switchTrackWidth - metrics.switchThumbSize - metrics.switchThumbInset
        } else {
            metrics.switchThumbInset
        },
        animationSpec = tween(motion.switchDurationMillis, easing = motion.switchEasing),
        label = "switchThumb",
    )

    val toggle = Modifier.toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = {
            dismissKeyboard()
            onCheckedChange(it)
        },
    )
    val control = @Composable {
        Box(
            modifier = Modifier
                .size(metrics.switchTrackWidth, metrics.switchTrackHeight)
                .clip(CircleShape)
                .background(track, CircleShape),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .align(Alignment.CenterStart)
                    .size(metrics.switchThumbSize)
                    .background(colors.surface, CircleShape),
            )
        }
    }

    if (label == null) {
        val name = requireNotNull(contentDescription) {
            "SaqzSwitch sem label precisa de contentDescription"
        }
        Box(
            modifier = modifier.then(toggle).semantics { this.contentDescription = name },
        ) { control() }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(toggle)
                .heightIn(min = metrics.minimumTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            Text(
                text = label,
                style = SaqzTheme.typography.body,
                color = if (enabled) colors.textPrimary else colors.disabledForeground,
                modifier = Modifier.weight(1f),
            )
            control()
        }
    }
}

/**
 * Valor do stepper depois de um passo, preso ao intervalo. Fora da composição para
 * o limite ser testável sem UI.
 */
internal fun saqzSteppedValue(value: Int, step: Int, min: Int, max: Int): Int =
    (value + step).coerceIn(min, max)

/**
 * O ± do 10h é o único círculo branco com borda do export
 * (`width:40px;height:40px;border-radius:50%;border:1px solid #D8DDE8;background:#fff`),
 * e por isso não passa pelo [SaqzIconButton], que só conhece transparente, ice e azul —
 * sobre o canvas o ice some. Desenho de 40 dentro do alvo de 48, como o [SaqzIconButton]
 * já faz com 44.
 *
 * A borda fica na cor de linha nos dois estados: apagá-la no desabilitado devolveria o
 * círculo invisível que o ice causava.
 *
 * ponytail: o 40 é dp cru porque `SaqzMetrics` não tem esse número e o arquivo é de
 * outro ticket; se um segundo componente pedir o mesmo círculo, nasce o token.
 */
@Composable
private fun SaqzStepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = SaqzTheme.colors
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    Box(
        modifier = Modifier
            .sizeIn(
                minWidth = SaqzTheme.metrics.minimumTouchTarget,
                minHeight = SaqzTheme.metrics.minimumTouchTarget,
            )
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
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(icon, tint = if (enabled) colors.textPrimary else colors.disabledForeground)
        }
    }
}

/**
 * 10h — menos, valor, mais. Os botões desligam ao encostar no limite.
 *
 * [format] escreve o valor ("2h", "19h"); [previous]/[next] dizem para onde o – e o + levam quando o passo
 * não é de 1 (grade de durações). [icon] é o ícone cinza à esquerda do [label]; as descrições dos botões
 * podem ser específicas ("Menos uma vaga") para o leitor de tela. [labelStyle] deixa o rótulo em semibold onde o campo é de formulário.
 */
@Composable
fun SaqzStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    label: String? = null,
    icon: ImageVector? = null,
    format: (Int) -> String = Int::toString,
    previous: (Int) -> Int = { saqzSteppedValue(it, -1, min, max) },
    next: (Int) -> Int = { saqzSteppedValue(it, 1, min, max) },
    decreaseDescription: String? = null,
    increaseDescription: String? = null,
    valueWidth: Dp = 32.dp,
    labelStyle: TextStyle = SaqzTheme.typography.body,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = metrics.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        if (label != null) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.grid),
            ) {
                if (icon != null) SaqzIcon(icon, tint = colors.textSecondary)
                Text(
                    text = label,
                    style = labelStyle,
                    color = colors.textPrimary,
                )
            }
        }
        SaqzStepperButton(
            icon = SaqzIcons.Minus,
            contentDescription = decreaseDescription ?: stringResource(Res.string.action_decrease),
            enabled = value > min,
            onClick = { onValueChange(previous(value)) },
        )
        Text(
            text = format(value),
            style = SaqzTheme.typography.subtitle,
            color = colors.textPrimary,
            modifier = Modifier.width(valueWidth),
            textAlign = TextAlign.Center,
        )
        SaqzStepperButton(
            icon = SaqzIcons.Plus,
            contentDescription = increaseDescription ?: stringResource(Res.string.action_increase),
            enabled = value < max,
            onClick = { onValueChange(next(value)) },
        )
    }
}

/**
 * 10h — 2 ou 3 opções curtas. O polegar **azul** desliza sob o rótulo selecionado
 * na curva enfática do design system, e o rótulo de cima vira branco.
 *
 * O trilho é **branco com borda**, não ice: o contêiner do export é
 * `background:#fff;border:1px solid #D8DDE8;border-radius:999px;padding:4px`. O trecho
 * `segBtn`/`segThumb` do bundle descreve só o botão e o polegar — quem pinta o trilho é
 * a `div` de fora.
 *
 * [selected] fora de [options] (ex. `-1`) é "sem seleção": o polegar some. [vertical] empilha as
 * opções, uma pílula por linha, para rótulos longos que não cabem lado a lado — a selecionada fica
 * azul cheia e as demais brancas com borda.
 *
 * [optionTags] dá um `testTag` por opção, na ordem de [options], para telas cujos testes
 * tocam cada uma.
 */
@Composable
fun SaqzSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionTags: List<String> = emptyList(),
    vertical: Boolean = false,
) {
    if (vertical) {
        SaqzVerticalSegmented(
            options = options,
            isSelected = { it == selected },
            onClick = onSelect,
            role = Role.Tab,
            modifier = modifier,
            optionTags = optionTags,
        )
        return
    }
    val colors = SaqzTheme.colors
    val motion = SaqzTheme.motion
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .padding(4.dp),
    ) {
        val slot = maxWidth / options.size.coerceAtLeast(1)
        val thumb by animateDpAsState(
            targetValue = slot * selected.coerceAtLeast(0),
            animationSpec = tween(motion.thumbDurationMillis, easing = motion.emphasized),
            label = "segmentedThumb",
        )
        if (selected in options.indices) {
            Box(
                modifier = Modifier
                    .offset(x = thumb)
                    .width(slot)
                    .height(maxHeight)
                    .background(colors.primary, CircleShape),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                // ponytail: o rótulo acompanha o thumb (280ms enfática) em vez do
                // `.2s ease` do export — não existe token de 200ms para texto. Se a
                // diferença aparecer, nasce um par próprio em SaqzMotionPolicy.
                val label by animateColorAsState(
                    targetValue = if (index == selected) colors.surface else colors.textPrimary,
                    animationSpec = tween(motion.thumbDurationMillis, easing = motion.emphasized),
                    label = "segmentedLabel",
                )
                Text(
                    text = option,
                    style = SaqzTheme.typography.support.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight(700),
                    ),
                    color = label,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .selectable(
                            selected = index == selected,
                            role = Role.Tab,
                            onClick = {
                                dismissKeyboard()
                                onSelect(index)
                            },
                        )
                        .padding(vertical = 9.dp)
                        .then(optionTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Seleção múltipla do segmented (dias da semana): cada opção liga e desliga sozinha e as marcadas ficam
 * azul cheias. Sem polegar deslizante, porque várias opções acesas ao mesmo tempo não têm um só lugar.
 */
@Composable
fun SaqzMultiSegmented(
    options: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionTags: List<String> = emptyList(),
) {
    val colors = SaqzTheme.colors
    val motion = SaqzTheme.motion
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val on = index in selected
            val fill by animateColorAsState(
                targetValue = if (on) colors.primary else colors.surface,
                animationSpec = tween(motion.thumbDurationMillis, easing = motion.emphasized),
                label = "multiSegmentedFill",
            )
            val label by animateColorAsState(
                targetValue = if (on) colors.surface else colors.textPrimary,
                animationSpec = tween(motion.thumbDurationMillis, easing = motion.emphasized),
                label = "multiSegmentedLabel",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(fill, CircleShape)
                    .toggleable(
                        value = on,
                        role = Role.Checkbox,
                        onValueChange = {
                            dismissKeyboard()
                            onToggle(index)
                        },
                    )
                    .then(optionTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = SaqzTheme.typography.support.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight(700),
                    ),
                    color = label,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SaqzVerticalSegmented(
    options: List<String>,
    isSelected: (Int) -> Boolean,
    onClick: (Int) -> Unit,
    role: Role,
    optionTags: List<String>,
    modifier: Modifier = Modifier,
) {
    SaqzSegmentedStack(
        count = options.size,
        isSelected = isSelected,
        onClick = onClick,
        role = role,
        optionTags = optionTags,
        enabled = true,
        containerRadius = VerticalSegmentedRadius,
        optionRadius = VerticalSegmentedRadius - 4.dp,
        optionMinHeight = 44.dp,
        optionPadding = 0.dp,
        optionAlignment = Alignment.Center,
        header = null,
        modifier = modifier,
    ) { index, _, label ->
        Text(
            text = options[index],
            style = SaqzTheme.typography.support.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight(700),
            ),
            color = label,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Escolha única em coluna com **conteúdo livre por opção**: o [SaqzSegmented] vertical para opções de mais de uma
 * linha (a posição com a contagem de vagas). Mesmo contêiner branco com um contorno só, a escolhida azul cheia, as
 * demais brancas e a mesma animação de cor; a altura acompanha o conteúdo. O raio segue o card (12) e a opção o
 * campo (10), porque a pílula de 24 do vertical de texto vira um estádio em opção de duas linhas.
 *
 * [content] recebe o índice e se a opção está escolhida, para trocar as cores do que desenha (texto branco sobre o
 * azul). [header] fica dentro do contorno, acima das opções (o título da pergunta). [enabled] falso apaga as opções a
 * 55% e ignora o toque. [selected] fora do intervalo é "nada escolhido". Semântica de rádio: o leitor de tela anuncia
 * "selecionado" na escolhida e o grupo inteiro como escolha única.
 */
@Composable
fun SaqzSegmentedColumn(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionTags: List<String> = emptyList(),
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    SaqzSegmentedStack(
        count = count,
        isSelected = { it == selected },
        onClick = onSelect,
        role = Role.RadioButton,
        optionTags = optionTags,
        enabled = enabled,
        containerRadius = SaqzTheme.metrics.cardRadius,
        optionRadius = SaqzTheme.metrics.inputRadius,
        optionMinHeight = SaqzTheme.metrics.minimumTouchTarget,
        optionPadding = 12.dp,
        optionAlignment = Alignment.TopStart,
        header = header,
        modifier = modifier,
    ) { index, on, _ -> content(index, on) }
}

// Um contorno só em volta de todas as opções, como no horizontal; as opções ficam sem borda e a escolhida em azul
// cheio. Pressionada, a opção branca escurece para o ice e encolhe na escala de toque do design system.
@Composable
private fun SaqzSegmentedStack(
    count: Int,
    isSelected: (Int) -> Boolean,
    onClick: (Int) -> Unit,
    role: Role,
    optionTags: List<String>,
    enabled: Boolean,
    containerRadius: Dp,
    optionRadius: Dp,
    optionMinHeight: Dp,
    optionPadding: Dp,
    optionAlignment: Alignment,
    header: (@Composable ColumnScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    option: @Composable (index: Int, selected: Boolean, label: Color) -> Unit,
) {
    val colors = SaqzTheme.colors
    val motion = SaqzTheme.motion
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    val shape = RoundedCornerShape(containerRadius)
    val optionShape = RoundedCornerShape(optionRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        header?.invoke(this)
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(count) { index ->
                val on = isSelected(index)
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val fill by animateColorAsState(
                    targetValue = when {
                        on -> colors.primary
                        pressed && enabled -> colors.surfaceSoft
                        else -> colors.surface
                    },
                    animationSpec = tween(motion.thumbDurationMillis, easing = motion.emphasized),
                    label = "verticalSegmentedFill",
                )
                val label by animateColorAsState(
                    targetValue = if (on) colors.surface else colors.textPrimary,
                    animationSpec = tween(motion.thumbDurationMillis, easing = motion.emphasized),
                    label = "verticalSegmentedLabel",
                )
                val scale by animateFloatAsState(
                    targetValue = saqzPressScale(pressed && enabled, motion),
                    animationSpec = tween(motion.pressDurationMillis),
                    label = "verticalSegmentedPress",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .alpha(if (enabled) 1f else DisabledOptionAlpha)
                        .clip(optionShape)
                        .background(fill)
                        .selectable(
                            selected = on,
                            enabled = enabled,
                            role = role,
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = {
                                dismissKeyboard()
                                onClick(index)
                            },
                        )
                        .then(optionTags.getOrNull(index)?.let { Modifier.testTag(it) } ?: Modifier)
                        .heightIn(min = optionMinHeight)
                        .padding(optionPadding),
                    contentAlignment = optionAlignment,
                ) {
                    option(index, on, label)
                }
            }
        }
    }
}

private val VerticalSegmentedRadius = 24.dp
private const val DisabledOptionAlpha = 0.55f

/**
 * 10h — chip de escolha (dias, filtros). Mesma linguagem do segmented: selecionado é
 * **preenchimento azul com rótulo branco** e sem borda; o não selecionado é branco com
 * borda e rótulo navy. Tinta translúcida de 8% é o chip de *status*
 * (`.saqz-chip--brand`), outro componente.
 * [compact] preserva um alvo de toque acessível, reduzindo tipografia e respiro
 * horizontal para fileiras densas como os sete dias da semana.
 */
@Composable
fun SaqzChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    val pick = {
        dismissKeyboard()
        onClick()
    }
    if (compact) {
        Box(
            modifier = modifier
                .selectable(selected = selected, role = Role.Tab, onClick = pick)
                .minimumInteractiveComponentSize(),
            contentAlignment = Alignment.Center,
        ) {
            // ponytail: o chip compacto e o IconButton medem 44 no export. Reusamos
            // `iconButtonSize` só por essa coincidência; se divergirem, nasce uma
            // métrica própria do chip sem alterar a área interativa externa de 48dp.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.iconButtonSize)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surface, CircleShape)
                    .then(if (selected) Modifier else Modifier.border(1.dp, colors.border, CircleShape))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ChoiceChipLabel(label = label, selected = selected, compact = true)
            }
        }
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(if (selected) colors.primary else colors.surface, CircleShape)
                .then(if (selected) Modifier else Modifier.border(1.dp, colors.border, CircleShape))
                .selectable(selected = selected, role = Role.Tab, onClick = pick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            ChoiceChipLabel(label = label, selected = selected, compact = false)
        }
    }
}

object SaqzChoiceChipDefaults {
    // Fluxo 2, célula 2c: `display:flex;gap:7px` na fileira dos sete dias.
    val CompactSpacing = 7.dp
}

@Composable
private fun ChoiceChipLabel(
    label: String,
    selected: Boolean,
    compact: Boolean,
) = Text(
    text = label,
    style = SaqzTheme.typography.support.copy(
        fontSize = if (compact) 13.sp else SaqzTheme.typography.support.fontSize,
        fontWeight = if (selected || compact) FontWeight(700) else FontWeight(600),
    ),
    color = if (selected) SaqzTheme.colors.onPrimary else SaqzTheme.colors.textPrimary,
    maxLines = 1,
    overflow = TextOverflow.Clip,
    softWrap = false,
    textAlign = TextAlign.Center,
)

@Preview
@Composable
private fun SaqzSwitchPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzSwitch(checked = true, onCheckedChange = {}, label = "Jogo toda semana")
        SaqzSwitch(checked = false, onCheckedChange = {}, label = "Avisar por push")
        SaqzSwitch(checked = false, onCheckedChange = {}, label = "Bloqueado", enabled = false)
    }
}

@Preview
@Composable
private fun SaqzStepperPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzStepper(value = 12, onValueChange = {}, min = 4, max = 24, label = "Vagas")
        SaqzStepper(value = 4, onValueChange = {}, min = 4, max = 24, label = "No mínimo")
        SaqzStepper(value = 24, onValueChange = {}, min = 4, max = 24, label = "No máximo")
    }
}

@Preview
@Composable
private fun SaqzSegmentedPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzSegmented(options = listOf("Masculino", "Feminino", "Misto"), selected = 2, onSelect = {})
        SaqzSegmented(options = listOf("Semanal", "Avulso"), selected = 0, onSelect = {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzChoiceChip("Todos · 26", selected = true, onClick = {})
            SaqzChoiceChip("Admins · 2", selected = false, onClick = {})
            SaqzChoiceChip("Pendentes · 2", selected = false, onClick = {})
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzChoiceChipDefaults.CompactSpacing),
        ) {
            listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEachIndexed { index, day ->
                SaqzChoiceChip(
                    label = day,
                    selected = index == 2,
                    onClick = {},
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
