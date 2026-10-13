package br.com.saqz.subscriptions.presentation.trial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import br.com.saqz.designsystem.SaqzSpinner
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import br.com.saqz.subscriptions.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object TrialEntryTags {
    const val Code = "trial-entry-code"
    const val Apply = "trial-entry-apply"
    const val Error = "trial-entry-error"
    const val Refresh = "trial-entry-refresh"
}

@Composable
fun TrialEntryRoot(onBack: () -> Unit, onSubscribe: () -> Unit,
    viewModel: TrialEntryViewModel = koinViewModel(),
    purchasesAvailable: Boolean = br.com.saqz.domain.StoreLaunchPolicy.purchases,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.ready) {
        content()
    } else {
        TrialEntryScreen(state, viewModel::onIntent, onBack, onSubscribe, purchasesAvailable = purchasesAvailable)
    }
}

@Composable
fun TrialEntryScreen(state: TrialEntryState, onIntent: (TrialEntryIntent) -> Unit, onBack: () -> Unit,
    onSubscribe: () -> Unit, modifier: Modifier = Modifier,
    purchasesAvailable: Boolean = br.com.saqz.domain.StoreLaunchPolicy.purchases,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(title = stringResource(Res.string.trial_entry_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
        ) {
            if (state.access?.canCreateGroup != true && !state.loading && state.failure == null) {
                TrialUnavailable()
            }
            if (state.loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.blockGap), verticalAlignment = Alignment.CenterVertically) {
                    SaqzSpinner(size = metrics.sectionGap)
                    Text(stringResource(Res.string.trial_entry_loading), style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary)
                }
            }
            state.failure?.let {
                SaqzCard(modifier = Modifier.testTag(TrialEntryTags.Error)) {
                    SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.errorForeground)
                    Text(stringResource(Res.string.trial_entry_load_error), style = SaqzTheme.typography.body,
                        color = SaqzTheme.colors.textPrimary)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(SaqzTheme.colors.surface).padding(metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
            if (purchasesAvailable && !state.loading && state.failure == null) {
                SaqzButton(stringResource(Res.string.trial_entry_plans), onSubscribe, fullWidth = true)
            }
            if (state.failure != null || (!state.loading && state.access?.canCreateGroup != true)) {
                SaqzButton(stringResource(if (state.failure != null) Res.string.trial_entry_retry else Res.string.trial_entry_refresh),
                    { onIntent(TrialEntryIntent.Refresh) },
                    enabled = !state.loading, fullWidth = true, variant = SaqzButtonVariant.Secondary,
                    modifier = Modifier.testTag(TrialEntryTags.Refresh))
            }
        }
    }
}

@Composable
private fun TrialUnavailable() {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        SaqzIcon(SaqzIcons.Users, tint = SaqzTheme.colors.primary, size = SaqzTheme.metrics.iconButtonSize)
        Text(stringResource(Res.string.trial_entry_setup_title), style = SaqzTheme.typography.title,
            color = SaqzTheme.colors.textPrimary)
        Text(stringResource(Res.string.trial_entry_setup_help), style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary)
    }
}

@Preview
@Composable
private fun TrialEntryPreview() = SaqzTheme {
    TrialEntryScreen(TrialEntryState(loading = false, access = TrialAccess(
        TrialStatus.Ineligible, null, null, "2026-09-15T12:00:00Z", false, false, 3, null, true, null,
    )), {}, {}, {})
}
