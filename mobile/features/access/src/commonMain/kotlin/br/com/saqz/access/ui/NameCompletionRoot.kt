package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.namecompletion.NameCompletionViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NameCompletionRoot(viewModel: NameCompletionViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NameCompletionScreen(state = state, onIntent = viewModel::onIntent)
}
