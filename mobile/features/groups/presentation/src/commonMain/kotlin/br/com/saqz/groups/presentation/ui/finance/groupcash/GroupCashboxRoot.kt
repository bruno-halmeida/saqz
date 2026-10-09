package br.com.saqz.groups.presentation.ui.finance.groupcash

import br.com.saqz.groups.presentation.ui.finance.sheets.ChargeReminderIntent

import br.com.saqz.groups.presentation.ui.finance.sheets.ChargeReminderViewModel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GroupCashboxRoot(
    groupId: String,
    onBack: () -> Unit,
    onOpenStatement: (String) -> Unit,
    onOpenMonthlyGeneration: (String) -> Unit,
    onOpenNewEntry: (String) -> Unit = {},
    onMutationSuccess: () -> Unit = {},
    refreshVersion: Int = 0,
    onOpenReceivables: (() -> Unit)? = null,
    onOpenChargePayment: ((String) -> Unit)? = null,
    viewModel: GroupCashboxViewModel = koinViewModel(
        key = "cashbox/$groupId",
        parameters = { parametersOf(groupId) },
    ),
    reminders: ChargeReminderViewModel = koinViewModel(
        key = "charge-reminders/$groupId", parameters = { parametersOf(groupId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reminder by reminders.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.chargeSheetOpen) {
        if (!state.chargeSheetOpen) reminders.onIntent(ChargeReminderIntent.Reset)
    }
    val clipboard = LocalClipboardManager.current
    // Toda volta à tela recarrega por baixo. O contador do host (VUL-205) entra como segunda chave:
    // a volta do lançamento (`FinanceRoute.NewEntry`) muda por baixo desta entrada, e a ViewModel
    // ignora o pedido que chega logo depois do `init { load() }`.
    RefreshWhenVisible(refreshVersion) { viewModel.onIntent(GroupCashboxIntent.Refresh) }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupCashboxEffect.OpenStatement -> onOpenStatement(effect.groupId)
            is GroupCashboxEffect.OpenNewEntry -> onOpenNewEntry(effect.groupId)
            is GroupCashboxEffect.OpenMonthlyGeneration -> onOpenMonthlyGeneration(effect.groupId)
            is GroupCashboxEffect.CopyPix -> clipboard.setText(AnnotatedString(effect.key))
            GroupCashboxEffect.MutationSucceeded -> onMutationSuccess()
        }
    }
    GroupCashboxScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent,
        reminder = reminder,
        onSendReminders = { reminders.onIntent(ChargeReminderIntent.Send(it)) }, onOpenReceivables = onOpenReceivables,
        onOpenChargePayment = onOpenChargePayment)
}
