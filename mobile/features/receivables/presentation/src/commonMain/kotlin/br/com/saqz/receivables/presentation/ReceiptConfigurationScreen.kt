package br.com.saqz.receivables.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object ReceiptConfigurationTags {
    const val Screen = "receipt-configuration"
    const val Preview = "receipt-preview"
    const val Accept = "receipt-accept"
    const val Activate = "receipt-activate"
    const val Deactivate = "receipt-deactivate"
    const val Terms = "receipt-terms"
    const val Error = "receipt-error"
    fun method(method: ReceiptMethod) = "receipt-method-${method.name}"
    fun account(id: String) = "receipt-account-$id"
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ReceiptConfigurationRoot(groupId: String, onBack: () -> Unit,
    viewModel: ReceiptConfigurationViewModel = koinViewModel(key = "receipts/$groupId", parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.pendingMutation) { /* Preserve the unresolved command until replay completes. */ }
    ReceiptConfigurationScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun ReceiptConfigurationScreen(state: ReceiptConfigurationState, onIntent: (ReceiptConfigurationIntent) -> Unit,
    onBack: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(ReceiptConfigurationTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.receipt_title), onBack = { if (!state.pendingMutation) onBack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            Text(stringResource(Res.string.receipt_intro), color = SaqzTheme.colors.textPrimary)
            if (state.loading) SaqzSpinner()
            state.error?.let { Text(stringResource(errorText(it)), Modifier.testTag(ReceiptConfigurationTags.Error)) }
            if (state.pendingMutation) {
                Text(stringResource(Res.string.receipt_pending))
                SaqzButton(stringResource(Res.string.receipt_retry_mutation), { onIntent(ReceiptConfigurationIntent.RetryMutation) },
                    enabled = !state.loading, fullWidth = true)
            }
            if (state.completed) Text(stringResource(Res.string.receipt_success))
            if (!state.loading && !state.pendingMutation && state.hasNoAccount && state.error == null) {
                Text(stringResource(Res.string.receipt_no_account))
            }
            if (state.accounts.isNotEmpty()) Text(stringResource(Res.string.receipt_accounts))
            state.accounts.forEach { account ->
                SaqzSwitch(checked = state.accountId == account.id,
                    onCheckedChange = { onIntent(ReceiptConfigurationIntent.SelectAccount(account.id)) },
                    label = stringResource(Res.string.receipt_account, account.id.takeLast(8)),
                    enabled = !state.loading && !state.pendingMutation,
                    modifier = Modifier.testTag(ReceiptConfigurationTags.account(account.id)))
                Text(stringResource(registrationText(account.registration)))
            }
            state.status?.let { status ->
                Text(stringResource(if (status.state.enabled) Res.string.receipt_enabled else Res.string.receipt_disabled))
                if (status.state.enabled) Text(stringResource(Res.string.receipt_current, listOfNotNull(
                    if (status.state.pixEnabled) stringResource(Res.string.receipt_pix) else null,
                    if (status.state.cardEnabled) stringResource(Res.string.receipt_card) else null,
                ).joinToString(", ")))
                if (!state.discoveryAvailable) Text(stringResource(Res.string.receipt_unavailable))
                Text(stringResource(Res.string.receipt_choose))
                ReceiptMethod.entries.forEach { method ->
                    SaqzSwitch(checked = method in state.methods,
                        onCheckedChange = { onIntent(ReceiptConfigurationIntent.ToggleMethod(method)) },
                        label = methodName(method),
                    enabled = !state.loading && !state.pendingMutation,
                        modifier = Modifier.testTag(ReceiptConfigurationTags.method(method)))
                }
                SaqzButton(stringResource(Res.string.receipt_preview), { onIntent(ReceiptConfigurationIntent.Preview) },
                    enabled = state.canPreview, modifier = Modifier.testTag(ReceiptConfigurationTags.Preview), fullWidth = true)
            }
            state.review?.let { ReviewContent(it, state.terms) }
            if (state.review != null) {
                SaqzSwitch(checked = state.accepted, onCheckedChange = { onIntent(ReceiptConfigurationIntent.Accept(it)) },
                    label = stringResource(Res.string.receipt_accept), enabled = state.canAccept,
                    modifier = Modifier.testTag(ReceiptConfigurationTags.Accept))
                if (state.review.permissions["ACTIVATE_GROUP"]?.allowed != true) Text(stringResource(Res.string.receipt_denied))
                SaqzButton(stringResource(Res.string.receipt_activate), { onIntent(ReceiptConfigurationIntent.Activate) },
                    enabled = state.canActivate, modifier = Modifier.testTag(ReceiptConfigurationTags.Activate), fullWidth = true)
            }
            if (state.status?.state?.enabled == true) {
                SaqzButton(stringResource(Res.string.receipt_deactivate), { onIntent(ReceiptConfigurationIntent.RequestDeactivation) },
                    enabled = state.canDeactivate, variant = SaqzButtonVariant.Secondary,
                    modifier = Modifier.testTag(ReceiptConfigurationTags.Deactivate), fullWidth = true)
            }
            if (state.confirmingDeactivation) {
                Text(stringResource(Res.string.receipt_confirm))
                SaqzButton(stringResource(Res.string.receipt_confirm_deactivate), { onIntent(ReceiptConfigurationIntent.Deactivate) },
                    enabled = state.canDeactivate, fullWidth = true)
                SaqzButton(stringResource(Res.string.receipt_cancel), { onIntent(ReceiptConfigurationIntent.DismissDeactivation) },

                    enabled = !state.loading && !state.pendingMutation, variant = SaqzButtonVariant.Secondary, fullWidth = true)
            }
            SaqzButton(stringResource(Res.string.receipt_refresh), { onIntent(ReceiptConfigurationIntent.Refresh) },
                enabled = !state.loading && !state.pendingMutation && state.error != ReceiptError.SIGNED_OUT,
                variant = SaqzButtonVariant.Secondary, fullWidth = true)
        }
    }
}

@Composable
private fun ReviewContent(review: ReceiptReview, terms: List<ReceiptTerms>) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
    var showRates by remember { mutableStateOf(false) }
    SaqzButton(stringResource(Res.string.receipt_composition), { showRates = !showRates },
        variant = SaqzButtonVariant.Secondary, fullWidth = true)
    if (showRates) review.schedules.forEach { schedule ->
        SaqzCard {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(stringResource(Res.string.receipt_schedule, methodName(schedule.method), schedule.termsVersion))
                Text(stringResource(Res.string.receipt_provider, receiptPercentage(schedule.providerRate),
                    formatBrl(schedule.providerFixedCents)))
                if (schedule.providerMinimumCents > 0) Text(stringResource(Res.string.receipt_provider_minimum,
                    formatBrl(schedule.providerMinimumCents)))
                schedule.providerMaximumCents?.let { maximum ->
                    Text(stringResource(Res.string.receipt_provider_maximum, formatBrl(maximum)))
                }
                Text(stringResource(Res.string.receipt_commission, receiptPercentage(schedule.commissionRate),
                    formatBrl(schedule.commissionFixedCents)))
            }
        }
    }
    if (review.prices.isEmpty()) Text(stringResource(Res.string.receipt_no_prices))
    review.prices.forEach { price ->
        SaqzCard {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(stringResource(Res.string.receipt_price,
                    stringResource(if (price.kind == "GAME") Res.string.receipt_GAME else Res.string.receipt_MONTHLY),
                    methodName(price.method)))
                Text(stringResource(Res.string.receipt_base, formatBrl(price.baseCents)))
                Text(stringResource(Res.string.receipt_fees, formatBrl(price.feesCents)))
                Text(stringResource(Res.string.receipt_total, formatBrl(price.totalCents)))
                Text(stringResource(Res.string.receipt_net, formatBrl(price.expectedNetCents)))
            }
        }
    }
    review.effectiveCutoffAt?.let { Text(stringResource(Res.string.receipt_cutoff, it)) }
    terms.forEach { document ->
        Text(stringResource(Res.string.receipt_terms, document.version))
        Text(document.content, Modifier.testTag(ReceiptConfigurationTags.Terms))
    }
}

}

