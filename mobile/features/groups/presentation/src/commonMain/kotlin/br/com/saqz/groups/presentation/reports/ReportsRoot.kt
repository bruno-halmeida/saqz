package br.com.saqz.groups.presentation.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReportsRoot(
    groupId: String,
    onBack: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(
        key = "reports/$groupId",
        parameters = { parametersOf(groupId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReportsScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
