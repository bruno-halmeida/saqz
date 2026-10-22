package br.com.saqz.groups.presentation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.refresh_failed
import org.jetbrains.compose.resources.stringResource

internal const val REFRESH_FAILED_TOAST_TAG = "refresh-failed-toast"

/**
 * Puxar para atualizar que falhou (GER-002): o que estava na tela fica e o aviso sai por cima.
 * O [modifier] posiciona — quem chama decide o canto do `Box`.
 */
@Composable
internal fun RefreshFailedToast(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (!visible) return
    SaqzToast(
        visible = true,
        onDismiss = onDismiss,
        modifier = modifier
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(REFRESH_FAILED_TOAST_TAG),
    ) {
        SaqzToastText(text = stringResource(Res.string.refresh_failed))
    }
}
