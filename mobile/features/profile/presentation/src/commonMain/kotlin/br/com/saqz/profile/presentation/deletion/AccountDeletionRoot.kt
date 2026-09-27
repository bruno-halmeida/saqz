package br.com.saqz.profile.presentation.deletion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountDeletionRoot(onBack: () -> Unit, onDeletionComplete: () -> Unit, viewModel: AccountDeletionViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { if (it == AccountDeletionEffect.DELETED) onDeletionComplete() }
    AccountDeletionScreen(state, viewModel::onIntent, onBack)
}
