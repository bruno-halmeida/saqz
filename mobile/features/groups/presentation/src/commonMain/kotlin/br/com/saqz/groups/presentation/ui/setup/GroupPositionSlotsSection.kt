package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.setup.GroupPositionDefaults
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.components.GroupFormCard
import br.com.saqz.groups.presentation.ui.pluralLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.position_slots_hint
import br.com.saqz.groups.resources.position_slots_suggest
import br.com.saqz.groups.resources.position_slots_suggest_failed
import br.com.saqz.groups.resources.position_slots_over
import br.com.saqz.groups.resources.position_slots_short
import br.com.saqz.groups.resources.position_slots_title
import br.com.saqz.groups.resources.position_slots_total_ok
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GroupPositionSlotsTags {
    const val Section = "group-position-slots"
    const val Suggest = "group-position-slots-suggest"
    const val Total = "group-position-slots-total"
    fun position(position: AthletePosition) = "group-position-slots-${position.name.lowercase()}"
}

/**
 * DEB-102: vagas por posição do 5-1, logo abaixo do total de jogadores. A soma tem de bater com o total: a diferença
 * ("faltam 2" / "sobram 2") aparece aqui e o Salvar fica bloqueado enquanto não fechar.
 */
@Composable
internal fun GroupPositionSlotsSection(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupFormCard(
        title = stringResource(Res.string.position_slots_title),
        hint = stringResource(Res.string.position_slots_hint),
        modifier = modifier.testTag(GroupPositionSlotsTags.Section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            SaqzButton(
                label = stringResource(Res.string.position_slots_suggest),
                onClick = { onIntent(GroupSetupIntent.SuggestPositionSlots) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                enabled = !state.isSuggestingPositions,
                modifier = Modifier.testTag(GroupPositionSlotsTags.Suggest),
            )
            if (state.positionSuggestionFailed) {
                Text(
                    text = stringResource(Res.string.position_slots_suggest_failed),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.errorForeground,
                )
            }
            AthletePosition.entries.forEach { position ->
                SaqzStepper(
                    value = state.positionSlots[position] ?: 0,
                    onValueChange = { onIntent(GroupSetupIntent.UpdatePositionSlots(position, it)) },
                    min = 0,
                    max = GroupPositionDefaults.MaxPerPosition,
                    label = position.pluralLabel().replaceFirstChar(Char::uppercase),
                    modifier = Modifier.testTag(GroupPositionSlotsTags.position(position)),
                )
            }
            val difference = state.positionsDifference
            Text(
                text = when {
                    difference < 0 -> pluralStringResource(Res.plurals.position_slots_short, -difference, -difference)
                    difference > 0 -> pluralStringResource(Res.plurals.position_slots_over, difference, difference)
                    else -> stringResource(Res.string.position_slots_total_ok, state.positionsTarget)
                },
                style = SaqzTheme.typography.support,
                color = if (difference != 0) SaqzTheme.colors.errorForeground else SaqzTheme.colors.textSecondary,
                modifier = Modifier.testTag(GroupPositionSlotsTags.Total),
            )
        }
    }
}