@Composable
private fun methodName(method: ReceiptMethod) =
    stringResource(if (method == ReceiptMethod.PIX) Res.string.receipt_pix else Res.string.receipt_card)
private fun registrationText(status: AccountRegistration) = when (status) {
    AccountRegistration.INCOMPLETE -> Res.string.receipt_registration_INCOMPLETE
    AccountRegistration.UNDER_REVIEW -> Res.string.receipt_registration_UNDER_REVIEW
    AccountRegistration.CORRECTION_REQUIRED -> Res.string.receipt_registration_CORRECTION_REQUIRED
    AccountRegistration.APPROVED -> Res.string.receipt_registration_APPROVED
    AccountRegistration.REJECTED -> Res.string.receipt_registration_REJECTED
}
private fun errorText(error: ReceiptError) = when (error) {
    ReceiptError.DENIED -> Res.string.receipt_error_DENIED
    ReceiptError.STALE -> Res.string.receipt_error_STALE
    ReceiptError.INVALID -> Res.string.receipt_error_INVALID
    ReceiptError.UNAVAILABLE -> Res.string.receipt_error_UNAVAILABLE
    ReceiptError.NETWORK -> Res.string.receipt_error_NETWORK
    ReceiptError.UNCERTAIN -> Res.string.receipt_error_UNCERTAIN
    ReceiptError.SIGNED_OUT -> Res.string.receipt_error_SIGNED_OUT
}

@Preview
@Composable
private fun ReceiptConfigurationPreview() = SaqzTheme {
    ReceiptConfigurationScreen(ReceiptConfigurationState(loading = false,
        accounts = listOf(ReceiptAccount("account", AccountRegistration.UNDER_REVIEW, false))), {}, {})
}
