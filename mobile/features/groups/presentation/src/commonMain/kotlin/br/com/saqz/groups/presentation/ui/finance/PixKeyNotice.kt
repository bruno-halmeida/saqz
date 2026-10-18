package br.com.saqz.groups.presentation.ui.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.pix_key_notice_action
import br.com.saqz.groups.resources.pix_key_notice_message
import org.jetbrains.compose.resources.stringResource

internal object PixKeyNoticeTags {
    const val Action = "pix-key-notice-action"
}

/**
 * DEB-058: há cobrança pendente e o grupo não tem chave Pix. Quem administra vê o aviso com o
 * atalho para a configuração do grupo; não tem "adiar", some quando a chave é cadastrada.
 */
@Composable
internal fun PixKeyNotice(
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) = SaqzCard(modifier = modifier, tone = SaqzCardTone.Soft) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.warningForeground)
        Text(
            text = stringResource(Res.string.pix_key_notice_message),
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
    SaqzButton(
        label = stringResource(Res.string.pix_key_notice_action),
        onClick = onOpenSetup,
        variant = SaqzButtonVariant.Secondary,
        fullWidth = true,
        modifier = Modifier.testTag(PixKeyNoticeTags.Action),
    )
}
