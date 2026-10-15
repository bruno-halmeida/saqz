package br.com.saqz.composeapp.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.groups.presentation.communication.NotificationPrompt
import br.com.saqz.groups.presentation.ui.NotificationPromptSheet
import org.koin.compose.koinInject

/**
 * A folha de pré-pedido de aviso, por cima de qualquer tela: quem a aciona são os ViewModels (primeira
 * presença respondida, primeiro jogo criado), e a política de quando mostrar mora em [NotificationPrompt].
 * Último filho do Box que ocupa a tela, como toda folha.
 */
@Composable
internal fun NotificationPromptHost(prompt: NotificationPrompt = koinInject()) {
    val audience by prompt.audience.collectAsStateWithLifecycle()
    audience?.let { NotificationPromptSheet(it, onEnable = prompt::enable, onNotNow = prompt::dismiss) }
}
