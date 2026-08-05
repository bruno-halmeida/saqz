package br.com.saqz.profile.presentation.exit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProfileExitRoot(
    email: String,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ProfileExitViewModel = koinViewModel(key = email, parameters = { parametersOf(email) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            ProfileExitEffect.AccountDeleted -> onLogout()
        }
    }
    ProfileExitScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onClose = onClose,
        onLogout = onLogout,
    )
}
