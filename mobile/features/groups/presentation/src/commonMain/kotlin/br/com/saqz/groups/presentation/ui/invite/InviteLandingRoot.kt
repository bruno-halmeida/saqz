package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.invite.InviteLandingEffect
import br.com.saqz.groups.presentation.invite.InviteLandingIntent
import br.com.saqz.groups.presentation.invite.InviteLandingViewModel
import br.com.saqz.groups.presentation.navigation.InviteLandingRouteError
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InviteLandingRoot(
    code: String,
    onJoin: (String) -> Unit,
    onRequest: () -> Unit,
    onBrowseOtherGroups: () -> Unit,
    onExploreApp: () -> Unit,
    onOpenAnotherGroup: () -> Unit,
    onRequestNewInvite: () -> Unit,
    initialRequestSent: Boolean = false,
    initialRedeemError: InviteLandingRouteError? = null,
    viewModel: InviteLandingViewModel = koinViewModel(
        key = code,
        parameters = { parametersOf(code, initialRequestSent, initialRedeemError) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is InviteLandingEffect.Joined -> onJoin(effect.groupId)
            InviteLandingEffect.RequestSent -> onRequest()
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
