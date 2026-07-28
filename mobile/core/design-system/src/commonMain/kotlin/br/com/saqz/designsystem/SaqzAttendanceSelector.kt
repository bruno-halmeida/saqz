package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.attendance_going
import br.com.saqz.designsystem.resources.attendance_maybe
import br.com.saqz.designsystem.resources.attendance_out
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

enum class SaqzAttendance { Going, Maybe, Out }

// "Talvez" é um dos três lugares em que o lime aparece (10a); os outros dois são o
// indicador do nav e o ponto de aviso.
internal fun SaqzColorTokens.attendanceFill(intent: SaqzAttendance): Color = when (intent) {
    SaqzAttendance.Going -> primary
    SaqzAttendance.Maybe -> accent
    SaqzAttendance.Out -> errorForeground
}

// Sobre o lime o texto continua navy — branco não passa contraste nele. É a única
// das três intenções em que a cor de conteúdo muda.
internal fun SaqzColorTokens.attendanceOnFill(intent: SaqzAttendance): Color =
    if (intent == SaqzAttendance.Maybe) textPrimary else onPrimary

/**
 * 10g — os três estados de presença, um alvo por opção. A resposta é otimista:
 * quem chama troca [value] na hora e sincroniza depois.
 */
@Composable
fun SaqzAttendanceSelector(
    value: SaqzAttendance?,
    onSelect: (SaqzAttendance) -> Unit,
    modifier: Modifier = Modifier,
    options: List<SaqzAttendance> = SaqzAttendance.entries.toList(),
    enabled: Boolean = true,
) {
    val labels = mapOf(
        SaqzAttendance.Going to stringResource(Res.string.attendance_going),
        SaqzAttendance.Maybe to stringResource(Res.string.attendance_maybe),
        SaqzAttendance.Out to stringResource(Res.string.attendance_out),
    )
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid * 2),
    ) {
        options.forEach { intent ->
            AttendanceOption(
                label = labels.getValue(intent),
                fill = SaqzTheme.colors.attendanceFill(intent),
                onFill = SaqzTheme.colors.attendanceOnFill(intent),
                selected = value == intent,
                enabled = enabled,
                onSelect = { onSelect(intent) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AttendanceOption(
    label: String,
    fill: Color,
    onFill: Color,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Text(
        text = label,
        style = SaqzTheme.typography.label,
        color = when {
            !enabled -> colors.disabledForeground
            selected -> onFill
            else -> colors.textPrimary
        },
        textAlign = TextAlign.Center,
        modifier = modifier
            .heightIn(min = SaqzTheme.metrics.minimumTouchTarget)
            .clip(CircleShape)
            .background(if (selected) fill else colors.surface, CircleShape)
            .border(1.dp, if (selected) fill else colors.border, CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 14.dp),
    )
}

@Preview
@Composable
private fun SaqzAttendanceSelectorPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzAttendanceSelector(value = null, onSelect = {})
        SaqzAttendanceSelector(value = SaqzAttendance.Going, onSelect = {})
        SaqzAttendanceSelector(value = SaqzAttendance.Maybe, onSelect = {})
        SaqzAttendanceSelector(value = SaqzAttendance.Out, onSelect = {})
    }
}
