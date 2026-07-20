package br.com.saqz.groups.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.port.GroupPhotoCrop
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.presentation.photo.GroupPhotoError
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.resources.*
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

object GroupPhotoTags {
    const val Editor = "group-photo-editor"
    const val Preview = "group-photo-preview"
    const val Fallback = "group-photo-fallback"
    const val Progress = "group-photo-progress"
    const val Camera = "group-photo-camera"
    const val Library = "group-photo-library"
    const val Confirm = "group-photo-confirm"
    const val Cancel = "group-photo-cancel"
    const val Remove = "group-photo-remove"
    const val Retry = "group-photo-retry"
    const val Reload = "group-photo-reload"
    const val MoveLeft = "group-photo-left"
    const val MoveRight = "group-photo-right"
    const val MoveUp = "group-photo-up"
    const val MoveDown = "group-photo-down"
    const val ZoomOut = "group-photo-zoom-out"
    const val ZoomIn = "group-photo-zoom-in"
}

@Composable
fun GroupPhotoEditor(
    state: GroupPhotoState,
    groupName: String,
    canEdit: Boolean,
    optional: Boolean,
    deferUpload: Boolean,
    onIntent: (GroupPhotoIntent) -> Unit,
    onPrepared: (Boolean) -> Unit = {},
    onReloadTarget: () -> Unit = {},
    preview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Unit)? = null,
) {
    val selectedPreview = state.selection?.preview
    val visiblePreview = selectedPreview ?: state.existing?.preview
    val busy = state.stage in setOf(
        GroupPhotoStage.SELECTING,
        GroupPhotoStage.ENCODING,
        GroupPhotoStage.UPLOADING,
        GroupPhotoStage.REMOVING,
    )
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Editor),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Text(
            stringResource(Res.string.group_photo_title),
            style = SaqzTheme.typography.bodyStrong,
            color = SaqzTheme.colors.textPrimary,
        )
        if (optional) {
            Text(
                stringResource(Res.string.group_photo_optional),
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textMuted,
            )
        }
        val previewDescription = stringResource(
            if (visiblePreview == null) Res.string.group_photo_fallback_description
            else Res.string.group_photo_preview_description,
        )
        if (selectedPreview == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
                verticalAlignment = Alignment.Top,
            ) {
                PhotoPreview(
                    visiblePreview = visiblePreview,
                    preview = preview,
                    groupName = groupName,
                    busy = busy,
                    description = previewDescription,
                    modifier = Modifier.size(104.dp),
                )
                if (canEdit) {
                    Box(Modifier.weight(1f)) { PhotoSourceActions(enabled = !busy, onIntent) }
                }
            }
        } else {
            PhotoPreview(
                visiblePreview = visiblePreview,
                preview = preview,
                groupName = groupName,
                busy = busy,
                description = previewDescription,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        }

        PhotoError(state.error, state, onIntent, onReloadTarget)

        if (!canEdit) return@Column
        if (selectedPreview != null) {
            CropActions(state.crop, enabled = !busy, onIntent)
            SaqzButton(
                stringResource(if (deferUpload) Res.string.group_photo_use else Res.string.group_photo_upload),
                {
                    if (deferUpload) onPrepared(true) else onIntent(GroupPhotoIntent.Upload)
                },
                enabled = !busy,
                loading = state.stage in setOf(GroupPhotoStage.ENCODING, GroupPhotoStage.UPLOADING),
                modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Confirm),
            )
            SaqzButton(
                stringResource(Res.string.action_cancel),
                {
                    onIntent(GroupPhotoIntent.Cancel)
                    onPrepared(false)
                },
                variant = SaqzButtonVariant.Ghost,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Cancel),
            )
        } else if (state.existing != null) {
                SaqzButton(
                    stringResource(Res.string.group_photo_remove),
                    { onIntent(GroupPhotoIntent.Remove) },
                    variant = SaqzButtonVariant.Destructive,
                    enabled = !busy,
                    loading = state.stage == GroupPhotoStage.REMOVING,
                    modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Remove),
                )
        }
    }
}

