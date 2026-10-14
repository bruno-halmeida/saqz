package br.com.saqz.groups.presentation.ui.home

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_pending_request
import org.jetbrains.compose.resources.stringResource

internal const val HomePendingRequestTag = "home-pending-request"

/**
 * Quem pediu para entrar num grupo com aprovação e saiu da tela "Pedido enviado" precisa ver que o
 * pedido existe. Só informa o estado; não promete aviso.
 */
@Composable
internal fun HomePendingRequestNotice(groupName: String, modifier: Modifier = Modifier) {
    SaqzCard(modifier = modifier.testTag(HomePendingRequestTag)) {
        Text(
            text = stringResource(Res.string.home_pending_request, groupName),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textPrimary,
        )
    }
}
