package br.com.saqz.groups.presentation.newentry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun NewEntryRoot(
    groupId: String,
    onBack: () -> Unit,
    onEffect: (NewEntryEffect) -> Unit,
    viewModel: NewEntryViewModel = koinViewModel(parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects, onEvent = onEffect)
    NewEntryScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
