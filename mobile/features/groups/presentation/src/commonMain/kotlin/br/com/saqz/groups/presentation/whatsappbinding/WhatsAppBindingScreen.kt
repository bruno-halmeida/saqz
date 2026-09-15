package br.com.saqz.groups.presentation.whatsappbinding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
import br.com.saqz.groups.presentation.ui.components.GroupFormCard
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.whatsapp_binding_confirmation
import br.com.saqz.groups.resources.whatsapp_binding_disable_action
import br.com.saqz.groups.resources.whatsapp_binding_empty_description
import br.com.saqz.groups.resources.whatsapp_binding_empty_title
import br.com.saqz.groups.resources.whatsapp_binding_enable_action
import br.com.saqz.groups.resources.whatsapp_binding_error
import br.com.saqz.groups.resources.whatsapp_binding_group_label
import br.com.saqz.groups.resources.whatsapp_binding_invite_label
import br.com.saqz.groups.resources.whatsapp_binding_invite_placeholder
import br.com.saqz.groups.resources.whatsapp_binding_link_action
import br.com.saqz.groups.resources.whatsapp_binding_retry
import br.com.saqz.groups.resources.whatsapp_binding_status_active
import br.com.saqz.groups.resources.whatsapp_binding_status_broken
import br.com.saqz.groups.resources.whatsapp_binding_status_disabled
import br.com.saqz.groups.resources.whatsapp_binding_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object WhatsAppBindingTags {
    const val Loading = "whatsapp-binding-loading"
    const val Error = "whatsapp-binding-error"
    const val Retry = "whatsapp-binding-retry"
    const val Empty = "whatsapp-binding-empty"
    const val InviteLink = "whatsapp-binding-invite-link"
    const val Link = "whatsapp-binding-link"
    const val GroupName = "whatsapp-binding-group-name"
    const val Status = "whatsapp-binding-status"
    const val Toggle = "whatsapp-binding-toggle"
    const val Confirmation = "whatsapp-binding-confirmation"
}

/**
 * Configurações avançadas do gestor: colar o link de convite, confirmar o nome do grupo
 * devolvido pelo backend e desabilitar/reabilitar o canal. Tela pura — recebe o estado e
 * devolve intents; quem fala com a rede é a ViewModel.
 */
@Composable
fun WhatsAppBindingScreen(
    state: WhatsAppBindingState,
    onIntent: (WhatsAppBindingIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(title = stringResource(Res.string.whatsapp_binding_title), onBack = onBack)
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().testTag(WhatsAppBindingTags.Loading),
                contentAlignment = Alignment.Center,
            ) {
                SaqzSpinner()
            }
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            if (state.error) WhatsAppBindingError(onRetry = { onIntent(WhatsAppBindingIntent.Load) })
            state.confirmedGroupName?.let { WhatsAppBindingConfirmation(it) }
            if (state.bound) WhatsAppBindingBound(state, onIntent) else WhatsAppBindingEmpty(state, onIntent)
        }
    }
}

@Composable
private fun WhatsAppBindingError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(WhatsAppBindingTags.Error),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        Text(
            text = stringResource(Res.string.whatsapp_binding_error),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.errorForeground,
        )
        SaqzButton(
            label = stringResource(Res.string.whatsapp_binding_retry),
            onClick = onRetry,
            variant = SaqzButtonVariant.Secondary,
            modifier = Modifier.testTag(WhatsAppBindingTags.Retry),
        )
    }
}

@Composable
private fun WhatsAppBindingConfirmation(groupName: String) {
    Text(
        text = stringResource(Res.string.whatsapp_binding_confirmation, groupName),
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.primary,
        modifier = Modifier.testTag(WhatsAppBindingTags.Confirmation),
    )
}

@Composable
private fun WhatsAppBindingEmpty(state: WhatsAppBindingState, onIntent: (WhatsAppBindingIntent) -> Unit) {
    GroupFormCard(
        title = stringResource(Res.string.whatsapp_binding_empty_title),
        hint = stringResource(Res.string.whatsapp_binding_empty_description),
        modifier = Modifier.testTag(WhatsAppBindingTags.Empty),
    ) {
        SaqzInput(
            value = state.inviteLink,
            onValueChange = { onIntent(WhatsAppBindingIntent.ChangeInviteLink(it)) },
            label = stringResource(Res.string.whatsapp_binding_invite_label),
            placeholder = stringResource(Res.string.whatsapp_binding_invite_placeholder),
            enabled = !state.saving,
            modifier = Modifier.testTag(WhatsAppBindingTags.InviteLink),
        )
        SaqzButton(
            label = stringResource(Res.string.whatsapp_binding_link_action),
            onClick = { onIntent(WhatsAppBindingIntent.Link) },
            enabled = state.inviteLink.isNotBlank() && !state.saving,
            loading = state.saving,
            fullWidth = true,
            modifier = Modifier.testTag(WhatsAppBindingTags.Link),
        )
    }
}

@Composable
private fun WhatsAppBindingBound(state: WhatsAppBindingState, onIntent: (WhatsAppBindingIntent) -> Unit) {
    GroupFormCard(title = stringResource(Res.string.whatsapp_binding_group_label)) {
        Text(
            text = state.groupName.orEmpty(),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.testTag(WhatsAppBindingTags.GroupName),
        )
        Text(
            text = stringResource(state.status.label()),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.testTag(WhatsAppBindingTags.Status),
        )
        val active = state.status == GroupWhatsAppStatus.ACTIVE
        SaqzButton(
            label = stringResource(
                if (active) Res.string.whatsapp_binding_disable_action else Res.string.whatsapp_binding_enable_action,
            ),
            onClick = { onIntent(WhatsAppBindingIntent.SetEnabled(!active)) },
            variant = SaqzButtonVariant.Secondary,
            enabled = !state.saving,
            loading = state.saving,
            fullWidth = true,
            modifier = Modifier.testTag(WhatsAppBindingTags.Toggle),
        )
    }
}

private fun GroupWhatsAppStatus.label(): StringResource = when (this) {
    GroupWhatsAppStatus.ACTIVE -> Res.string.whatsapp_binding_status_active
    GroupWhatsAppStatus.DISABLED -> Res.string.whatsapp_binding_status_disabled
    GroupWhatsAppStatus.BROKEN -> Res.string.whatsapp_binding_status_broken
    GroupWhatsAppStatus.NONE -> Res.string.whatsapp_binding_empty_title
}

@Preview
@Composable
private fun WhatsAppBindingNonePreview() = SaqzTheme {
    WhatsAppBindingScreen(
        state = WhatsAppBindingState(loading = false, inviteLink = "chat.whatsapp.com/abc123"),
        onIntent = {},
        onBack = {},
    )
}

@Preview
@Composable
private fun WhatsAppBindingActivePreview() = SaqzTheme {
    WhatsAppBindingScreen(
        state = WhatsAppBindingState(
            loading = false,
            bound = true,
            groupName = "Vôlei do CERET",
            status = GroupWhatsAppStatus.ACTIVE,
        ),
        onIntent = {},
        onBack = {},
    )
}

@Preview
@Composable
private fun WhatsAppBindingBrokenPreview() = SaqzTheme {
    WhatsAppBindingScreen(
        state = WhatsAppBindingState(
            loading = false,
            bound = true,
            groupName = "Vôlei do CERET",
            status = GroupWhatsAppStatus.BROKEN,
        ),
        onIntent = {},
        onBack = {},
    )
}
