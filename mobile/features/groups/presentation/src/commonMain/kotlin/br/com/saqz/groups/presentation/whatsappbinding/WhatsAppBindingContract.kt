package br.com.saqz.groups.presentation.whatsappbinding

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus

/**
 * O vínculo do grupo Saqz com o grupo do WhatsApp. `loading` cobre só a leitura inicial;
 * `saving` cobre vincular/reabilitar/desabilitar. `confirmedGroupName` é o nome devolvido
 * pelo backend no vínculo, exibido como confirmação do grupo que entrou.
 */
@Immutable
data class WhatsAppBindingState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Boolean = false,
    val bound: Boolean = false,
    val groupName: String? = null,
    val status: GroupWhatsAppStatus = GroupWhatsAppStatus.NONE,
    val inviteLink: String = "",
    val confirmedGroupName: String? = null,
) {
    val canToggle: Boolean = bound && status != GroupWhatsAppStatus.NONE
}

sealed interface WhatsAppBindingIntent {
    data object Load : WhatsAppBindingIntent

    data class ChangeInviteLink(val value: String) : WhatsAppBindingIntent

    data object Link : WhatsAppBindingIntent

    data class SetEnabled(val enabled: Boolean) : WhatsAppBindingIntent
}

sealed interface WhatsAppBindingEffect {
    /** Confirmação transitória de que o vínculo ou o estado foi persistido. */
    data object Saved : WhatsAppBindingEffect
}
