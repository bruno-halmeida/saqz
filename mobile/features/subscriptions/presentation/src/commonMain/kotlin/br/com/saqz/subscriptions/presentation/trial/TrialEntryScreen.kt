package br.com.saqz.subscriptions.presentation.trial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object TrialEntryTags {
    const val Code = "trial-entry-code"
    const val Apply = "trial-entry-apply"
    const val Continue = "trial-entry-continue"
    const val Error = "trial-entry-error"
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
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(title = stringResource(Res.string.trial_entry_title), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(
            horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            if (state.loading) Text(stringResource(Res.string.trial_entry_loading))
            state.access?.let { access ->
                if (access.canCreateGroup) {
                    Text(stringResource(Res.string.trial_entry_available, access.trialDays), style = SaqzTheme.typography.subtitle)
                    Text(stringResource(Res.string.trial_entry_terms))
                } else {
                    Text(stringResource(when {
                        access.canRedeemCoupon -> Res.string.trial_entry_coupon_only
                        access.offerMode == "OFF" -> Res.string.trial_entry_off
                        else -> Res.string.trial_entry_unavailable
                    }), style = SaqzTheme.typography.subtitle)
                }
                if (access.canRedeemCoupon) {
                    if (access.selectedCouponCode == null) Text(stringResource(
                        if (access.offerMode == "ON") Res.string.trial_entry_coupon_optional else Res.string.trial_entry_coupon_help,
                    ))
                    SaqzInput(state.code, { onIntent(TrialEntryIntent.EditCode(it)) }, stringResource(Res.string.trial_entry_code),
                        enabled = !state.loading, modifier = Modifier.testTag(TrialEntryTags.Code))
                    SaqzButton(stringResource(Res.string.trial_entry_apply), { onIntent(TrialEntryIntent.Apply) },
                        enabled = !state.loading && state.code.isNotBlank(), fullWidth = true,
                        variant = if (access.canCreateGroup) SaqzButtonVariant.Secondary else SaqzButtonVariant.Primary,
                        modifier = Modifier.testTag(TrialEntryTags.Apply))
                }
                access.selectedCouponCode?.takeIf { state.failure == null }?.let {
                    Text(stringResource(Res.string.trial_entry_applied, it))
                }
                if (access.canCreateGroup) {
                    SaqzButton(stringResource(Res.string.trial_entry_continue), { onIntent(TrialEntryIntent.Continue) },
                        enabled = state.canContinue, fullWidth = true, modifier = Modifier.testTag(TrialEntryTags.Continue))
                }
                if (!access.canCreateGroup) SaqzButton(stringResource(Res.string.trial_entry_plans), onSubscribe,
                    enabled = !state.loading, fullWidth = true, variant = SaqzButtonVariant.Secondary)
            }
            state.failure?.let { failure ->
                Text(stringResource(when (failure) {
                    TrialEntryFailure.Load -> Res.string.trial_entry_load_error
                    TrialEntryFailure.Coupon -> Res.string.trial_entry_coupon_error
                    TrialEntryFailure.Offer -> Res.string.trial_entry_offer_error
                    TrialEntryFailure.Apply -> Res.string.trial_entry_apply_error
                }), color = SaqzTheme.colors.textPrimary, modifier = Modifier.testTag(TrialEntryTags.Error))
            }
            SaqzButton(stringResource(Res.string.trial_entry_refresh), { onIntent(TrialEntryIntent.Refresh) },
                enabled = !state.loading, fullWidth = true, variant = SaqzButtonVariant.Secondary)
        }
    }
}

@Preview
@Composable
private fun TrialEntryPreview() = SaqzTheme { TrialEntryScreen(TrialEntryState(), {}, {}, {}) }
