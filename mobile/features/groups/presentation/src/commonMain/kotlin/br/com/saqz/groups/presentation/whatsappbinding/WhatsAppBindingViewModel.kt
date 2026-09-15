package br.com.saqz.groups.presentation.whatsappbinding

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.GroupWhatsAppBinding
import br.com.saqz.groups.domain.communication.GroupWhatsAppGateway
import kotlinx.coroutines.launch

class WhatsAppBindingViewModel(
    private val groupId: String,
    private val gateway: GroupWhatsAppGateway,
) : MviViewModel<WhatsAppBindingState, WhatsAppBindingIntent, WhatsAppBindingEffect>(WhatsAppBindingState()) {
    private var generation = 0

    init {
        load()
    }

    override fun onIntent(intent: WhatsAppBindingIntent) {
        when (intent) {
            WhatsAppBindingIntent.Load -> load()
            is WhatsAppBindingIntent.ChangeInviteLink -> update { it.copy(inviteLink = intent.value) }
            WhatsAppBindingIntent.Link -> link()
            is WhatsAppBindingIntent.SetEnabled -> setEnabled(intent.enabled)
        }
    }

    private fun load() {
        val current = ++generation
        update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            val result = gateway.binding(GroupId(groupId))
            if (current != generation) return@launch
            applyBinding(result)
        }
    }

    private fun link() {
        // Intent inválido retorna cedo: link em branco não emite efeito nem altera estado.
        val inviteLink = state.value.inviteLink.trim()
        if (inviteLink.isEmpty()) return
        val current = ++generation
        update { it.copy(saving = true, error = false, confirmedGroupName = null) }
        viewModelScope.launch {
            val result = gateway.link(GroupId(groupId), inviteLink)
            if (current != generation) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    applyBinding(result)
                    update { it.copy(inviteLink = "", confirmedGroupName = result.value.groupName) }
                    emit(WhatsAppBindingEffect.Saved)
                }
                is SaqzResult.Failure -> update { it.copy(saving = false, error = true) }
            }
        }
    }

    private fun setEnabled(enabled: Boolean) {
        val current = state.value
        if (!current.canToggle || current.saving) return
        val currentGeneration = ++generation
        update { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            val result = gateway.setEnabled(GroupId(groupId), enabled)
            if (currentGeneration != generation) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    applyBinding(result)
                    emit(WhatsAppBindingEffect.Saved)
                }
                is SaqzResult.Failure -> update { it.copy(saving = false, error = true) }
            }
        }
    }

    private fun applyBinding(result: SaqzResult<GroupWhatsAppBinding, CommunicationError>) {
        when (result) {
            is SaqzResult.Success -> update {
                it.copy(
                    loading = false,
                    saving = false,
                    error = false,
                    bound = result.value.bound,
                    groupName = result.value.groupName,
                    status = result.value.status,
                )
            }
            is SaqzResult.Failure -> update { it.copy(loading = false, saving = false, error = true) }
        }
    }
}
