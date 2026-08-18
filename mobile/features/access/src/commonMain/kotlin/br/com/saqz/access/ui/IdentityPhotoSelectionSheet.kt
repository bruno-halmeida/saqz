package br.com.saqz.access.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.identity_photo_gallery
import br.com.saqz.access.resources.identity_photo_sheet_title
import br.com.saqz.access.resources.identity_photo_take
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object IdentityPhotoTags {
    const val Sheet = "identity-photo-sheet"
    const val Camera = "identity-photo-take"
    const val Library = "identity-photo-library"
}

/**
 * Folha da 1c: câmera ou galeria. A foto é opcional e ainda não subiu, então não há
 * remover — escolher de novo substitui a imagem local.
 */
@Composable
internal fun IdentityPhotoSelectionSheet(
    open: Boolean,
    onClose: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzBottomSheet(
        open = open,
        onClose = onClose,
        modifier = modifier.testTag(IdentityPhotoTags.Sheet),
        title = stringResource(Res.string.identity_photo_sheet_title),
    ) {
        IdentityPhotoAction(
            label = stringResource(Res.string.identity_photo_take),
            onClick = onTakePhoto,
            tag = IdentityPhotoTags.Camera,
            icon = SaqzIcons.Camera,
        )
        IdentityPhotoAction(
            label = stringResource(Res.string.identity_photo_gallery),
            onClick = onChooseFromGallery,
            tag = IdentityPhotoTags.Library,
        )
    }
}

@Composable
private fun IdentityPhotoAction(
    label: String,
    onClick: () -> Unit,
    tag: String,
    icon: ImageVector? = null,
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
