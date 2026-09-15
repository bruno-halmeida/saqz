package br.com.saqz.subscriptions.presentation.trial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import br.com.saqz.designsystem.SaqzSpinner
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    const val Continue = "trial-entry-continue"
    const val Error = "trial-entry-error"
    const val Refresh = "trial-entry-refresh"
}

@Composable
fun TrialEntryRoot(onBack: () -> Unit, onSubscribe: () -> Unit,
    viewModel: TrialEntryViewModel = koinViewModel(), content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.ready) content() else TrialEntryScreen(state, viewModel::onIntent, onBack, onSubscribe)
}

@Composable
fun TrialEntryScreen(state: TrialEntryState, onIntent: (TrialEntryIntent) -> Unit, onBack: () -> Unit,
    onSubscribe: () -> Unit, modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(title = stringResource(Res.string.trial_entry_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
        ) {
            if (state.access?.canCreateGroup == true) {
                TrialHero(state.access.trialDays)
                TrialBenefits(state.access)
                SaqzCard(tone = SaqzCardTone.Soft) {
                    Text(stringResource(Res.string.trial_entry_after_title), style = SaqzTheme.typography.label,
                        color = SaqzTheme.colors.textPrimary)
                    Text(stringResource(Res.string.trial_entry_after), style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary)
                }
            } else if (!state.loading && state.failure == null) {
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
            if (state.access?.canCreateGroup == true && state.failure == null) {
                SaqzButton(stringResource(Res.string.trial_entry_continue), { onIntent(TrialEntryIntent.Continue) },
                    enabled = state.canContinue, fullWidth = true, modifier = Modifier.testTag(TrialEntryTags.Continue))
            } else if (!state.loading && state.failure == null) {
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
private fun TrialHero(days: Int) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(Modifier.fillMaxWidth().background(colors.primary, RoundedCornerShape(metrics.blockRadius))
        .padding(metrics.sectionGap), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.trial_entry_eyebrow), style = SaqzTheme.typography.eyebrow, color = colors.accent)
            Box(Modifier.background(colors.onPrimary.copy(alpha = 0.12f), CircleShape).padding(metrics.blockGap)) {
                SaqzIcon(SaqzIcons.Calendar, tint = colors.onPrimary)
            }
        }
        Text(stringResource(Res.string.trial_entry_available, days), style = SaqzTheme.typography.headline, color = colors.onPrimary)
        Text(stringResource(Res.string.trial_entry_intro), style = SaqzTheme.typography.body, color = colors.onPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.grid), verticalAlignment = Alignment.CenterVertically) {
            SaqzIcon(SaqzIcons.Clock, tint = colors.accent, size = metrics.horizontalPadding)
            Text(stringResource(Res.string.trial_entry_starts), style = SaqzTheme.typography.support, color = colors.onPrimary)
        }
    }
}

@Composable
private fun TrialBenefits(access: TrialAccess) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.sectionGap)) {
        Text(stringResource(Res.string.trial_entry_included), style = SaqzTheme.typography.subtitle,
            color = SaqzTheme.colors.textPrimary)
        val maxAthletes = access.maxAthletes
        val groups = if (maxAthletes == null) {
            stringResource(Res.string.trial_entry_group_unlimited, access.maxGroups)
        } else {
            stringResource(Res.string.trial_entry_group, access.maxGroups, maxAthletes)
        }
        TrialBenefit(SaqzIcons.Users, groups,
            stringResource(Res.string.trial_entry_group_help))
        TrialBenefit(SaqzIcons.Calendar, stringResource(Res.string.trial_entry_games), stringResource(Res.string.trial_entry_games_help))
        TrialBenefit(SaqzIcons.CreditCard, stringResource(Res.string.trial_entry_finance),
            stringResource(Res.string.trial_entry_finance_help))
    }
}

@Composable
private fun TrialBenefit(icon: ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        Box(Modifier.background(SaqzTheme.colors.surface, RoundedCornerShape(SaqzTheme.metrics.cardRadius))
            .padding(SaqzTheme.metrics.blockGap)) { SaqzIcon(icon, tint = SaqzTheme.colors.primary) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(title, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
            Text(description, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
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
        TrialStatus.Available, null, null, "2026-09-15T12:00:00Z", false, true, 3, null, true, null,
    )), {}, {}, {})
}
