package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.ReceiptError
import br.com.saqz.receivables.domain.ReceiptManagementRole
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object FinancialManagementTags {
    const val Screen = "financial-management"
    const val Correct = "financial-management-correct"
    const val Recover = "financial-management-recover"
    const val Grant = "financial-management-grant"
    fun account(id: String) = "financial-management-account-$id"
    fun field(field: ManagementField) = "financial-management-${field.name}"
    fun revoke(id: String) = "financial-management-revoke-$id"
}

@Composable
fun FinancialManagementRoot(onBack: () -> Unit, onChange: () -> Unit,
    viewModel: FinancialManagementViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { if (viewModel.validEffect(it)) onChange() }
    FinancialManagementScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun FinancialManagementScreen(state: FinancialManagementState, onIntent: (FinancialManagementIntent) -> Unit,
    onBack: () -> Unit, modifier: Modifier = Modifier) = PaymentPage(stringResource(Res.string.management_title),
    FinancialManagementTags.Screen, { if (!state.pending) onBack() }, modifier) {
    if (state.loading) SaqzSpinner()
    state.error?.let { PaymentError(it) }
    if (state.accounts.isEmpty() && !state.loading) Text(stringResource(Res.string.management_empty))
    state.accounts.forEachIndexed { index, account ->
        SaqzButton(stringResource(Res.string.management_account, (index + 1).toString()),
            { onIntent(FinancialManagementIntent.SelectAccount(account.id)) },
            enabled = !state.pending, fullWidth = true, variant = SaqzButtonVariant.Secondary,
            modifier = Modifier.testTag(FinancialManagementTags.account(account.id)))
    }
    state.role?.let { Text(stringResource(if (it == ReceiptManagementRole.OWNER)
        Res.string.management_owner else Res.string.management_delegate)) }
    state.completedRequestId?.let { Text(stringResource(Res.string.management_completed)) }
    if (state.pending) {
        Text(stringResource(Res.string.management_pending))
        SaqzButton(stringResource(Res.string.management_recover), { onIntent(FinancialManagementIntent.Recover) },
            enabled = !state.loading, fullWidth = true, modifier = Modifier.testTag(FinancialManagementTags.Recover))
    }
    if (state.role != null && !state.pending) {
        Text(stringResource(Res.string.management_correction_title))
        ManagementField.entries.forEach { field ->
            SaqzInput(state.form[field], { onIntent(FinancialManagementIntent.Edit(field, it)) },
                stringResource(field.label()), enabled = !state.loading,
                keyboardType = when (field) {
                    ManagementField.EMAIL -> KeyboardType.Email
                    ManagementField.PHONE, ManagementField.MOBILE_PHONE, ManagementField.POSTAL_CODE -> KeyboardType.Number
                    ManagementField.INCOME -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                }, modifier = Modifier.testTag(FinancialManagementTags.field(field)))
        }
        state.form.correction()?.let { Text(stringResource(Res.string.management_income_review, formatBrl(it.incomeCents))) }
        Text(stringResource(Res.string.management_municipality_warning))
        SaqzSwitch(state.municipalityWarningAccepted, { onIntent(FinancialManagementIntent.AcceptMunicipalityWarning(it)) },
            label = stringResource(Res.string.management_warning_accept), enabled = !state.loading)
        SaqzButton(stringResource(Res.string.management_correct), { onIntent(FinancialManagementIntent.Correct) },
            enabled = state.canCorrect, fullWidth = true, modifier = Modifier.testTag(FinancialManagementTags.Correct))
        Text(stringResource(Res.string.management_delegations))
        state.delegations.forEach { delegation ->
            SaqzCard {
                Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                    Text(state.candidates.firstOrNull { it.userId == delegation.userId }?.displayName
                        ?: stringResource(Res.string.management_administrator))
                    Text(stringResource(if (delegation.revoked) Res.string.management_revoked else Res.string.management_active))
                    if (!delegation.revoked && state.role == ReceiptManagementRole.OWNER) SaqzButton(
                        stringResource(Res.string.management_revoke), { onIntent(FinancialManagementIntent.Revoke(delegation.userId)) },
                        enabled = !state.loading, modifier = Modifier.testTag(FinancialManagementTags.revoke(delegation.userId)))
                }
            }
        }
        if (state.role == ReceiptManagementRole.OWNER) {
            Text(stringResource(Res.string.management_choose_administrator))
            val available = state.candidates.filter { candidate ->
                state.delegations.none { it.userId == candidate.userId && !it.revoked }
            }
            if (available.isEmpty()) Text(stringResource(Res.string.management_no_candidates))
            available.forEach { candidate ->
                SaqzSwitch(state.delegateUserId == candidate.userId,
                    { onIntent(FinancialManagementIntent.DelegateUser(candidate.userId)) },
                    label = candidate.displayName + " · " + candidate.groupNames.joinToString(", "),
                    enabled = !state.loading, modifier = Modifier.testTag("management-candidate-${candidate.userId}"))
            }
            state.terms?.let { Text(stringResource(Res.string.receipt_terms, it.version)); Text(it.content) }
            SaqzSwitch(state.acceptedDelegation, { onIntent(FinancialManagementIntent.AcceptDelegation(it)) },
                label = stringResource(Res.string.management_accept_delegation), enabled = !state.loading)
            SaqzButton(stringResource(Res.string.management_grant), { onIntent(FinancialManagementIntent.Grant) },
                enabled = state.canGrant, fullWidth = true, modifier = Modifier.testTag(FinancialManagementTags.Grant))
        } else Text(stringResource(Res.string.management_no_redelegation))
    }
    SaqzButton(stringResource(Res.string.onboarding_refresh), { onIntent(FinancialManagementIntent.Refresh) },
        enabled = !state.loading && !state.pending && state.error != ReceiptError.SIGNED_OUT,
        fullWidth = true, variant = SaqzButtonVariant.Secondary)
}

private fun ManagementField.label(): StringResource = when (this) {
    ManagementField.EMAIL -> Res.string.management_email; ManagementField.PHONE -> Res.string.management_phone
    ManagementField.MOBILE_PHONE -> Res.string.management_mobile; ManagementField.SITE -> Res.string.management_site
    ManagementField.INCOME -> Res.string.management_income; ManagementField.POSTAL_CODE -> Res.string.management_postal
    ManagementField.ADDRESS -> Res.string.management_address; ManagementField.ADDRESS_NUMBER -> Res.string.management_number
    ManagementField.COMPLEMENT -> Res.string.management_complement; ManagementField.PROVINCE -> Res.string.management_province
}

@Preview
@Composable
private fun FinancialManagementPreview() = SaqzTheme {
    FinancialManagementScreen(FinancialManagementState(loading = false), {}, {})
}
