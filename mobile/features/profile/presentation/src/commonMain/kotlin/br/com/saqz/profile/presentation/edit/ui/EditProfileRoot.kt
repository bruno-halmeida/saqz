package br.com.saqz.profile.presentation.edit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.profile.presentation.edit.EditProfileEffect
import br.com.saqz.profile.presentation.edit.EditProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditProfileRoot(
    onSave: () -> Unit,
    onPickPhoto: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            EditProfileEffect.Saved -> onSave()
        }
    }
    EditProfileScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onPickPhoto = onPickPhoto,
        onBack = onBack,
    )
}
