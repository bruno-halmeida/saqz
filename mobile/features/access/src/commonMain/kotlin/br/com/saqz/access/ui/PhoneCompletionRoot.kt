package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.phonecompletion.PhoneCompletionViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PhoneCompletionRoot(viewModel: PhoneCompletionViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PhoneCompletionScreen(state = state, onIntent = viewModel::onIntent)
}
