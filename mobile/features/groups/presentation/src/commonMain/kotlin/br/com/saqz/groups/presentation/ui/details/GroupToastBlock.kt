package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupDetailsToast
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_toast_game_created
import br.com.saqz.groups.resources.home_toast_confirmed
import br.com.saqz.groups.resources.home_toast_declined
import br.com.saqz.groups.resources.home_toast_pix_copied
import br.com.saqz.groups.resources.home_toast_waitlisted
import br.com.saqz.groups.resources.home_toast_waitlisted_late
import br.com.saqz.groups.resources.home_toast_waitlisted_manual
import org.jetbrains.compose.resources.stringResource

/**
 * O retorno da ação recém-concluída (resposta de presença, chave Pix copiada), com os textos da
 * Início. Sem toast o bloco não emite nada; o `SaqzToast` some sozinho e devolve `DismissToast`.
 */
@Composable
internal fun GroupToastBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toast = state.toast ?: return
    SaqzToast(
        visible = true,
        onDismiss = { onIntent(GroupDetailsIntent.DismissToast) },
        modifier = modifier
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(GroupDetailsTags.Toast),
    ) {
        SaqzToastText(
            text = stringResource(
                when (toast) {
                    GroupDetailsToast.Confirmed -> Res.string.home_toast_confirmed
                    GroupDetailsToast.Declined -> Res.string.home_toast_declined
                    GroupDetailsToast.Waitlisted -> Res.string.home_toast_waitlisted
                    GroupDetailsToast.WaitlistedManual -> Res.string.home_toast_waitlisted_manual
                    GroupDetailsToast.WaitlistedAfterDeadline -> Res.string.home_toast_waitlisted_late
                    GroupDetailsToast.PixCopied -> Res.string.home_toast_pix_copied
                    GroupDetailsToast.GameCreated -> Res.string.group_details_toast_game_created
                },
            ),
        )
    }
}
