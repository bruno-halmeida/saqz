package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.communication_failure
import br.com.saqz.groups.resources.communication_reminded
import br.com.saqz.groups.resources.group_details_notify_pending
import org.jetbrains.compose.resources.stringResource

/**
 * Andaime (T): o botão "Avisar quem falta confirmar" e o retorno dele, fora do card de
 * contadores. O C5 troca o corpo pelo bloco "Esperando você". O texto de sucesso
 * (`communication_reminded`) é contrato do e2e e precisa continuar na árvore.
 */
@Composable
internal fun GroupWaitingBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isAdmin || state.nextGame == null) return
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.Waiting),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzButton(
            label = stringResource(Res.string.group_details_notify_pending),
            onClick = { onIntent(GroupDetailsIntent.NotifyPending) },
            modifier = Modifier.testTag(GroupDetailsTags.NotifyPending),
            variant = SaqzButtonVariant.Ghost,
            fullWidth = true,
            leadingContent = { tint -> SaqzIcon(SaqzIcons.Megaphone, tint = tint) },
        )
        if (state.notifying) SaqzSpinner()
        if (state.notificationFailed) Text(stringResource(Res.string.communication_failure))
        state.notifiedCount?.let { Text(stringResource(Res.string.communication_reminded, it)) }
    }
}
