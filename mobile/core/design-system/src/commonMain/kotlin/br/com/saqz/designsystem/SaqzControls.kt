package br.com.saqz.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
        onValueChange = onCheckedChange,
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
                onClick = onClick,
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
 */
@Composable
fun SaqzStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    label: String? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = metrics.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = SaqzTheme.typography.body,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        SaqzStepperButton(
            icon = SaqzIcons.Minus,
            contentDescription = stringResource(Res.string.action_decrease),
            enabled = value > min,
            onClick = { onValueChange(saqzSteppedValue(value, -1, min, max)) },
        )
        Text(
            text = value.toString(),
            style = SaqzTheme.typography.subtitle,
            color = colors.textPrimary,
            modifier = Modifier.width(32.dp),
        )
        SaqzStepperButton(
            icon = SaqzIcons.Plus,
            contentDescription = stringResource(Res.string.action_increase),
            enabled = value < max,
            onClick = { onValueChange(saqzSteppedValue(value, 1, min, max)) },
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
 */
@Composable
fun SaqzSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val motion = SaqzTheme.motion
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
            targetValue = slot * selected,
            animationSpec = tween(motion.thumbDurationMillis, easing = motion.emphasized),
            label = "segmentedThumb",
        )
        Box(
            modifier = Modifier
                .offset(x = thumb)
                .width(slot)
                .height(maxHeight)
                .background(colors.primary, CircleShape),
        )
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
                            onClick = { onSelect(index) },
                        )
                        .padding(vertical = 9.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

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
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surface, CircleShape)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.border, CircleShape))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .then(if (compact) Modifier.heightIn(min = metrics.minimumTouchTarget) else Modifier)
            .padding(
                horizontal = if (compact) 4.dp else 14.dp,
                vertical = if (compact) 0.dp else 9.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.support.copy(
                fontSize = if (compact) 13.sp else SaqzTheme.typography.support.fontSize,
                fontWeight = if (selected || compact) FontWeight(700) else FontWeight(600),
            ),
            color = if (selected) colors.onPrimary else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
