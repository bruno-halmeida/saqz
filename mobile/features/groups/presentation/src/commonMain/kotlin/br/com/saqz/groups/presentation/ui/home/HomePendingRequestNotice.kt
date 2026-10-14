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
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_first_access_create
import br.com.saqz.groups.resources.home_first_access_invite
import br.com.saqz.groups.resources.home_pending_create_title
import br.com.saqz.groups.resources.home_pending_request_status
import org.jetbrains.compose.resources.stringResource

internal const val HomePendingRequestTag = "home-pending-request"
internal const val HomePendingCreateTag = "home-pending-create"
internal const val HomePendingCreateButtonTag = "home-pending-create-button"
internal const val HomePendingInviteButtonTag = "home-pending-invite-button"

/**
 * Quem pediu para entrar num grupo com aprovação e saiu da tela "Pedido enviado" precisa ver que o
 * pedido existe: cartão próprio, com o nome do grupo e o estado. Só informa; não promete aviso.
 */
@Composable
internal fun HomePendingRequestNotice(groupName: String, modifier: Modifier = Modifier) {
    SaqzCard(modifier = modifier.fillMaxWidth().testTag(HomePendingRequestTag)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                text = groupName,
                style = SaqzTheme.typography.body,
                fontWeight = FontWeight.SemiBold,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.home_pending_request_status),
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
        }
    }
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
