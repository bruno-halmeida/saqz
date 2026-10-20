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
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.components.GroupFormCard
import br.com.saqz.groups.presentation.ui.pluralLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.position_slots_error_total
import br.com.saqz.groups.resources.position_slots_hint
import br.com.saqz.groups.resources.position_slots_suggest
import br.com.saqz.groups.resources.position_slots_suggest_failed
import br.com.saqz.groups.resources.position_slots_teams_label
import br.com.saqz.groups.resources.position_slots_title
import br.com.saqz.groups.resources.position_slots_total
import br.com.saqz.groups.resources.position_slots_total_empty
import org.jetbrains.compose.resources.stringResource

internal object GroupPositionSlotsTags {
    const val Section = "group-position-slots"
    const val Suggest = "group-position-slots-suggest"
    const val Teams = "group-position-slots-teams"
    const val Total = "group-position-slots-total"
    fun position(position: AthletePosition) = "group-position-slots-${position.name.lowercase()}"
}

/**
 * DEB-102: vagas por posição do 5-1. Em grupo com alguma posição preenchida este card **substitui** o campo do
 * total de jogadores (que passa a ser a soma); com todas vazias o total segue valendo e o card só oferece a sugestão.
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
            SaqzStepper(
                value = state.positionTeams,
                onValueChange = { onIntent(GroupSetupIntent.UpdatePositionTeams(it)) },
                min = 1,
                max = GroupPositionDefaults.MaxTeams,
                label = stringResource(Res.string.position_slots_teams_label),
                modifier = Modifier.testTag(GroupPositionSlotsTags.Teams),
            )
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
            val hasError = GroupSetupError.PositionSlotsTotalOutOfRange in state.errors
            Text(
                text = when {
                    hasError -> stringResource(Res.string.position_slots_error_total)
                    state.usesPositionSlots -> stringResource(Res.string.position_slots_total, state.positionsTotal)
                    else -> stringResource(Res.string.position_slots_total_empty)
                },
                style = SaqzTheme.typography.support,
                color = if (hasError) SaqzTheme.colors.errorForeground else SaqzTheme.colors.textSecondary,
                modifier = Modifier.testTag(GroupPositionSlotsTags.Total),
            )
        }
    }
}
