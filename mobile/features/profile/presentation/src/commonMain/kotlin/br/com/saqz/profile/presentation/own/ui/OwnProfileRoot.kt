package br.com.saqz.profile.presentation.own.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.profile.presentation.own.OwnProfileEffect
import br.com.saqz.profile.presentation.own.OwnProfileIntent
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OwnProfileRoot(
    onOpenEditor: () -> Unit,
    onOpenPasswordRecovery: () -> Unit,
    onSignOut: () -> Unit,
    refreshVersion: Int = 0,
    topBarWindowInsets: WindowInsets = WindowInsets.statusBars,
    viewModel: OwnProfileViewModel = koinViewModel(),
    imageLoader: ImageLoader = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // VUL-205: só recarrega se o contador mudou desde que esta ViewModel nasceu. O contador é
    // do host e sobrevive ao pop da entrada (`SaqzNavHost`), então `> 0` fazia a aba de perfil
    // somar o `Refresh` ao `init { load() }` da ViewModel nova toda vez que o shell voltava a
    // ser montado depois de uma edição salva.
    val loadedVersion = rememberSaveable(viewModel) { refreshVersion }
    LaunchedEffect(viewModel, refreshVersion) {
        if (refreshVersion != loadedVersion) viewModel.onIntent(OwnProfileIntent.Refresh)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            OwnProfileEffect.OpenEditor -> onOpenEditor()
            OwnProfileEffect.OpenPasswordRecovery -> onOpenPasswordRecovery()
            OwnProfileEffect.SignedOut -> onSignOut()
        }
    }
    OwnProfileScreen(
        state = state,
        onIntent = viewModel::onIntent,
        imageLoader = imageLoader,
        topBarWindowInsets = topBarWindowInsets,
    )
}
