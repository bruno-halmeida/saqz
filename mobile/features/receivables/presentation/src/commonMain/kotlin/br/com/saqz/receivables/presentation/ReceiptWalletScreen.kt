package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object ReceiptWalletTags {
    const val Screen = "receipt-wallet"
    const val Balance = "receipt-wallet-balance"
    const val Pending = "receipt-wallet-pending"
    const val Amount = "receipt-wallet-amount"
    const val Accept = "receipt-wallet-accept"
    const val Withdraw = "receipt-wallet-withdraw"
    const val Recover = "receipt-wallet-recover"
    const val SaveBank = "receipt-wallet-save-bank"
    const val Password = "receipt-wallet-password"
    const val Authenticate = "receipt-wallet-authenticate"
    const val More = "receipt-wallet-more"
}
@Composable
fun ReceiptWalletRoot(onBack: () -> Unit, viewModel: ReceiptWalletViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) { viewModel.onIntent(ReceiptWalletIntent.Refresh); onPauseOrDispose { } }
    ReceiptWalletScreen(state, viewModel::onIntent, onBack)
}
@Composable
fun ReceiptWalletScreen(state: ReceiptWalletState, onIntent: (ReceiptWalletIntent) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier) = PaymentPage(stringResource(Res.string.wallet_title), ReceiptWalletTags.Screen, onBack, modifier) {
    if (state.loading || state.authenticating) SaqzSpinner()
    state.error?.let { WalletFailure(it) }
    if (!state.loading && state.accounts.isEmpty() && state.error == null) Text(stringResource(Res.string.wallet_empty))
    if (state.accounts.size > 1) state.accounts.forEachIndexed { index, account ->
        SaqzSwitch(state.accountId == account.id, { onIntent(ReceiptWalletIntent.Account(account.id)) },
            label = stringResource(Res.string.wallet_account, (index + 1).toString()), enabled = state.canEdit)
    }
    state.balance?.let {
        Text(stringResource(Res.string.wallet_available, formatBrl(it.availableBalanceCents)),
            modifier = Modifier.testTag(ReceiptWalletTags.Balance), style = SaqzTheme.typography.body)
        Text(stringResource(Res.string.wallet_receivables, formatBrl(it.pendingReceivablesCents)),
            modifier = Modifier.testTag(ReceiptWalletTags.Pending))
        Text(stringResource(Res.string.wallet_balance_help))
    }
    if (state.attempt != null) Text(stringResource(Res.string.wallet_attempt_pending))
    state.withdrawal?.let { withdrawal ->
        Text(stringResource(when (withdrawal.status) {
            "COMPLETED" -> Res.string.wallet_withdrawal_completed
            "REJECTED" -> Res.string.wallet_withdrawal_rejected
            "CANCELLED" -> Res.string.wallet_withdrawal_cancelled
            else -> Res.string.wallet_withdrawal_pending
        }, formatBrl(withdrawal.amountCents)))
        if (withdrawal.status == "COMPLETED") Text(stringResource(Res.string.wallet_observed_fee, formatBrl(withdrawal.feeCents)))
    }
    if (state.accountId != null && state.error != WalletError.SIGNED_OUT) {
        WalletDestinations(state, onIntent)
        WalletWithdrawalForm(state, onIntent)
        WalletAuthentication(state, onIntent)
        WalletStatement(state, onIntent)
    }
    SaqzButton(stringResource(if (state.attempt == null) Res.string.wallet_refresh else Res.string.wallet_recover),
        { onIntent(ReceiptWalletIntent.Refresh) }, enabled = state.idle, fullWidth = true,
        modifier = Modifier.testTag(ReceiptWalletTags.Recover), variant = SaqzButtonVariant.Secondary)
}
@Composable
private fun WalletDestinations(s: ReceiptWalletState, onIntent: (ReceiptWalletIntent) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
    Text(stringResource(Res.string.wallet_destinations), style = SaqzTheme.typography.body)
    s.destinations.forEach { bank ->
        SaqzSwitch(s.destinationId == bank.id, { onIntent(ReceiptWalletIntent.Destination(bank.id)) },
            label = stringResource(Res.string.wallet_destination, bank.bankCode, bank.agencySuffix, bank.accountSuffix),
            enabled = s.canEdit, modifier = Modifier.testTag("wallet-destination-${bank.id}"))
    }
    SaqzSwitch(s.editingBank, { onIntent(ReceiptWalletIntent.EditBank(it)) }, label = stringResource(Res.string.wallet_add_bank),
        enabled = s.canEdit)
    if (s.editingBank) {
        Text(stringResource(Res.string.wallet_bank_help))
        WalletBankField.entries.forEach { field ->
            val label = when (field) {
                WalletBankField.BANK -> Res.string.wallet_bank_code
                WalletBankField.NAME -> Res.string.wallet_bank_name
                WalletBankField.DOCUMENT -> Res.string.wallet_bank_document
                WalletBankField.AGENCY -> Res.string.wallet_bank_agency
                WalletBankField.ACCOUNT -> Res.string.wallet_bank_account
                WalletBankField.DIGIT -> Res.string.wallet_bank_digit
            }
            SaqzInput(s.bankForm[field], { onIntent(ReceiptWalletIntent.BankField(field, it)) }, stringResource(label),
                enabled = s.canEdit, modifier = Modifier.testTag("wallet-bank-${field.name}"))
        }
        SaqzSwitch(s.bankForm.savings, { onIntent(ReceiptWalletIntent.Savings(it)) },
            label = stringResource(Res.string.wallet_savings), enabled = s.canEdit)
        SaqzButton(stringResource(Res.string.wallet_save_bank), { onIntent(ReceiptWalletIntent.SaveBank) },
            enabled = s.canEdit && s.bankForm.details().valid(), fullWidth = true, modifier = Modifier.testTag(ReceiptWalletTags.SaveBank))
    }
}
@Composable
private fun WalletWithdrawalForm(s: ReceiptWalletState, onIntent: (ReceiptWalletIntent) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
    Text(stringResource(Res.string.wallet_withdraw_title), style = SaqzTheme.typography.body)
    SaqzInput(s.amount, { onIntent(ReceiptWalletIntent.Amount(it)) }, stringResource(Res.string.wallet_amount),
        enabled = s.canEdit, keyboardType = KeyboardType.Decimal, modifier = Modifier.testTag(ReceiptWalletTags.Amount))
    s.amountCents?.let { amount ->
        Text(stringResource(Res.string.wallet_amount_review, formatBrl(amount)))
        if (s.balance != null && amount > s.balance.availableBalanceCents) Text(stringResource(Res.string.wallet_insufficient))
    }
    SaqzSwitch(s.accepted, { onIntent(ReceiptWalletIntent.Accept(it)) }, label = stringResource(Res.string.wallet_accept),
        enabled = s.canEdit && s.destinationId != null && s.amountCents != null, modifier = Modifier.testTag(ReceiptWalletTags.Accept))
    SaqzButton(stringResource(Res.string.wallet_withdraw), { onIntent(ReceiptWalletIntent.Withdraw) },
        enabled = s.canWithdraw, fullWidth = true, modifier = Modifier.testTag(ReceiptWalletTags.Withdraw))
}
@Composable
private fun WalletAuthentication(s: ReceiptWalletState, onIntent: (ReceiptWalletIntent) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
    Text(stringResource(Res.string.wallet_auth_help))
    SaqzInput(s.password, { onIntent(ReceiptWalletIntent.Password(it)) }, stringResource(Res.string.wallet_password),
        kind = SaqzInputKind.Password, enabled = s.canEdit, modifier = Modifier.testTag(ReceiptWalletTags.Password))
    SaqzButton(stringResource(Res.string.wallet_auth_password), { onIntent(ReceiptWalletIntent.AuthenticatePassword) },
        enabled = s.canEdit && s.password.isNotBlank(), fullWidth = true, modifier = Modifier.testTag(ReceiptWalletTags.Authenticate),
        variant = SaqzButtonVariant.Secondary)
    SaqzButton(stringResource(Res.string.wallet_auth_google), { onIntent(ReceiptWalletIntent.AuthenticateGoogle) },
        enabled = s.canEdit, fullWidth = true, variant = SaqzButtonVariant.Secondary)
}
@Composable
private fun WalletStatement(s: ReceiptWalletState, onIntent: (ReceiptWalletIntent) -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
    Text(stringResource(Res.string.wallet_statement), style = SaqzTheme.typography.body)
    s.entries.forEach { entry -> SaqzCard {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(entry.description); Text(entry.occurredOn); Text(formatBrl(entry.amountCents))
            Text(stringResource(Res.string.wallet_entry_balance, formatBrl(entry.balanceCents)))
        }
    } }
    if (s.nextCursor != null) SaqzButton(stringResource(Res.string.wallet_more), { onIntent(ReceiptWalletIntent.More) },
        enabled = s.idle, fullWidth = true, modifier = Modifier.testTag(ReceiptWalletTags.More), variant = SaqzButtonVariant.Secondary)
}
@Composable
private fun WalletFailure(error: WalletError) = Text(stringResource(when (error) {
    WalletError.RECENT_AUTHENTICATION -> Res.string.wallet_recent_auth
    WalletError.INSUFFICIENT_BALANCE -> Res.string.wallet_insufficient
    WalletError.UNCERTAIN -> Res.string.wallet_attempt_pending
    WalletError.DENIED -> Res.string.wallet_denied
    WalletError.SIGNED_OUT -> Res.string.wallet_signed_out
    WalletError.CONFLICT -> Res.string.wallet_conflict
    WalletError.INVALID -> Res.string.wallet_invalid
    else -> Res.string.wallet_unavailable
}))
@Preview @Composable private fun WalletPreview() = SaqzTheme {
    ReceiptWalletScreen(ReceiptWalletState(loading = false,
        balance = ReceiptWalletBalance("account", 12345, 6789, "2026-09-13")), {}, {})
}
