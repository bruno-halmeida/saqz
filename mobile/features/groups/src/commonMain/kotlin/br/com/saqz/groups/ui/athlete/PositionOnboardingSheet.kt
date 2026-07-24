package br.com.saqz.groups.ui.athlete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.component.SaqzBottomSheet
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.athlete.PositionOnboardingIntent
import br.com.saqz.groups.presentation.athlete.PositionOnboardingState
import br.com.saqz.groups.presentation.athlete.PositionOnboardingViewModel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.athlete_position_body
import br.com.saqz.groups.resources.athlete_position_central
import br.com.saqz.groups.resources.athlete_position_error
import br.com.saqz.groups.resources.athlete_position_levantador
import br.com.saqz.groups.resources.athlete_position_libero
import br.com.saqz.groups.resources.athlete_position_oposto
import br.com.saqz.groups.resources.athlete_position_ponta
import br.com.saqz.groups.resources.athlete_position_skip
import br.com.saqz.groups.resources.athlete_position_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

object PositionOnboardingTags {
    const val Sheet = "position-onboarding-sheet"
    const val Skip = "position-onboarding-skip"
    fun option(position: AthletePosition) = "position-option-${position.name}"
}

fun AthletePosition.labelRes(): StringResource = when (this) {
    AthletePosition.LIBERO -> Res.string.athlete_position_libero
    AthletePosition.PONTA -> Res.string.athlete_position_ponta
    AthletePosition.CENTRAL -> Res.string.athlete_position_central
    AthletePosition.OPOSTO -> Res.string.athlete_position_oposto
    AthletePosition.LEVANTADOR -> Res.string.athlete_position_levantador
}

@Composable
fun PositionOnboardingHost(viewModel: PositionOnboardingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.visible) PositionOnboardingSheet(state, viewModel::onIntent)
}

@Composable
fun PositionOnboardingSheet(
    state: PositionOnboardingState,
    onIntent: (PositionOnboardingIntent) -> Unit,
) = SaqzBottomSheet(
    title = stringResource(Res.string.athlete_position_title),
    onCloseRequest = { onIntent(PositionOnboardingIntent.Skip) },
    primaryAction = {
        SaqzButton(
            label = stringResource(Res.string.athlete_position_skip),
            onClick = { onIntent(PositionOnboardingIntent.Skip) },
            variant = SaqzButtonVariant.Secondary,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().testTag(PositionOnboardingTags.Skip),
        )
    },
    modifier = Modifier.testTag(PositionOnboardingTags.Sheet),
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.athlete_position_body),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
        )
        if (state.failed) {
            Text(
                stringResource(Res.string.athlete_position_error),
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.errorForeground,
            )
        }
        AthletePosition.entries.forEach { position ->
            SaqzButton(
                label = stringResource(position.labelRes()),
                onClick = { onIntent(PositionOnboardingIntent.Choose(position)) },
                variant = SaqzButtonVariant.Secondary,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth().testTag(PositionOnboardingTags.option(position)),
            )
        }
    }
}
