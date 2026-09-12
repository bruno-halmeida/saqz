package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.access.domain.appaccess.AppAccessError
import br.com.saqz.access.domain.appaccess.OnboardingError
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState
import br.com.saqz.access.presentation.appaccess.AppOnboardingIntent
import br.com.saqz.access.presentation.appaccess.AppOnboardingState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.action_retry
import br.com.saqz.access.resources.app_onboarding_body
import br.com.saqz.access.resources.app_onboarding_continue
import br.com.saqz.access.resources.app_onboarding_auth_invalid
import br.com.saqz.access.resources.app_onboarding_auth_mismatch
import br.com.saqz.access.resources.app_onboarding_auth_provider
import br.com.saqz.access.resources.app_onboarding_complete_error
import br.com.saqz.access.resources.app_onboarding_confirm_body
import br.com.saqz.access.resources.app_onboarding_confirm_cancel
import br.com.saqz.access.resources.app_onboarding_new_link
import br.com.saqz.access.resources.app_onboarding_normal_login
import br.com.saqz.access.resources.app_onboarding_skip
import br.com.saqz.access.resources.app_onboarding_confirm_account
import br.com.saqz.access.resources.app_onboarding_title
import br.com.saqz.access.resources.app_onboarding_emphasis
import br.com.saqz.access.resources.app_onboarding_current_account
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object AppOnboardingTags {
    const val Content = "app-onboarding-content"
    const val Continue = "app-onboarding-continue"
    const val Skip = "app-onboarding-skip"
    const val Retry = "app-onboarding-retry"
    const val Cancel = "app-onboarding-cancel"
    const val Error = "app-onboarding-error"
    const val Login = "app-onboarding-login"
    const val Loading = "app-onboarding-loading"
}

@Composable
fun AppOnboardingScreen(
    state: AppOnboardingState,
    onIntent: (AppOnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
    authState: AppOnboardingAuthState = AppOnboardingAuthState.Idle,
    currentAccountName: String? = null,
    onClose: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    onConfirmAccount: () -> Unit = {},
    onNewLink: () -> Unit = {},
) {
    AccessScaffold(modifier = modifier.testTag(AppOnboardingTags.Content), spacious = true) {
        AccessBrandMark()
        Spacer(Modifier.height(AccessMetrics.blockGap))
        AccessHeader(
            title = stringResource(Res.string.app_onboarding_title),
            emphasis = stringResource(Res.string.app_onboarding_emphasis),
            subtitle = stringResource(Res.string.app_onboarding_body),
        )
        Spacer(Modifier.height(AccessMetrics.blockGap))
        when (val handoff = authState) {
            is AppOnboardingAuthState.NeedsAccountConfirmation -> {
                SaqzInlineAlert(
                    text = stringResource(
                        Res.string.app_onboarding_confirm_body,
                        currentAccountName ?: stringResource(Res.string.app_onboarding_current_account),
                    ),
                    tone = SaqzInlineAlertTone.Warning,
                    modifier = Modifier.testTag(AppOnboardingTags.Error),
                )
                Spacer(Modifier.height(AccessMetrics.fieldGap))
                SaqzButton(
                    label = stringResource(Res.string.app_onboarding_confirm_account),
                    onClick = onConfirmAccount,
                    modifier = Modifier.fillMaxWidth().testTag(AppOnboardingTags.Continue),
                    fullWidth = true,
                )
                Spacer(Modifier.height(AccessMetrics.fieldGap))
                SaqzButton(
                    label = stringResource(Res.string.app_onboarding_confirm_cancel),
                    onClick = onClose,
                    variant = SaqzButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth().testTag(AppOnboardingTags.Cancel),
                    fullWidth = true,
                )
            }
            is AppOnboardingAuthState.Failed -> {
                SaqzInlineAlert(
                    text = stringResource(handoff.error.messageResource()),
                    tone = SaqzInlineAlertTone.Error,
                    modifier = Modifier.testTag(AppOnboardingTags.Error),
                )
                Spacer(Modifier.height(AccessMetrics.fieldGap))
                SaqzButton(
                    label = stringResource(Res.string.app_onboarding_new_link),
                    onClick = onNewLink,
                    modifier = Modifier.fillMaxWidth().testTag(AppOnboardingTags.Retry),
                    fullWidth = true,
                )
                Spacer(Modifier.height(AccessMetrics.fieldGap))
                SaqzButton(
                    label = stringResource(Res.string.app_onboarding_normal_login),
                    onClick = onOpenLogin,
                    variant = SaqzButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth().testTag(AppOnboardingTags.Login),
                    fullWidth = true,
                )
            }
            AppOnboardingAuthState.Redeeming,
            AppOnboardingAuthState.WaitingForSessionResolution,
            AppOnboardingAuthState.SigningIn,
            -> SaqzSpinner(Modifier.testTag(AppOnboardingTags.Loading))
            else -> when {
                state.isLoading -> SaqzSpinner(Modifier.testTag(AppOnboardingTags.Loading))
                state.failure != null -> {
                    SaqzInlineAlert(
                        text = stringResource(state.failure.messageResource()),
                        tone = SaqzInlineAlertTone.Error,
                        modifier = Modifier.testTag(AppOnboardingTags.Error),
                    )
                    Spacer(Modifier.height(AccessMetrics.fieldGap))
                    SaqzButton(
                        label = stringResource(Res.string.action_retry),
                        onClick = { onIntent(AppOnboardingIntent.Retry) },
                        modifier = Modifier.testTag(AppOnboardingTags.Retry),
                        fullWidth = true,
                    )
                }
                state.completed -> Unit
                else -> {
                    SaqzButton(
                        label = stringResource(Res.string.app_onboarding_continue),
                        onClick = { onIntent(AppOnboardingIntent.Continue) },
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
}

private fun AppAccessError.messageResource() = when (this) {
    AppAccessError.CodeInvalid -> Res.string.app_onboarding_auth_invalid
    AppAccessError.ProviderUnavailable -> Res.string.app_onboarding_auth_provider
    AppAccessError.IdentityMismatch -> Res.string.app_onboarding_auth_mismatch
    is AppAccessError.Data -> Res.string.app_onboarding_auth_provider
}

private fun OnboardingError.messageResource() = when (this) {
    OnboardingError.AccountNotFound,
    OnboardingError.AccountSuspended,
    is OnboardingError.Data,
    -> Res.string.app_onboarding_complete_error
}

@Preview(name = "Primeiro acesso — pronto", widthDp = 390, heightDp = 844)
@Composable
private fun AppOnboardingPreview() = SaqzTheme {
    AppOnboardingScreen(
        state = AppOnboardingState(),
        onIntent = {},
    )
}

@Preview(name = "Primeiro acesso — confirmação", widthDp = 390, heightDp = 844)
@Composable
private fun AppOnboardingConfirmationPreview() = SaqzTheme {
    AppOnboardingScreen(
        state = AppOnboardingState(),
        onIntent = {},
        authState = AppOnboardingAuthState.NeedsAccountConfirmation("target-owner"),
        currentAccountName = "Ana",
    )
}

@Preview(name = "Primeiro acesso — erro", widthDp = 390, heightDp = 844)
@Composable
private fun AppOnboardingErrorPreview() = SaqzTheme {
    AppOnboardingScreen(
        state = AppOnboardingState(),
        onIntent = {},
        authState = AppOnboardingAuthState.Failed(AppAccessError.CodeInvalid),
    )
}
