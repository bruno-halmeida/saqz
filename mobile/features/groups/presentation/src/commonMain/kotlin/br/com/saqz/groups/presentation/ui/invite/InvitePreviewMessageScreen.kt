package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.InvitePreviewError
import br.com.saqz.groups.presentation.invite.InvitePreviewIntent
import br.com.saqz.groups.presentation.invite.InvitePreviewState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.invite_preview_back
import br.com.saqz.groups.resources.invite_preview_counter
import br.com.saqz.groups.resources.invite_preview_error
import br.com.saqz.groups.resources.invite_preview_message_hint
import br.com.saqz.groups.resources.invite_preview_message_label
import br.com.saqz.groups.resources.invite_preview_title
import br.com.saqz.groups.resources.invite_preview_whatsapp
import org.jetbrains.compose.resources.stringResource

internal object InvitePreviewTags { const val Screen = "invite-preview"; const val Message = "invite-preview-message" }

@Composable
internal fun InvitePreviewMessageScreen(
    state: InvitePreviewState,
    onIntent: (InvitePreviewIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(InvitePreviewTags.Screen),
    ) {
        SaqzTopAppBar(title = stringResource(Res.string.invite_preview_title, state.groupName), onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            SaqzInput(
                value = state.message,
                onValueChange = { onIntent(InvitePreviewIntent.MessageChanged(it)) },
                label = stringResource(Res.string.invite_preview_message_label),
                placeholder = stringResource(Res.string.invite_preview_message_hint),
                modifier = Modifier.fillMaxWidth().testTag(InvitePreviewTags.Message),
            )
            Text(
                text = stringResource(Res.string.invite_preview_counter, state.message.length),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            Text(
                text = state.inviteUrl,
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            state.error?.let {
                if (it == InvitePreviewError.Share) {
                    Text(stringResource(Res.string.invite_preview_error), color = SaqzTheme.colors.errorForeground)
                }
            }
            SaqzButton(
                label = stringResource(Res.string.invite_preview_whatsapp),
                onClick = { onIntent(InvitePreviewIntent.Share) },
                loading = state.isSharing,
                fullWidth = true,
            )
            SaqzButton(
                label = stringResource(Res.string.invite_preview_back),
                onClick = { onIntent(InvitePreviewIntent.Back) },
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
            )
        }
    }
}
