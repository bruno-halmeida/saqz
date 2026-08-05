package br.com.saqz.groups.presentation.newentry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_settlement_court_expense_description_prefill
import org.jetbrains.compose.resources.stringResource

@Composable
fun NewEntryRoot(
    groupId: String,
    onBack: () -> Unit,
    onEffect: (NewEntryEffect) -> Unit,
    prefill: NewEntryPrefill? = null,
    viewModel: NewEntryViewModel = koinViewModel(key = groupId, parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefillDescription = prefill?.let {
        when (it) {
            is NewEntryPrefill.GameCourt -> stringResource(Res.string.game_settlement_court_expense_description_prefill)
        }
    }
    LaunchedEffect(viewModel, prefill, prefillDescription) {
        if (prefill != null && prefillDescription != null) {
            viewModel.onIntent(NewEntryIntent.ApplyPrefill(prefill, prefillDescription))
        }
    }
    ObserveAsEvents(viewModel.effects, onEvent = onEffect)
    NewEntryScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
