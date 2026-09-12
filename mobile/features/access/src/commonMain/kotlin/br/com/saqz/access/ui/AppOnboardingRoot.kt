package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.appaccess.AppOnboardingViewModel
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthCoordinator
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppOnboardingRoot(
    onCreateGroup: () -> Unit,
    viewModel: AppOnboardingViewModel = koinViewModel(),
    coordinator: AppOnboardingAuthCoordinator = koinInject(),
) {
    val handoffState = coordinator.state.collectAsStateWithLifecycle().value
    AppOnboardingScreen(
        state = viewModel.state.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onCreateGroup = onCreateGroup,
        accountConfirmationRequired = handoffState is AppOnboardingAuthState.NeedsAccountConfirmation,
        onConfirmAccount = coordinator::confirmAccountReplacement,
    )
}
