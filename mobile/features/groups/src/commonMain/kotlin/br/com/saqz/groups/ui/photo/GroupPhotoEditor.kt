package br.com.saqz.groups.ui.photo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzBottomSheet
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.presentation.photo.GroupPhotoError
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.resources.*
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

object GroupPhotoTags {
    const val Editor = "group-photo-editor"
    const val Preview = "group-photo-preview"
    const val Fallback = "group-photo-fallback"
    const val Progress = "group-photo-progress"
    const val Camera = "group-photo-camera"
    const val Library = "group-photo-library"
    const val Add = "group-photo-add"
    const val Picker = "group-photo-picker"
    const val Confirm = "group-photo-confirm"
    const val Cancel = "group-photo-cancel"
    const val Remove = "group-photo-remove"
    const val Retry = "group-photo-retry"
    const val Reload = "group-photo-reload"
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
    preview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Boolean)? = null,
    sourceActionBorderColor: Color? = null,
    compactIdle: Boolean = false,
    prepared: Boolean = false,
) {
    val selectedPreview = state.selection?.preview
    val visiblePreview = selectedPreview ?: state.existing?.preview
    val busy = state.stage in setOf(
        GroupPhotoStage.SELECTING,
        GroupPhotoStage.ENCODING,
        GroupPhotoStage.UPLOADING,
        GroupPhotoStage.REMOVING,
    )
    var sourceSheetVisible by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Editor),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        if (!compactIdle) {
            Text(
                stringResource(Res.string.group_photo_title),
                style = SaqzTheme.typography.bodyStrong,
                color = SaqzTheme.colors.textPrimary,
            )
        }
        if (optional && !compactIdle) {
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
        if (selectedPreview == null || (compactIdle && prepared)) {
            if (compactIdle) {
                val pickerModifier = if (canEdit) {
                    Modifier.clickable(
                        enabled = !busy,
                        onClickLabel = stringResource(Res.string.group_photo_add),
                    ) { sourceSheetVisible = true }.testTag(GroupPhotoTags.Picker)
                } else {
                    Modifier
                }
                Box(
                    Modifier.size(128.dp).align(Alignment.CenterHorizontally).then(pickerModifier),
                ) {
                    PhotoPreview(
                        visiblePreview = visiblePreview,
                        preview = preview,
                        groupName = groupName,
                        busy = busy,
                        description = previewDescription,
                        photoIconFallback = true,
                        modifier = Modifier.size(112.dp).align(Alignment.TopCenter),
                    )
                    if (canEdit) {
                        Box(
                            Modifier.size(44.dp).align(Alignment.BottomEnd)
                                .clickable(
                                    enabled = !busy,
                                    onClickLabel = stringResource(Res.string.group_photo_add),
                                ) { sourceSheetVisible = true }
                                .testTag(GroupPhotoTags.Add),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(30.dp)
                                    .background(SaqzTheme.colors.surface, CircleShape)
                                    .border(1.dp, sourceActionBorderColor ?: SaqzTheme.colors.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                GroupPhotoMaterialIcon(Res.drawable.material_add, SaqzTheme.colors.primary, 16.dp)
                            }
                        }
                    }
                }
            } else {
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
                        Box(Modifier.weight(1f)) {
                            PhotoSourceActions(
                                enabled = !busy,
                                borderColor = sourceActionBorderColor,
                                onIntent = onIntent,
                            )
                        }
                    }
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
        if (selectedPreview != null && !prepared) {
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
        } else if (state.existing != null && !compactIdle) {
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

    if (sourceSheetVisible) {
        SaqzBottomSheet(
            title = stringResource(Res.string.group_photo_add),
            onCloseRequest = { sourceSheetVisible = false },
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            showCloseAction = false,
            primaryAction = {
                SaqzButton(
                    stringResource(Res.string.action_cancel),
                    { sourceSheetVisible = false },
                    variant = SaqzButtonVariant.Ghost,
                    borderColor = sourceActionBorderColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
                PhotoSheetAction(Res.string.group_photo_take, GroupPhotoTags.Camera, sourceActionBorderColor) {
                    sourceSheetVisible = false
                    onPrepared(false)
                    onIntent(GroupPhotoIntent.ChooseCamera)
                }
                PhotoSheetAction(Res.string.group_photo_choose_library, GroupPhotoTags.Library, sourceActionBorderColor) {
                    sourceSheetVisible = false
                    onPrepared(false)
                    onIntent(GroupPhotoIntent.ChooseLibrary)
                }
                SaqzButton(
                    stringResource(Res.string.group_photo_remove),
                    {
                        sourceSheetVisible = false
                        onIntent(GroupPhotoIntent.Remove)
                    },
                    variant = SaqzButtonVariant.Ghost,
                    borderColor = sourceActionBorderColor,
                    enabled = state.existing != null || state.selection != null,
                    modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Remove),
                )
            }
        }
    }
}

@Composable
private fun PhotoSheetAction(label: StringResource, tag: String, borderColor: Color?, onClick: () -> Unit) {
    SaqzButton(
        stringResource(label),
        onClick,
        variant = SaqzButtonVariant.Secondary,
        borderColor = borderColor,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun PhotoPreview(
    visiblePreview: GroupPhotoPreviewHandle?,
    preview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Boolean)?,
    groupName: String,
    busy: Boolean,
    description: String,
    photoIconFallback: Boolean = false,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(SaqzTheme.metrics.cardRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(SaqzTheme.colors.disabledSurface, shape)
            .semantics { contentDescription = description }
            .testTag(GroupPhotoTags.Preview),
        contentAlignment = Alignment.Center,
    ) {
        val rendered = if (visiblePreview != null && preview != null) {
            preview(visiblePreview, Modifier.fillMaxWidth().aspectRatio(1f))
        } else {
            false
        }
        if (!rendered) {
            PhotoFallback(groupName, photoIconFallback, Modifier.fillMaxWidth())
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
private fun PhotoFallback(groupName: String, photoIcon: Boolean, modifier: Modifier = Modifier) {
    val initials = groupPhotoInitials(groupName)
    Box(modifier.testTag(GroupPhotoTags.Fallback), contentAlignment = Alignment.Center) {
        if (photoIcon) {
            GroupPhotoMaterialIcon(Res.drawable.material_photo_camera, SaqzTheme.colors.textMuted, 36.dp)
        } else {
            Text(initials, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textMuted)
        }
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
private fun PhotoSourceActions(enabled: Boolean, borderColor: Color?, onIntent: (GroupPhotoIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), modifier = Modifier.fillMaxWidth()) {
        SaqzButton(
            stringResource(Res.string.group_photo_camera),
            { onIntent(GroupPhotoIntent.ChooseCamera) },
            variant = SaqzButtonVariant.Secondary,
            enabled = enabled,
            borderColor = borderColor,
            modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Camera),
            leadingContent = { color -> GroupPhotoMaterialIcon(Res.drawable.material_photo_camera, color) },
        )
        SaqzButton(
            stringResource(Res.string.group_photo_library),
            { onIntent(GroupPhotoIntent.ChooseLibrary) },
            variant = SaqzButtonVariant.Secondary,
            enabled = enabled,
            borderColor = borderColor,
            modifier = Modifier.fillMaxWidth().testTag(GroupPhotoTags.Library),
            leadingContent = { color -> GroupPhotoMaterialIcon(Res.drawable.material_photo_library, color) },
        )
    }
}

@Composable
private fun GroupPhotoMaterialIcon(resource: DrawableResource, color: Color, size: androidx.compose.ui.unit.Dp = 20.dp) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(size).clearAndSetSemantics {},
    )
}

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

@Preview
@Composable
private fun GroupPhotoEditorPreview() = SaqzTheme {
    GroupPhotoEditor(
        state = GroupPhotoState(),
        groupName = "Saqz Runners",
        canEdit = true,
        optional = true,
        deferUpload = true,
        onIntent = {},
    )
}
