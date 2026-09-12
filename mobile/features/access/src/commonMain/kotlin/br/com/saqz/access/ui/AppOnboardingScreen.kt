package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.access.presentation.appaccess.AppOnboardingIntent
import br.com.saqz.access.presentation.appaccess.AppOnboardingState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.action_retry
import br.com.saqz.access.resources.app_onboarding_body
import br.com.saqz.access.resources.app_onboarding_continue
import br.com.saqz.access.resources.app_onboarding_skip
import br.com.saqz.access.resources.app_onboarding_title
import br.com.saqz.access.resources.app_onboarding_confirm_account
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzSpinner
import org.jetbrains.compose.resources.stringResource

internal object AppOnboardingTags {
    const val Content = "app-onboarding-content"
    const val Continue = "app-onboarding-continue"
    const val Skip = "app-onboarding-skip"
    const val Retry = "app-onboarding-retry"
}

@Composable
fun AppOnboardingScreen(
    state: AppOnboardingState,
    onIntent: (AppOnboardingIntent) -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier,
    accountConfirmationRequired: Boolean = false,
    onConfirmAccount: () -> Unit = {},
) {
    AccessScaffold(modifier = modifier.testTag(AppOnboardingTags.Content), spacious = true) {
        AccessBrandMark()
        Spacer(Modifier.height(AccessMetrics.blockGap))
        AccessHeader(
            title = stringResource(Res.string.app_onboarding_title),
            emphasis = "junto.",
            subtitle = stringResource(Res.string.app_onboarding_body),
        )
        Spacer(Modifier.height(AccessMetrics.blockGap))
        when {
            accountConfirmationRequired -> SaqzButton(
                label = stringResource(Res.string.app_onboarding_confirm_account),
                onClick = onConfirmAccount,
                modifier = Modifier.fillMaxWidth().testTag(AppOnboardingTags.Continue),
                fullWidth = true,
            )
            state.isLoading -> SaqzSpinner()
            state.failure != null -> SaqzButton(
                label = stringResource(Res.string.action_retry),
                onClick = { onIntent(AppOnboardingIntent.Retry) },
                modifier = Modifier.testTag(AppOnboardingTags.Retry),
                fullWidth = true,
            )
            state.completed -> SaqzButton(
                label = stringResource(Res.string.app_onboarding_continue),
                onClick = onCreateGroup,
                modifier = Modifier.fillMaxWidth().testTag(AppOnboardingTags.Continue),
                fullWidth = true,
            )
            else -> {
                SaqzButton(
                    label = stringResource(Res.string.app_onboarding_continue),
                    onClick = { onIntent(AppOnboardingIntent.Skip) },
                    modifier = Modifier.fillMaxWidth().testTag(AppOnboardingTags.Continue),
                    fullWidth = true,
                )
                Spacer(Modifier.height(AccessMetrics.fieldGap))
                SaqzButton(
                    label = stringResource(Res.string.app_onboarding_skip),
                    onClick = { onIntent(AppOnboardingIntent.Skip) },
                    variant = SaqzButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth().testTag(AppOnboardingTags.Skip),
                    fullWidth = true,
                )
            }
        }
    }
}
