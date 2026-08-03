package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.InviteQrError
import br.com.saqz.groups.presentation.invite.InviteQrIntent
import br.com.saqz.groups.presentation.invite.InviteQrState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.invite_qr_error_save
import br.com.saqz.groups.resources.invite_qr_error_share
import br.com.saqz.groups.resources.invite_qr_save
import br.com.saqz.groups.resources.invite_qr_share
import br.com.saqz.groups.resources.invite_qr_title
import br.com.saqz.groups.resources.invite_qr_validity
import org.jetbrains.compose.resources.stringResource

internal object InviteQrTags {
    const val Screen = "invite-qr"
    const val Image = "invite-qr-image"
}

@Composable
internal fun InviteQrScreen(
    state: InviteQrState,
    onIntent: (InviteQrIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag(InviteQrTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.invite_qr_title), onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(SaqzTheme.metrics.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            Text(state.groupName, style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
            Text(
                stringResource(Res.string.invite_qr_validity),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            state.pngBytes?.let {
                InviteQrImage(
                    it,
                    stringResource(Res.string.invite_qr_title),
                    Modifier.testTag(InviteQrTags.Image),
                )
            }
            state.error?.let {
                Text(
                    text = stringResource(
                        if (it == InviteQrError.Save) Res.string.invite_qr_error_save
                        else Res.string.invite_qr_error_share,
                    ),
                    color = SaqzTheme.colors.errorForeground,
                )
            }
            SaqzButton(
                label = stringResource(Res.string.invite_qr_save),
                onClick = { onIntent(InviteQrIntent.Save) },
                loading = state.isSaving,
                fullWidth = true,
            )
            SaqzButton(
                label = stringResource(Res.string.invite_qr_share),
                onClick = { onIntent(InviteQrIntent.Share) },
                loading = state.isSharing,
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
            )
        }
    }
}
