package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.registration.RegistrationViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RegistrationRoot(viewModel: RegistrationViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegistrationScreen(state = state, onIntent = viewModel::onIntent)
}
