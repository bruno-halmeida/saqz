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
import br.com.saqz.groups.presentation.details.GroupOnboarding
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.onboarding_create_title
import br.com.saqz.groups.resources.onboarding_create_body
import br.com.saqz.groups.resources.onboarding_create_action
import br.com.saqz.groups.resources.onboarding_invite_title
import br.com.saqz.groups.resources.onboarding_invite_body
import br.com.saqz.groups.resources.onboarding_invite_action
import br.com.saqz.groups.resources.onboarding_responses_action
import br.com.saqz.groups.resources.onboarding_finance_title
import br.com.saqz.groups.resources.onboarding_finance_body
import br.com.saqz.groups.resources.onboarding_finance_action
import org.jetbrains.compose.resources.stringResource

internal object GroupOnboardingTags {
    const val Card = "group-onboarding-card"
    const val Action = "group-onboarding-action"
    const val Responses = "group-onboarding-responses"
}

@Composable
internal fun GroupOnboardingCard(guide: GroupOnboarding, onIntent: (GroupDetailsIntent) -> Unit) {
    val (title, body, action) = when (guide) {
        GroupOnboarding.CreateGame -> Triple(
            Res.string.onboarding_create_title, Res.string.onboarding_create_body, Res.string.onboarding_create_action,
        )
        is GroupOnboarding.InviteAthletes -> Triple(
            Res.string.onboarding_invite_title, Res.string.onboarding_invite_body, Res.string.onboarding_invite_action,
        )
        is GroupOnboarding.ReviewFinances -> Triple(
            Res.string.onboarding_finance_title, Res.string.onboarding_finance_body, Res.string.onboarding_finance_action,
        )
    }
    SaqzCard(modifier = Modifier.testTag(GroupOnboardingTags.Card)) {
        Text(stringResource(title), style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
        Text(stringResource(body), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        SaqzButton(
            stringResource(action), { onIntent(GroupDetailsIntent.OnboardingAction) }, fullWidth = true,
            modifier = Modifier.testTag(GroupOnboardingTags.Action),
        )
        if (guide is GroupOnboarding.InviteAthletes) {
            SaqzButton(
                stringResource(Res.string.onboarding_responses_action), { onIntent(GroupDetailsIntent.ViewGame) },
                variant = SaqzButtonVariant.Secondary, fullWidth = true,
                modifier = Modifier.testTag(GroupOnboardingTags.Responses),
            )
        }
    }
}

@Preview
@Composable
private fun FirstGameGuidePreview() = SaqzTheme { GroupOnboardingCard(GroupOnboarding.CreateGame, {}) }
