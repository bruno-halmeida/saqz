package br.com.saqz.groups.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.pix_copied
import org.jetbrains.compose.resources.stringResource

internal const val PIX_COPIED_TOAST_TAG = "pix-copied-toast"

/**
 * DEB-151: copiar o Pix do grupo avisa na tela (no iPhone o sistema não mostra nada).
 * O [modifier] posiciona — quem chama decide o canto do `Box`.
 */
@Composable
internal fun PixCopiedToast(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (!visible) return
    SaqzToast(
        visible = true,
        onDismiss = onDismiss,
        modifier = modifier
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(PIX_COPIED_TOAST_TAG),
    ) {
        SaqzToastText(text = stringResource(Res.string.pix_copied))
    }
}
