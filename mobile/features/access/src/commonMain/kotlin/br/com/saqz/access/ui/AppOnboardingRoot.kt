package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthCoordinator
import br.com.saqz.access.presentation.appaccess.AppOnboardingAuthState
import br.com.saqz.access.presentation.appaccess.AppOnboardingEffect
import br.com.saqz.access.presentation.appaccess.AppOnboardingIntent
import br.com.saqz.access.presentation.appaccess.AppOnboardingViewModel
import br.com.saqz.access.presentation.appaccess.AppOnboardingState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppOnboardingRoot(
    onCreateGroup: () -> Unit,
    onClose: () -> Unit,
    onOpenLogin: () -> Unit,
    session: SessionAccessState,
    coordinator: AppOnboardingAuthCoordinator = koinInject(),
) {
    val createGroup by rememberUpdatedState(onCreateGroup)
    val closeIntro by rememberUpdatedState(onClose)
    val handoffState = coordinator.state.collectAsStateWithLifecycle().value
    val ownerUserId = (session as? SessionAccessState.Ready)?.session?.user?.id
    val canLoadIntro = session is SessionAccessState.Ready &&
        handoffState is AppOnboardingAuthState.Completed && !handoffState.onboardingCompleted
    val viewModel = if (canLoadIntro && ownerUserId != null) {
        koinViewModel<AppOnboardingViewModel>(key = "app-onboarding/$ownerUserId")
    } else {
        null
    }
    LaunchedEffect(canLoadIntro, ownerUserId) {
        if (canLoadIntro && ownerUserId != null) viewModel?.onIntent(AppOnboardingIntent.Opened)
    }
    LaunchedEffect(viewModel) {
        viewModel?.effects?.collect { effect ->
            when (effect) {
                AppOnboardingEffect.OpenFirstGroupForm -> {
                    coordinator.cancel()
                    createGroup()
                }
                AppOnboardingEffect.CloseIntro -> {
                    coordinator.cancel()
                    closeIntro()
                }
            }
        }
    }
    val currentSession = session as? SessionAccessState.Ready
    AppOnboardingScreen(
        state = viewModel?.state?.collectAsStateWithLifecycle()?.value ?: AppOnboardingState(),
        onIntent = viewModel?.let { it::onIntent } ?: {},
        authState = handoffState,
        currentAccountName = currentSession?.session?.user?.displayName,
        onClose = {
            coordinator.cancel()
            onClose()
        },
        onOpenLogin = {
            coordinator.cancel()
            onOpenLogin()
        },
        onConfirmAccount = coordinator::confirmAccountReplacement,
        onNewLink = coordinator::reset,
    )
}
