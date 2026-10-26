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
import androidx.compose.runtime.LaunchedEffect
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
    const val Registration = "receipt-registration"
    fun method(method: ReceiptMethod) = "receipt-method-${method.name}"
    fun account(id: String) = "receipt-account-$id"
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ReceiptConfigurationRoot(groupId: String, onBack: () -> Unit, onRegistration: () -> Unit,
    viewModel: ReceiptConfigurationViewModel = koinViewModel(key = "receipts/$groupId", parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.pendingMutation) { /* Preserve the unresolved command until replay completes. */ }
    // Uma conta só (o caso de quase todo gestor): já entra nela, sem pedir escolha.
    LaunchedEffect(state.accounts, state.accountId, state.loading) {
        val only = state.accounts.singleOrNull()
        if (!state.loading && state.accountId == null && only != null) {
            viewModel.onIntent(ReceiptConfigurationIntent.SelectAccount(only.id))
        }
    }
    ReceiptConfigurationScreen(state, viewModel::onIntent, onBack, onRegistration)
}

/**
 * Gestor-Ativar: o argumento ("Chega de conferir extrato"), os três passos, as taxas e o cadastro. Com o grupo ativo,
 * a mesma tela é onde se desliga (o "Desligar recebimento pelo app" do Editar grupo chega aqui).
 */
