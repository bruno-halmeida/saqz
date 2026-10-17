package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomePendingRequestUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_first_access_create
import br.com.saqz.groups.resources.home_first_access_invite
import br.com.saqz.groups.resources.home_pending_cancel_back
import br.com.saqz.groups.resources.home_pending_cancel_body
import br.com.saqz.groups.resources.home_pending_cancel_title
import br.com.saqz.groups.resources.home_pending_create_title
import br.com.saqz.groups.resources.home_pending_request_cancel
import br.com.saqz.groups.resources.home_pending_request_status
import org.jetbrains.compose.resources.stringResource

internal const val HomePendingRequestTag = "home-pending-request"
internal const val HomePendingCreateTag = "home-pending-create"
internal const val HomePendingCreateButtonTag = "home-pending-create-button"
internal const val HomePendingInviteButtonTag = "home-pending-invite-button"
internal const val HomePendingCancelTag = "home-pending-cancel"
internal const val HomePendingCancelSheetTag = "home-pending-cancel-sheet"
internal const val HomePendingCancelConfirmTag = "home-pending-cancel-confirm"

/**
 * Quem pediu para entrar num grupo com aprovação e saiu da tela "Pedido enviado" precisa ver que o
 * pedido existe: cartão próprio, com o nome do grupo e o estado. Só informa; não promete aviso.
 */
@Composable
internal fun HomePendingRequestNotice(
    request: HomePendingRequestUi,
    cancelEnabled: Boolean,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzCard(modifier = modifier.fillMaxWidth().testTag(HomePendingRequestTag)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                text = request.groupName,
                style = SaqzTheme.typography.body,
                fontWeight = FontWeight.SemiBold,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.home_pending_request_status),
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
            SaqzButton(
                label = stringResource(Res.string.home_pending_request_cancel),
                onClick = { onIntent(HomeIntent.RequestCancelEntryRequest(request.groupId)) },
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                enabled = cancelEnabled,
                modifier = Modifier.testTag(HomePendingCancelTag),
            )
        }
    }
}

/** Confirmação de "Cancelar pedido"; só o botão de dentro chama a API. */
@Composable
internal fun HomeCancelEntryRequestSheet(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    SaqzBottomSheet(
        open = state.cancelEntryCandidate != null,
        onClose = { onIntent(HomeIntent.DismissCancelEntryRequest) },
        modifier = Modifier.testTag(HomePendingCancelSheetTag),
        title = stringResource(
            Res.string.home_pending_cancel_title,
            state.cancelEntryCandidate?.groupName.orEmpty(),
        ),
        description = stringResource(Res.string.home_pending_cancel_body),
        splitFooter = {
            SaqzButton(
                label = stringResource(Res.string.home_pending_cancel_back),
                onClick = { onIntent(HomeIntent.DismissCancelEntryRequest) },
                variant = SaqzButtonVariant.Ghost,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.home_pending_request_cancel),
                onClick = { onIntent(HomeIntent.ConfirmCancelEntryRequest) },
                variant = SaqzButtonVariant.Danger,
                modifier = Modifier.weight(1f).testTag(HomePendingCancelConfirmTag),
            )
        },
    ) {}
}

/** Com pedido pendente, criar grupo vira ação secundária: o destaque da Início é o pedido. */
@Composable
internal fun HomePendingCreateGroup(onIntent: (HomeIntent) -> Unit, modifier: Modifier = Modifier) {
    SaqzCard(modifier = modifier.fillMaxWidth().testTag(HomePendingCreateTag)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            Text(
                text = stringResource(Res.string.home_pending_create_title),
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
            ) {
                SaqzButton(
                    label = stringResource(Res.string.home_first_access_create),
                    onClick = { onIntent(HomeIntent.CreateGroup) },
                    variant = SaqzButtonVariant.Secondary,
                    fullWidth = true,
                    modifier = Modifier.weight(1f).testTag(HomePendingCreateButtonTag),
                )
                SaqzButton(
                    label = stringResource(Res.string.home_first_access_invite),
                    onClick = { onIntent(HomeIntent.OpenInviteSheet) },
                    variant = SaqzButtonVariant.Ghost,
                    fullWidth = true,
                    modifier = Modifier.weight(1f).testTag(HomePendingInviteButtonTag),
                )
            }
        }
    }
}
