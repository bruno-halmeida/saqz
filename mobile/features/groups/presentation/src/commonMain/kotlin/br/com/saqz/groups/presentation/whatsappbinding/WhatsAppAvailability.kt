package br.com.saqz.groups.presentation.whatsappbinding

import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.GroupWhatsAppGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Se o WhatsApp do Saqz está ligado no servidor. Quem decide é o backend
 * (`saqz.notifications.whatsapp.enabled`): o app nasce desligado e só liga com a resposta dele,
 * então ligar e desligar não pede versão nova. Falha mantém o último valor conhecido.
 */
class WhatsAppAvailability(private val gateway: GroupWhatsAppGateway) {
    private val mutableEnabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    suspend fun refresh(): Boolean {
        val result = gateway.availability()
        if (result is SaqzResult.Success) mutableEnabled.value = result.value
        return mutableEnabled.value
    }
}
