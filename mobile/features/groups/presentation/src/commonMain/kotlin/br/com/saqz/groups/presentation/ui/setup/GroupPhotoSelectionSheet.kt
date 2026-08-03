package br.com.saqz.groups.presentation.ui.setup

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
import br.com.saqz.groups.presentation.photo.GroupPhotoUiError
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_photo_camera_permission_denied
import br.com.saqz.groups.resources.group_photo_encoding_failed
import br.com.saqz.groups.resources.group_photo_gallery
import br.com.saqz.groups.resources.group_photo_library_permission_denied
import br.com.saqz.groups.resources.group_photo_load_failed
import br.com.saqz.groups.resources.group_photo_remove
import br.com.saqz.groups.resources.group_photo_removal_failed
import br.com.saqz.groups.resources.group_photo_selection_failed
import br.com.saqz.groups.resources.group_photo_sheet_title
import br.com.saqz.groups.resources.group_photo_take
import br.com.saqz.groups.resources.group_photo_target_unavailable
import br.com.saqz.groups.resources.group_photo_upload_failed
import org.jetbrains.compose.resources.stringResource

internal object GroupPhotoTags {
    const val Sheet = "group-photo-sheet"
    const val Camera = "group-photo-take"
    const val Library = "group-photo-library"
    const val Remove = "group-photo-remove"
    const val Error = "group-photo-error"
}

@Composable
internal fun GroupPhotoSelectionSheet(
    open: Boolean,
    photoUrl: String?,
    onClose: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    error: GroupPhotoUiError? = null,
) {
    val metrics = SaqzTheme.metrics
    SaqzBottomSheet(
        open = open,
        onClose = onClose,
        modifier = modifier.testTag(GroupPhotoTags.Sheet),
        title = stringResource(Res.string.group_photo_sheet_title),
    ) {
        GroupPhotoAction(stringResource(Res.string.group_photo_take), onTakePhoto, GroupPhotoTags.Camera, SaqzIcons.Camera)
        GroupPhotoAction(stringResource(Res.string.group_photo_gallery), onChooseFromGallery, GroupPhotoTags.Library)
        if (photoUrl != null) {
            GroupPhotoAction(stringResource(Res.string.group_photo_remove), onRemovePhoto, GroupPhotoTags.Remove, SaqzIcons.Trash)
        }
        if (error != null) {
            Spacer(Modifier.heightIn(min = metrics.subGrid))
            Text(
                text = groupPhotoErrorText(error),
                color = SaqzTheme.colors.errorForeground,
                style = SaqzTheme.typography.support,
                modifier = Modifier.testTag(GroupPhotoTags.Error),
            )
        }
    }
}

@Composable
private fun groupPhotoErrorText(error: GroupPhotoUiError): String = when (error) {
    GroupPhotoUiError.CameraPermissionDenied -> stringResource(Res.string.group_photo_camera_permission_denied)
    GroupPhotoUiError.LibraryPermissionDenied -> stringResource(Res.string.group_photo_library_permission_denied)
    GroupPhotoUiError.SelectionFailed -> stringResource(Res.string.group_photo_selection_failed)
    GroupPhotoUiError.EncodingFailed -> stringResource(Res.string.group_photo_encoding_failed)
    GroupPhotoUiError.LoadFailed -> stringResource(Res.string.group_photo_load_failed)
    GroupPhotoUiError.UploadFailed -> stringResource(Res.string.group_photo_upload_failed)
    GroupPhotoUiError.RemovalFailed -> stringResource(Res.string.group_photo_removal_failed)
    GroupPhotoUiError.TargetUnavailable -> stringResource(Res.string.group_photo_target_unavailable)
}

@Composable
private fun GroupPhotoAction(
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
        if (icon != null) SaqzIcon(icon, tint = colors.primary)
        Text(
            text = label,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        SaqzIcon(SaqzIcons.ChevronRight, tint = colors.textSecondary)
    }
}
