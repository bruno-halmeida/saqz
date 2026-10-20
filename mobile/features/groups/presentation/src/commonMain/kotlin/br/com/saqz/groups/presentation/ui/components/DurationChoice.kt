package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.duration.CustomDuration
import br.com.saqz.groups.presentation.duration.DurationDraft
import br.com.saqz.groups.presentation.ui.durationLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.duration_choice_error
import br.com.saqz.groups.resources.duration_field_equivalent
import br.com.saqz.groups.resources.duration_field_label
import br.com.saqz.groups.resources.duration_field_placeholder
import br.com.saqz.groups.resources.duration_picker_other
import br.com.saqz.groups.resources.duration_picker_summary
import org.jetbrains.compose.resources.stringResource

internal object DurationChoiceTags {
    const val Other = "duration-choice-other"
    const val Field = "duration-choice-field"
}

/**
 * Escolha de duração dos três usos (editor de jogo, duração padrão do grupo e horários regulares): chips 1h a 2h30 e
 * "Outros". **A forma de digitar o "Outros" mora só aqui**: hoje um campo de horas cheias (3 a 8) com teclado
 * numérico, que mostra o equivalente em minutos; os usos só passam o [draft] e recebem os toques.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DurationChoice(
    draft: DurationDraft,
    onSelectPreset: (Int) -> Unit,
    onSelectOther: () -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasError: Boolean = false,
) {
    val other = CustomDuration.isOther(draft)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        ) {
            CustomDuration.Presets.forEach { value ->
                SaqzChoiceChip(
                    label = durationLabel(value),
                    selected = !other && value == draft.minutes,
                    onClick = { onSelectPreset(value) },
                )
            }
            SaqzChoiceChip(
                label = stringResource(Res.string.duration_picker_other),
                selected = other,
                onClick = onSelectOther,
                modifier = Modifier.testTag(DurationChoiceTags.Other),
            )
        }
        if (other) DurationHoursField(draft, onTextChange, hasError)
    }
}

@Composable
private fun DurationHoursField(draft: DurationDraft, onTextChange: (String) -> Unit, hasError: Boolean) {
    val text = CustomDuration.fieldText(draft)
    val hours = CustomDuration.hours(text)
    // Hora cheia digitada: "3 h = 180 min". Valor salvo que não é hora cheia (195): "Duração: 3h15".
    val helper = when {
        hours != null -> stringResource(Res.string.duration_field_equivalent, hours, hours * MINUTES_PER_HOUR)
        draft.text == null && draft.minutes > 0 ->
            stringResource(Res.string.duration_picker_summary, durationLabel(draft.minutes))
        else -> null
    }
    SaqzInput(
        value = text,
        onValueChange = onTextChange,
        label = stringResource(Res.string.duration_field_label),
        placeholder = stringResource(Res.string.duration_field_placeholder),
        helperText = helper,
        errorText = if (hasError) stringResource(Res.string.duration_choice_error) else null,
        keyboardType = KeyboardType.Number,
        modifier = Modifier.testTag(DurationChoiceTags.Field),
    )
}

private const val MINUTES_PER_HOUR = 60
