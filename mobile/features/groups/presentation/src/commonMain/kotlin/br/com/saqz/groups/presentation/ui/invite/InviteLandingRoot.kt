package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.invite.InviteLandingEffect
import br.com.saqz.groups.presentation.invite.InviteLandingIntent
import br.com.saqz.groups.presentation.invite.InviteLandingViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InviteLandingRoot(
    code: String,
    onJoined: (String) -> Unit,
    onRequestSent: () -> Unit,
    onBrowseOtherGroups: () -> Unit,
    onExploreApp: () -> Unit,
    onOpenAnotherGroup: () -> Unit,
    onRequestNewInvite: () -> Unit,
    viewModel: InviteLandingViewModel = koinViewModel(parameters = { parametersOf(code) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is InviteLandingEffect.Joined -> onJoined(effect.groupId)
            InviteLandingEffect.RequestSent -> onRequestSent()
            InviteLandingEffect.BrowseOtherGroups -> onBrowseOtherGroups()
            InviteLandingEffect.ExploreApp -> onExploreApp()
            InviteLandingEffect.OpenAnotherGroup -> onOpenAnotherGroup()
            InviteLandingEffect.RequestNewInvite -> onRequestNewInvite()
        }
    }
    InviteLandingScreen(
        state = state,
        onBack = { viewModel.onIntent(InviteLandingIntent.BrowseOtherGroups) },
        onIntent = viewModel::onIntent,
    )
}
