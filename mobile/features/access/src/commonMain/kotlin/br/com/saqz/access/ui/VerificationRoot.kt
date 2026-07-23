package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.verification.VerificationViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VerificationRoot(viewModel: VerificationViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VerificationScreen(state = state, onIntent = viewModel::onIntent)
}
