package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatLocalDatePtBrString
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.ReceiptError
import br.com.saqz.receivables.domain.ReceiptMethod
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object ChargeApprovalTags {
    const val Screen = "charge-approval"
    const val Accept = "charge-approval-accept"
    const val Submit = "charge-approval-submit"
    const val Cancel = "charge-approval-cancel"
    const val Confirm = "charge-approval-confirm"
    const val Replay = "charge-approval-replay"
    fun account(id: String) = "charge-approval-account-$id"
}
@Composable
fun ChargeApprovalRoot(groupId: String, chargeId: String, onBack: () -> Unit, onMutationSuccess: () -> Unit,
    viewModel: ChargeApprovalViewModel = koinViewModel(key = "approval/$groupId/$chargeId",
        parameters = { parametersOf(groupId, chargeId) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) { viewModel.onIntent(ChargeApprovalIntent.Refresh); onPauseOrDispose { } }
    ObserveAsEvents(viewModel.effects) { if (viewModel.validEffect(it)) onMutationSuccess() }
    ChargeApprovalScreen(state, viewModel::onIntent, onBack)
}
@Composable
fun ChargeApprovalScreen(state: ChargeApprovalState, onIntent: (ChargeApprovalIntent) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier) = PaymentPage(stringResource(Res.string.approval_title), ChargeApprovalTags.Screen, onBack, modifier) {
    if (state.loading) SaqzSpinner()
    state.error?.let { PaymentError(it) }
    if (state.accountId == null) {
        Text(stringResource(Res.string.approval_choose_account))
        if (!state.loading && state.error == null && state.accounts.isEmpty()) Text(stringResource(Res.string.approval_empty))
        state.accounts.forEach { account ->
            SaqzButton(stringResource(Res.string.approval_account, account.id.takeLast(8)),
                { onIntent(ChargeApprovalIntent.Account(account.id)) }, enabled = !state.loading,
                modifier = Modifier.testTag(ChargeApprovalTags.account(account.id)), fullWidth = true)
        }
    } else Text(stringResource(Res.string.approval_account, state.accountId.takeLast(8)))
    if (state.accountId != null && state.accounts.size > 1 && state.attempt == null) {
        SaqzButton(stringResource(Res.string.approval_change_account), { onIntent(ChargeApprovalIntent.ChooseAccount) },
            enabled = !state.loading, fullWidth = true, variant = SaqzButtonVariant.Secondary)
    }
    if (state.attempt != null) {
        Text(stringResource(Res.string.approval_pending))
        SaqzButton(stringResource(Res.string.approval_replay), { onIntent(ChargeApprovalIntent.Replay) },
            enabled = !state.loading, fullWidth = true, modifier = Modifier.testTag(ChargeApprovalTags.Replay))
    }
    state.review?.let { review ->
        Text(stringResource(Res.string.payment_reference, review.target.chargeId.takeLast(8)))
        Text(stringResource(Res.string.payment_due, formatLocalDatePtBrString(review.dueDate)))
        Text(stringResource(Res.string.approval_review))
        review.quotes.forEach { quote ->
            Text(stringResource(if (quote.method == ReceiptMethod.PIX) Res.string.receipt_pix else Res.string.receipt_card))
            QuoteSummary(quote)
        }
        state.terms.forEach { terms -> Text(stringResource(Res.string.receipt_terms, terms.version)); Text(terms.content) }
        SaqzSwitch(state.accepted, { onIntent(ChargeApprovalIntent.Accept(it)) }, label = stringResource(Res.string.approval_accept),
            enabled = state.canAccept, modifier = Modifier.testTag(ChargeApprovalTags.Accept))
        SaqzButton(stringResource(Res.string.approval_submit), { onIntent(ChargeApprovalIntent.Approve) }, enabled = state.canApprove,
            fullWidth = true, modifier = Modifier.testTag(ChargeApprovalTags.Submit))
    }
    state.detail?.let {
        Text(stringResource(Res.string.approval_existing))
        Text(stringResource(Res.string.payment_reference, it.order.chargeId.takeLast(8)))
        Text(stringResource(paymentStatusText(displayPaymentStatus(MemberPaymentState(loading = false, detail = it)))))
        if (state.canCancel) SaqzButton(stringResource(Res.string.approval_cancel), { onIntent(ChargeApprovalIntent.RequestCancel) },
            fullWidth = true, variant = SaqzButtonVariant.Secondary, modifier = Modifier.testTag(ChargeApprovalTags.Cancel))
    }
    if (state.confirmCancel) {
        Text(stringResource(Res.string.approval_cancel_confirm))
        SaqzButton(stringResource(Res.string.approval_cancel_yes), { onIntent(ChargeApprovalIntent.ConfirmCancel) },
            enabled = state.canCancel, fullWidth = true, modifier = Modifier.testTag(ChargeApprovalTags.Confirm))
        SaqzButton(stringResource(Res.string.approval_cancel_no), { onIntent(ChargeApprovalIntent.DismissCancel) },
            fullWidth = true, variant = SaqzButtonVariant.Secondary)
    }
    SaqzButton(stringResource(Res.string.approval_refresh), { onIntent(ChargeApprovalIntent.Refresh) },
        enabled = !state.loading && state.error != ReceiptError.SIGNED_OUT, fullWidth = true, variant = SaqzButtonVariant.Secondary)
}
@Preview @Composable private fun ApprovalPreview() = SaqzTheme {
    ChargeApprovalScreen(ChargeApprovalState(loading = false), {}, {})
}
