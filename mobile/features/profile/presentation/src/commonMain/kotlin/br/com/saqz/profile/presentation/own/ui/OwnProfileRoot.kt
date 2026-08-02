package br.com.saqz.profile.presentation.own.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.profile.presentation.own.OwnProfileEffect
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OwnProfileRoot(
    onOpenEditor: () -> Unit,
    onOpenPasswordRecovery: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: OwnProfileViewModel = koinViewModel(),
    imageLoader: ImageLoader = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            OwnProfileEffect.OpenEditor -> onOpenEditor()
            OwnProfileEffect.OpenPasswordRecovery -> onOpenPasswordRecovery()
            OwnProfileEffect.SignedOut -> onSignOut()
        }
    }
    OwnProfileScreen(state = state, onIntent = viewModel::onIntent, imageLoader = imageLoader)
}
