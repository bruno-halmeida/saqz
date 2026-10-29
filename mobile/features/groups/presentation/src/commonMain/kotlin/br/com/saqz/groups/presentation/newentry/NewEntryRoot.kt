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
    expenseId: String? = null,
    /** DEB-148: `true` abre a Saída; senão a Entrada. */
    out: Boolean = false,
    viewModel: NewEntryViewModel = koinViewModel(
        key = "new-entry/$groupId/${expenseId.orEmpty()}",
        parameters = { parametersOf(groupId) },
    ),
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
    LaunchedEffect(viewModel, out) {
        if (expenseId == null && prefill == null) {
            viewModel.onIntent(NewEntryIntent.SelectDirection(if (out) NewEntryDirection.Out else NewEntryDirection.In))
        }
    }
    LaunchedEffect(viewModel, expenseId) {
        if (expenseId != null) viewModel.onIntent(NewEntryIntent.LoadEdit(expenseId))
    }
    ObserveAsEvents(viewModel.effects, onEvent = onEffect)
    NewEntryScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
