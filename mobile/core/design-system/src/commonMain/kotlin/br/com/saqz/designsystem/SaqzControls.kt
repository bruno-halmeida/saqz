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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        SaqzIconButton(
            onClick = { onValueChange(saqzSteppedValue(value, -1, min, max)) },
            contentDescription = stringResource(Res.string.action_decrease),
            enabled = value > min,
            soft = true,
        ) {
            SaqzIcon(SaqzIcons.Minus, tint = if (value > min) colors.textPrimary else colors.disabledForeground)
        }
        Text(
            text = value.toString(),
            style = SaqzTheme.typography.subtitle,
            color = colors.textPrimary,
            modifier = Modifier.width(32.dp),
        )
        SaqzIconButton(
            onClick = { onValueChange(saqzSteppedValue(value, 1, min, max)) },
            contentDescription = stringResource(Res.string.action_increase),
            enabled = value < max,
            soft = true,
        ) {
            SaqzIcon(SaqzIcons.Plus, tint = if (value < max) colors.textPrimary else colors.disabledForeground)
        }
    }
}

/**
 * 10h — 2 ou 3 opções curtas. O polegar **azul** desliza sob o rótulo selecionado
 * na curva enfática do design system, e o rótulo de cima vira branco.
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
            .background(colors.surfaceSoft, CircleShape)
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
 * 10h — chip de escolha (dias, filtros). Selecionado pinta no tom da marca.
 */
@Composable
fun SaqzChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Text(
        text = label,
        style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
        color = if (selected) colors.primary else colors.textSecondary,
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) colors.primary.copy(alpha = 0.08f) else colors.surface, CircleShape)
            .border(1.dp, if (selected) colors.primary else colors.border, CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
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
    }
}

@Preview
@Composable
private fun SaqzSegmentedPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzSegmented(options = listOf("Masculino", "Feminino", "Misto"), selected = 2, onSelect = {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzChoiceChip("Todos · 26", selected = true, onClick = {})
            SaqzChoiceChip("Admins · 2", selected = false, onClick = {})
            SaqzChoiceChip("Pendentes · 2", selected = false, onClick = {})
        }
    }
}
