package br.com.saqz.groups.presentation.ui.finance.settlement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GameSettlementRoot(
    groupId: String,
    gameId: String,
    onBack: () -> Unit,
    onOpenNewEntry: (String, String) -> Unit,
    onOpenCashbox: (String) -> Unit,
    onMutationSuccess: () -> Unit = {},
    refreshVersion: Int = 0,
    viewModel: GameSettlementViewModel = koinViewModel(parameters = { parametersOf(groupId, gameId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(viewModel, refreshVersion) {
        if (refreshVersion > 0) viewModel.onIntent(GameSettlementIntent.Retry)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GameSettlementEffect.OpenNewEntry -> onOpenNewEntry(effect.groupId, effect.localDate)
            is GameSettlementEffect.OpenCashbox -> onOpenCashbox(effect.groupId)
            is GameSettlementEffect.CopyPix -> clipboard.setText(AnnotatedString(effect.key))
            GameSettlementEffect.MutationSucceeded -> onMutationSuccess()
        }
    }
    GameSettlementScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
