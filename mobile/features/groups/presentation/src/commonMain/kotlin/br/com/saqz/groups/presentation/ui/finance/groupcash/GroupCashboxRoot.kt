package br.com.saqz.groups.presentation.ui.finance.groupcash

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
fun GroupCashboxRoot(
    groupId: String,
    onBack: () -> Unit,
    onOpenStatement: (String) -> Unit,
    onOpenNewEntry: (String) -> Unit = {},
    onMutationSuccess: () -> Unit = {},
    refreshVersion: Int = 0,
    viewModel: GroupCashboxViewModel = koinViewModel(parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(viewModel, refreshVersion) {
        if (refreshVersion > 0) viewModel.onIntent(GroupCashboxIntent.Retry)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupCashboxEffect.OpenStatement -> onOpenStatement(effect.groupId)
            is GroupCashboxEffect.OpenNewEntry -> onOpenNewEntry(effect.groupId)
            is GroupCashboxEffect.CopyPix -> clipboard.setText(AnnotatedString(effect.key))
            GroupCashboxEffect.MutationSucceeded -> onMutationSuccess()
        }
    }
    GroupCashboxScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
