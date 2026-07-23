package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.passwordreset.PasswordResetViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PasswordResetRoot(viewModel: PasswordResetViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PasswordResetScreen(state = state, onIntent = viewModel::onIntent)
}
