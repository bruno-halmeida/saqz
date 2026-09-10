package br.com.saqz.groups.presentation.monthlygeneration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MonthlyGenerationRoot(
    groupId: String,
    onBack: () -> Unit,
    onGenerationSuccess: () -> Unit,
    viewModel: MonthlyGenerationViewModel = koinViewModel(
        key = "monthly-generation/$groupId", parameters = { parametersOf(groupId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Keep the result observer alive until confirmation finishes, including system back/gestures.
    BackHandler(enabled = state.isSaving) {}
    ObserveAsEvents(viewModel.effects) { onGenerationSuccess() }
    MonthlyGenerationScreen(state, onBack = { if (!state.isSaving) onBack() }, onIntent = viewModel::onIntent)
}