@Composable
fun ReceiptConfigurationScreen(state: ReceiptConfigurationState, onIntent: (ReceiptConfigurationIntent) -> Unit,
    onBack: () -> Unit, onRegistration: () -> Unit = {}, modifier: Modifier = Modifier,
) {
    val enabled = state.status?.state?.enabled == true
    val account = state.accounts.firstOrNull { it.id == state.accountId } ?: state.accounts.singleOrNull()
    ReceivablesPage(stringResource(Res.string.activate_title), ReceiptConfigurationTags.Screen,
        { if (!state.pendingMutation) onBack() }, modifier, footer = {
            ActivationFooter(state, account, enabled, onIntent, onRegistration)
        }) {
        val metrics = SaqzTheme.metrics
        ReceivablesHero(kicker = stringResource(Res.string.activate_title), amount = stringResource(Res.string.activate_headline),
            support = stringResource(Res.string.activate_body))
        if (state.loading) SaqzSpinner(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(metrics.blockGap))
        Column(Modifier.fillMaxWidth().padding(metrics.horizontalPadding), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
            state.error?.let { Box(Modifier.testTag(ReceiptConfigurationTags.Error)) { ReceiptErrorText(it) } }
            if (state.pendingMutation) Text(stringResource(Res.string.receipt_pending), style = SaqzTheme.typography.support)
            if (state.completed && enabled) Text(stringResource(Res.string.activate_done), style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.successForeground)
            if (state.accounts.size > 1) state.accounts.forEach { item ->
                SaqzSwitch(checked = state.accountId == item.id,
                    onCheckedChange = { onIntent(ReceiptConfigurationIntent.SelectAccount(item.id)) },
                    label = stringResource(Res.string.receipt_account, item.id.takeLast(8)),
                    enabled = !state.loading && !state.pendingMutation,
                    modifier = Modifier.testTag(ReceiptConfigurationTags.account(item.id)))
            }
        }
        Step(1, Res.string.activate_step1_title, Res.string.activate_step1_body)
        Step(2, Res.string.activate_step2_title, Res.string.activate_step2_body)
        Step(3, Res.string.activate_step3_title, Res.string.activate_step3_body)
        Column(Modifier.fillMaxWidth().padding(metrics.horizontalPadding), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
            state.review?.let { review ->
                FeeTable(review.schedules.firstOrNull { it.method == ReceiptMethod.PIX }?.let { feeLabel(it) },
                    review.schedules.firstOrNull { it.method == ReceiptMethod.CARD }?.let { feeLabel(it) })
            }
            Text(stringResource(Res.string.activate_fees_choice), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
            if (state.terms.isNotEmpty()) {
                var showTerms by remember { mutableStateOf(false) }
                SaqzTextLink(stringResource(Res.string.activate_terms), { showTerms = !showTerms })
                if (showTerms) state.terms.forEach { document ->
                    Text(stringResource(Res.string.receipt_terms, document.version), style = SaqzTheme.typography.caption)
                    Text(document.content, Modifier.testTag(ReceiptConfigurationTags.Terms), style = SaqzTheme.typography.caption)
                }
            }
            Text(stringResource(Res.string.activate_wallet_note), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
            if (account == null || account.registration != AccountRegistration.APPROVED) {
                Text(stringResource(Res.string.activate_registration_note), style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textSecondary)
            }
            if (!enabled && account?.registration == AccountRegistration.APPROVED && !state.discoveryAvailable && !state.loading) {
                Text(stringResource(Res.string.activate_unavailable), style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.warningForeground)
            }
        }
    }
}

@Composable
private fun ActivationFooter(state: ReceiptConfigurationState, account: ReceiptAccount?, enabled: Boolean,
    onIntent: (ReceiptConfigurationIntent) -> Unit, onRegistration: () -> Unit) {
    val busy = state.loading || state.pendingMutation
    when {
        state.pendingMutation -> SaqzButton(stringResource(Res.string.receipt_retry_mutation),
            { onIntent(ReceiptConfigurationIntent.RetryMutation) }, enabled = !state.loading, fullWidth = true)
        enabled && state.confirmingDeactivation -> {
            Text(stringResource(Res.string.receipt_confirm), style = SaqzTheme.typography.support)
            SaqzButton(stringResource(Res.string.receipt_confirm_deactivate), { onIntent(ReceiptConfigurationIntent.Deactivate) },
                enabled = state.canDeactivate, variant = SaqzButtonVariant.Danger, fullWidth = true)
            SaqzButton(stringResource(Res.string.receipt_cancel), { onIntent(ReceiptConfigurationIntent.DismissDeactivation) },
                enabled = !busy, variant = SaqzButtonVariant.Secondary, fullWidth = true)
        }
        enabled -> SaqzButton(stringResource(Res.string.receiving_turn_off), { onIntent(ReceiptConfigurationIntent.RequestDeactivation) },
            enabled = state.canDeactivate, variant = SaqzButtonVariant.Ghost, contentColor = SaqzTheme.colors.errorForeground,
            modifier = Modifier.testTag(ReceiptConfigurationTags.Deactivate), fullWidth = true)
        state.loading && account == null -> Unit
        account == null -> SaqzButton(stringResource(Res.string.activate_start_registration), onRegistration, fullWidth = true,
            modifier = Modifier.testTag(ReceiptConfigurationTags.Registration))
        account.registration != AccountRegistration.APPROVED -> {
            Text(stringResource(if (account.registration == AccountRegistration.UNDER_REVIEW) Res.string.activate_under_review
                else Res.string.activate_correction), style = SaqzTheme.typography.support)
            SaqzButton(stringResource(Res.string.activate_open_registration), onRegistration, fullWidth = true,
                variant = SaqzButtonVariant.Secondary, modifier = Modifier.testTag(ReceiptConfigurationTags.Registration))
        }
        state.review == null -> SaqzButton(stringResource(Res.string.activate_review),
            { onIntent(ReceiptConfigurationIntent.PreviewAllMethods) }, fullWidth = true, loading = state.loading,
            enabled = state.status != null && state.discoveryAvailable, modifier = Modifier.testTag(ReceiptConfigurationTags.Preview))
        else -> {
            SaqzSwitch(checked = state.accepted, onCheckedChange = { onIntent(ReceiptConfigurationIntent.Accept(it)) },
                label = stringResource(Res.string.activate_accept), enabled = state.canAccept,
                modifier = Modifier.testTag(ReceiptConfigurationTags.Accept))
            if (state.review.permissions["ACTIVATE_GROUP"]?.allowed != true) Text(stringResource(Res.string.receipt_denied),
                style = SaqzTheme.typography.caption)
            SaqzButton(stringResource(Res.string.activate_confirm), { onIntent(ReceiptConfigurationIntent.Activate) },
                enabled = state.canActivate, loading = state.loading, modifier = Modifier.testTag(ReceiptConfigurationTags.Activate),
                fullWidth = true)
        }
    }
}

@Composable
private fun Step(number: Int, title: org.jetbrains.compose.resources.StringResource, body: org.jetbrains.compose.resources.StringResource) {
    ReceivablesRow("$number  ${stringResource(title)}", stringResource(body))
    SaqzDivider()
}

/** "Asaas 0,99% + R$ 0,49 · Saqz 1% + R$ 0,20": a composição; o rótulo único da tela é "Taxas de serviço e pagamento". */
private fun feeLabel(schedule: ReceiptSchedule): String =
    "${receiptPercentage(schedule.providerRate)} + ${formatBrl(schedule.providerFixedCents)} · " +
        "${receiptPercentage(schedule.commissionRate)} + ${formatBrl(schedule.commissionFixedCents)}"

@Preview(name = "Receber pelo app · sem cadastro", widthDp = 390)
@Composable
private fun ReceiptConfigurationPreview() = SaqzTheme {
    ReceiptConfigurationScreen(ReceiptConfigurationState(loading = false), {}, {})
}

@Preview(name = "Receber pelo app · cadastro aprovado", widthDp = 390)
@Composable
private fun ReceiptConfigurationApprovedPreview() = SaqzTheme {
    ReceiptConfigurationScreen(ReceiptConfigurationState(loading = false, discoveryAvailable = true, accountId = "account",
        accounts = listOf(ReceiptAccount("account", AccountRegistration.APPROVED, true)),
        status = ReceiptStatus(ReceiptConfiguration("account", "group", false, false, false), emptyMap())), {}, {})
}
