package br.com.saqz.groups.presentation.ui.details

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.onboarding_athlete_title
import br.com.saqz.groups.resources.onboarding_athlete_body
import br.com.saqz.groups.resources.onboarding_athlete_share
import br.com.saqz.groups.resources.onboarding_athlete_dismiss
import br.com.saqz.groups.resources.onboarding_athlete_share_failed
import org.jetbrains.compose.resources.stringResource

internal object AthleteOnboardingTags {
    const val Card = "athlete-onboarding-card"
    const val Share = "athlete-onboarding-share"
    const val Dismiss = "athlete-onboarding-dismiss"
}

@Composable
internal fun AthleteOnboardingCard(shareFailed: Boolean, onIntent: (GroupDetailsIntent) -> Unit) {
    SaqzCard(modifier = Modifier.testTag(AthleteOnboardingTags.Card)) {
        Text(stringResource(Res.string.onboarding_athlete_title),
            style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
        Text(stringResource(Res.string.onboarding_athlete_body),
            style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        SaqzButton(stringResource(Res.string.onboarding_athlete_share), { onIntent(GroupDetailsIntent.ShareSaqz) },
            variant = SaqzButtonVariant.Secondary, fullWidth = true, modifier = Modifier.testTag(AthleteOnboardingTags.Share))
        SaqzButton(stringResource(Res.string.onboarding_athlete_dismiss), { onIntent(GroupDetailsIntent.DismissAthleteIntro) },
            variant = SaqzButtonVariant.Ghost, fullWidth = true, modifier = Modifier.testTag(AthleteOnboardingTags.Dismiss))
        if (shareFailed) Text(stringResource(Res.string.onboarding_athlete_share_failed),
            style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
    }
}

@Preview
@Composable
private fun AthleteOnboardingPreview() = SaqzTheme { AthleteOnboardingCard(false, {}) }
