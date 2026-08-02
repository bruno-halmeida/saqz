package br.com.saqz.profile.presentation.photo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.resources.Res
import br.com.saqz.profile.resources.profile_photo_camera_permission_denied
import br.com.saqz.profile.resources.profile_photo_gallery
import br.com.saqz.profile.resources.profile_photo_library_permission_denied
import br.com.saqz.profile.resources.profile_photo_remove
import br.com.saqz.profile.resources.profile_photo_removal_failed
import br.com.saqz.profile.resources.profile_photo_selection_failed
import br.com.saqz.profile.resources.profile_photo_sheet_title
import br.com.saqz.profile.resources.profile_photo_take
import br.com.saqz.profile.resources.profile_photo_upload_failed
import org.jetbrains.compose.resources.stringResource

object ProfilePhotoTags {
    const val Sheet = "profile-photo-sheet"
    const val Camera = "profile-photo-take"
    const val Library = "profile-photo-library"
    const val Remove = "profile-photo-remove"
    const val Error = "profile-photo-error"
}

/** Folha compartilhada pela edição de perfil; a tela hospedeira decide quando abri-la. */
@Composable
fun ProfilePhotoSelectionSheet(
    open: Boolean,
    onClose: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    error: ProfilePhotoError? = null,
) {
    val metrics = SaqzTheme.metrics
    SaqzBottomSheet(
        open = open,
        onClose = onClose,
        modifier = modifier.testTag(ProfilePhotoTags.Sheet),
        title = stringResource(Res.string.profile_photo_sheet_title),
    ) {
        ProfilePhotoAction(
            label = stringResource(Res.string.profile_photo_take),
            onClick = onTakePhoto,
            tag = ProfilePhotoTags.Camera,
            icon = SaqzIcons.Camera,
        )
        ProfilePhotoAction(
            label = stringResource(Res.string.profile_photo_gallery),
            onClick = onChooseFromGallery,
            tag = ProfilePhotoTags.Library,
        )
        ProfilePhotoAction(
            label = stringResource(Res.string.profile_photo_remove),
            onClick = onRemovePhoto,
            tag = ProfilePhotoTags.Remove,
            icon = SaqzIcons.Trash,
        )
        if (error != null) {
            Spacer(Modifier.heightIn(min = metrics.subGrid))
            ProfilePhotoErrorText(error, Modifier.testTag(ProfilePhotoTags.Error))
        }
    }
}

@Composable
fun ProfilePhotoErrorText(
    error: ProfilePhotoError,
    modifier: Modifier = Modifier,
) {
    val message = when (error) {
        ProfilePhotoError.CameraPermissionDenied -> stringResource(Res.string.profile_photo_camera_permission_denied)
        ProfilePhotoError.LibraryPermissionDenied -> stringResource(Res.string.profile_photo_library_permission_denied)
        ProfilePhotoError.SelectionFailed -> stringResource(Res.string.profile_photo_selection_failed)
        ProfilePhotoError.UploadFailed -> stringResource(Res.string.profile_photo_upload_failed)
        ProfilePhotoError.RemovalFailed -> stringResource(Res.string.profile_photo_removal_failed)
    }
    Text(
        text = message,
        color = SaqzTheme.colors.errorForeground,
        style = SaqzTheme.typography.support,
        modifier = modifier,
    )
}

@Composable
private fun ProfilePhotoAction(
    label: String,
    onClick: () -> Unit,
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(vertical = metrics.subGrid)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        if (icon != null) {
            SaqzIcon(icon, tint = colors.primary)
        }
        Text(
            text = label,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        SaqzIcon(SaqzIcons.ChevronRight, tint = colors.textSecondary)
    }
}