@Composable
private fun PhotoPreview(
    visiblePreview: GroupPhotoPreviewHandle?,
    preview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Unit)?,
    groupName: String,
    busy: Boolean,
    description: String,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .background(SaqzTheme.colors.disabledSurface, RoundedCornerShape(SaqzTheme.metrics.cardRadius))
            .semantics { contentDescription = description }
            .testTag(GroupPhotoTags.Preview),
        contentAlignment = Alignment.Center,
    ) {
        if (visiblePreview == null || preview == null) {
            PhotoFallback(groupName, Modifier.fillMaxWidth())
        } else {
            preview(visiblePreview, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        if (busy) {
            CircularProgressIndicator(
                color = SaqzTheme.colors.primary,
                modifier = Modifier.testTag(GroupPhotoTags.Progress),
            )
        }
    }
}

@Composable
private fun PhotoFallback(groupName: String, modifier: Modifier = Modifier) {
    val initials = groupPhotoInitials(groupName)
    Box(modifier.testTag(GroupPhotoTags.Fallback), contentAlignment = Alignment.Center) {
        Text(initials, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textMuted)
    }
}

internal fun groupPhotoInitials(groupName: String): String {
    val words = groupName.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return when {
        words.isEmpty() -> "SG"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

@Composable
private fun PhotoSourceActions(enabled: Boolean, onIntent: (GroupPhotoIntent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), modifier = Modifier.fillMaxWidth()) {
        SaqzButton(
            stringResource(Res.string.group_photo_camera),
            { onIntent(GroupPhotoIntent.ChooseCamera) },
            variant = SaqzButtonVariant.Secondary,
            enabled = enabled,
            modifier = Modifier.weight(1f).testTag(GroupPhotoTags.Camera),
        )
        SaqzButton(
            stringResource(Res.string.group_photo_library),
            { onIntent(GroupPhotoIntent.ChooseLibrary) },
            variant = SaqzButtonVariant.Secondary,
            enabled = enabled,
            modifier = Modifier.weight(1f).testTag(GroupPhotoTags.Library),
        )
    }
}

@Composable
private fun CropActions(crop: GroupPhotoCrop, enabled: Boolean, onIntent: (GroupPhotoIntent) -> Unit) {
    Text(stringResource(Res.string.group_photo_crop), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textMuted)
    val delta = 0.05f / crop.zoom
    Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), modifier = Modifier.fillMaxWidth()) {
        CropButton(Res.string.group_photo_left, GroupPhotoTags.MoveLeft, enabled && crop.centerX > 0f) {
            onIntent(GroupPhotoIntent.ChangeCrop(crop.copy(centerX = (crop.centerX - delta).coerceAtLeast(0f))))
        }
        CropButton(Res.string.group_photo_right, GroupPhotoTags.MoveRight, enabled && crop.centerX < 1f) {
            onIntent(GroupPhotoIntent.ChangeCrop(crop.copy(centerX = (crop.centerX + delta).coerceAtMost(1f))))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), modifier = Modifier.fillMaxWidth()) {
        CropButton(Res.string.group_photo_up, GroupPhotoTags.MoveUp, enabled && crop.centerY > 0f) {
            onIntent(GroupPhotoIntent.ChangeCrop(crop.copy(centerY = (crop.centerY - delta).coerceAtLeast(0f))))
        }
        CropButton(Res.string.group_photo_down, GroupPhotoTags.MoveDown, enabled && crop.centerY < 1f) {
            onIntent(GroupPhotoIntent.ChangeCrop(crop.copy(centerY = (crop.centerY + delta).coerceAtMost(1f))))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), modifier = Modifier.fillMaxWidth()) {
        CropButton(Res.string.group_photo_zoom_out, GroupPhotoTags.ZoomOut, enabled && crop.zoom > 1f) {
            onIntent(GroupPhotoIntent.ChangeCrop(crop.copy(zoom = (crop.zoom - 0.25f).coerceAtLeast(1f))))
        }
        CropButton(Res.string.group_photo_zoom_in, GroupPhotoTags.ZoomIn, enabled && crop.zoom < 8f) {
            onIntent(GroupPhotoIntent.ChangeCrop(crop.copy(zoom = (crop.zoom + 0.25f).coerceAtMost(8f))))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CropButton(
    label: org.jetbrains.compose.resources.StringResource,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) = SaqzButton(
    stringResource(label),
    onClick,
    variant = SaqzButtonVariant.Ghost,
    enabled = enabled,
    modifier = Modifier.weight(1f).sizeIn(minHeight = SaqzTheme.metrics.minimumTouchTarget).testTag(tag),
)

@Composable
private fun PhotoError(
    error: GroupPhotoError?,
    state: GroupPhotoState,
    onIntent: (GroupPhotoIntent) -> Unit,
    onReloadTarget: () -> Unit,
) {
    if (error == null) return
    val label = when (error) {
        GroupPhotoError.SELECTION_FAILED -> Res.string.group_photo_selection_failed
        GroupPhotoError.ENCODING_FAILED -> Res.string.group_photo_invalid
        GroupPhotoError.UPLOAD_FAILED -> Res.string.group_photo_upload_failed
        GroupPhotoError.REMOVE_FAILED -> Res.string.group_photo_remove_failed
        GroupPhotoError.STALE_VERSION -> Res.string.group_photo_stale
        GroupPhotoError.TARGET_UNAVAILABLE -> Res.string.group_photo_target_unavailable
    }
    Text(stringResource(label), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground)
    when (error) {
        GroupPhotoError.ENCODING_FAILED, GroupPhotoError.UPLOAD_FAILED -> SaqzButton(
            stringResource(Res.string.action_retry),
            { onIntent(GroupPhotoIntent.RetryUpload) },
            variant = SaqzButtonVariant.Secondary,
            enabled = state.stage == GroupPhotoStage.CROPPING,
            modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Retry),
        )
        GroupPhotoError.REMOVE_FAILED -> SaqzButton(
            stringResource(Res.string.action_retry),
            { onIntent(GroupPhotoIntent.Remove) },
            variant = SaqzButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Retry),
        )
        GroupPhotoError.STALE_VERSION -> SaqzButton(
            stringResource(Res.string.group_setup_reload),
            onReloadTarget,
            variant = SaqzButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Reload),
        )
        else -> Unit
    }
}
