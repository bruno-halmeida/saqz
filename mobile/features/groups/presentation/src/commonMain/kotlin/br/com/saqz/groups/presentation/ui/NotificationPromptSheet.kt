package br.com.saqz.groups.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.groups.presentation.communication.NotificationPromptAudience
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.notification_prompt_body_athlete
import br.com.saqz.groups.resources.notification_prompt_body_organizer
import br.com.saqz.groups.resources.notification_prompt_enable
import br.com.saqz.groups.resources.notification_prompt_not_now
import br.com.saqz.groups.resources.notification_prompt_title
import org.jetbrains.compose.resources.stringResource

object NotificationPromptTags {
    const val Sheet = "notification-prompt"
    const val Enable = "notification-prompt-enable"
    const val NotNow = "notification-prompt-not-now"
}

/**
 * Explica os avisos antes do pedido do sistema: "Ligar avisos" abre o pedido, "Agora não" (ou fechar)
 * guarda a escolha. Colocar como último filho de um Box que ocupa a tela.
 */
@Composable
fun NotificationPromptSheet(
    audience: NotificationPromptAudience,
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzBottomSheet(
        open = true,
        onClose = onNotNow,
        modifier = modifier.testTag(NotificationPromptTags.Sheet),
        title = stringResource(Res.string.notification_prompt_title),
        description = stringResource(
            when (audience) {
                NotificationPromptAudience.Athlete -> Res.string.notification_prompt_body_athlete
                NotificationPromptAudience.Organizer -> Res.string.notification_prompt_body_organizer
            },
        ),
        splitFooter = {
            SaqzButton(
                stringResource(Res.string.notification_prompt_not_now),
                onNotNow,
                Modifier.weight(1f).testTag(NotificationPromptTags.NotNow),
                SaqzButtonVariant.Secondary,
            )
            SaqzButton(
                stringResource(Res.string.notification_prompt_enable),
                onEnable,
                Modifier.weight(1f).testTag(NotificationPromptTags.Enable),
            )
        },
        content = {},
    )
}
