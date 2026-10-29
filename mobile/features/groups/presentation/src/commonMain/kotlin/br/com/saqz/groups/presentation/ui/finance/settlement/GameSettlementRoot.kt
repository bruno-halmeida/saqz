package br.com.saqz.groups.presentation.ui.finance.settlement

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
fun GameSettlementRoot(
    groupId: String,
    gameId: String,
    onBack: () -> Unit,
    onOpenNewEntry: (String, String) -> Unit,
    onOpenCashbox: (String) -> Unit,
    onOpenUnchargedReview: (groupId: String, gameId: String) -> Unit = { _, _ -> },
    onMutationSuccess: () -> Unit = {},
    refreshVersion: Int = 0,
    viewModel: GameSettlementViewModel = koinViewModel(
        key = "settlement/$groupId/$gameId",
        parameters = { parametersOf(groupId, gameId) },
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
    // Toda volta à tela recarrega por baixo; o contador do host (VUL-205) é a segunda chave.
    RefreshWhenVisible(refreshVersion) { viewModel.onIntent(GameSettlementIntent.Refresh) }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            GameSettlementEffect.Closed -> onBack()
            is GameSettlementEffect.OpenNewEntry -> onOpenNewEntry(effect.groupId, effect.localDate)
            is GameSettlementEffect.OpenCashbox -> onOpenCashbox(effect.groupId)
            is GameSettlementEffect.OpenUnchargedReview -> onOpenUnchargedReview(effect.groupId, effect.gameId)
            is GameSettlementEffect.CopyPix -> clipboard.setText(AnnotatedString(effect.key))
            GameSettlementEffect.MutationSucceeded -> onMutationSuccess()
        }
    }
    GameSettlementScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent,
        reminder = reminder,
        onSendReminders = { reminders.onIntent(ChargeReminderIntent.Send(it)) })
}
