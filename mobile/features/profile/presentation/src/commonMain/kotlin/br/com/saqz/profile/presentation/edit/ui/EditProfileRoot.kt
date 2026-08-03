package br.com.saqz.profile.presentation.edit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.profile.presentation.edit.EditProfileEffect
import br.com.saqz.profile.presentation.edit.EditProfileViewModel
import br.com.saqz.profile.presentation.photo.ProfilePhotoIntent
import br.com.saqz.profile.presentation.photo.ProfilePhotoSelectionSheet
import br.com.saqz.profile.presentation.photo.ProfilePhotoViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditProfileRoot(
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = koinViewModel(),
    imageLoader: ImageLoader = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoViewModel: ProfilePhotoViewModel = koinViewModel()
    val photoState by photoViewModel.state.collectAsStateWithLifecycle()
    var photoSheetOpen by rememberSaveable { mutableStateOf(false) }
    val photoUrl = if (photoState.hasLocalUpdate) photoState.photoUrl else state.photoUrl

    LaunchedEffect(photoState.hasLocalUpdate, photoState.isLoading, photoState.error) {
        when {
            photoState.error != null -> photoSheetOpen = true
            photoState.hasLocalUpdate && !photoState.isLoading -> photoSheetOpen = false
        }
    }

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            EditProfileEffect.Saved -> onSave()
        }
    }
    Box(modifier.fillMaxSize()) {
        EditProfileScreen(
            state = state.copy(photoUrl = photoUrl),
            onIntent = viewModel::onIntent,
            onPickPhoto = { photoSheetOpen = true },
            onBack = onBack,
            imageLoader = imageLoader,
        )
        ProfilePhotoSelectionSheet(
            open = photoSheetOpen,
            photoUrl = photoUrl,
            onClose = { photoSheetOpen = false },
            onTakePhoto = {
                photoSheetOpen = false
                photoViewModel.onIntent(ProfilePhotoIntent.ChooseCamera)
            },
            onChooseFromGallery = {
                photoSheetOpen = false
                photoViewModel.onIntent(ProfilePhotoIntent.ChooseLibrary)
            },
            onRemovePhoto = { photoViewModel.onIntent(ProfilePhotoIntent.Remove) },
            error = photoState.error,
        )
    }
}
