package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.duration.CustomDuration
import br.com.saqz.groups.presentation.ui.durationLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.duration_stepper_decrease
import br.com.saqz.groups.resources.duration_stepper_hint
import br.com.saqz.groups.resources.duration_stepper_increase
import org.jetbrains.compose.resources.stringResource

/**
 * Escolha de duração dos três usos (editor de jogo, duração padrão do grupo e horários regulares): um contador
 * [–] 2h [+] que anda na grade de [CustomDuration]. **A grade mora só em [CustomDuration]**; os usos só passam os
 * minutos e recebem o vizinho. Valor salvo fora da grade aparece como "3h15" e o – / + vai para o vizinho da grade.
 */
@Composable
internal fun DurationStepper(
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    hasError: Boolean = false,
) {
    val text = durationLabel(minutes)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        SaqzStepper(
            value = minutes,
            onValueChange = onChange,
            min = CustomDuration.MIN_MINUTES,
            max = CustomDuration.MAX_MINUTES,
            label = label,
            icon = icon,
            format = { text },
            previous = CustomDuration::previous,
            next = CustomDuration::next,
            decreaseDescription = stringResource(Res.string.duration_stepper_decrease),
            increaseDescription = stringResource(Res.string.duration_stepper_increase),
            valueWidth = 56.dp,
        )
        Text(
            text = stringResource(Res.string.duration_stepper_hint),
            style = SaqzTheme.typography.support,
            color = if (hasError) SaqzTheme.colors.errorForeground else SaqzTheme.colors.textSecondary,
        )
    }
}
