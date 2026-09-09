package br.com.saqz.groups.presentation.communication

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationGateway
import kotlinx.coroutines.launch

class NotificationCenterViewModel(private val settings: Boolean, private val gateway: CommunicationGateway) :
    MviViewModel<NotificationCenterState, NotificationCenterIntent, NotificationCenterEffect>(NotificationCenterState()) {
    private var generation = 0
    init { load() }
    override fun onIntent(intent: NotificationCenterIntent) {
        if (state.value.busy) return
        when (intent) {
            NotificationCenterIntent.Refresh -> load()
            NotificationCenterIntent.More -> if (!settings && state.value.nextCursor != null) load(more = true)
            NotificationCenterIntent.Save -> if (settings && !state.value.loading && state.value.error == null) save()
            is NotificationCenterIntent.Preferences -> update { it.copy(preferences = intent.value, saved = false, actionFailed = false) }
            is NotificationCenterIntent.Open -> open(intent.sequence)
        }
    }
    private fun load(more: Boolean = false) {
        val request = ++generation
        val before = if (more) state.value.nextCursor else null
        update { it.copy(loading = !more, busy = more, error = null, actionFailed = false, saved = false) }
        viewModelScope.launch {
            if (settings) {
                when (val result = gateway.preferences()) {
                    is SaqzResult.Success -> if (request == generation) update { it.copy(loading = false, preferences = result.value) }
                    is SaqzResult.Failure -> if (request == generation) update {
                        it.copy(loading = false, error = result.error.toUiError())
                    }
                }
            } else when (val result = gateway.inbox(before)) {
                is SaqzResult.Success -> if (request == generation) update {
                    it.copy(loading = false, busy = false, nextCursor = result.value.nextCursor, items =
                        ((if (more) it.items else emptyList()) + result.value.items.map { notification ->
                            val message = notification.message
                            NotificationUi(
                                notification.sequence, message.groupId.value, message.channel,
                                message.gameId, message.toThreadUi(), notification.read,
                            )
                        }).distinctBy { item -> item.sequence })
                }
                is SaqzResult.Failure -> if (request == generation) update {
                    it.copy(loading = false, busy = false, actionFailed = more, error = if (more) null else result.error.toUiError())
                }
            }
        }
    }
    private fun save() {
        val preferences = state.value.preferences
        update { it.copy(busy = true, actionFailed = false, saved = false) }
        viewModelScope.launch {
            when (val result = gateway.savePreferences(preferences)) {
                is SaqzResult.Success -> update { it.copy(busy = false, preferences = result.value, saved = true) }
                is SaqzResult.Failure -> update { it.copy(busy = false, actionFailed = true) }
            }
        }
    }
    private fun open(sequence: Long) {
        val item = state.value.items.find { it.sequence == sequence } ?: return
        update { it.copy(busy = true, actionFailed = false) }
        viewModelScope.launch {
            when (gateway.markRead(sequence)) {
                is SaqzResult.Success -> {
                    update {
                        it.copy(busy = false, items = it.items.map { row -> if (row.sequence == sequence) row.copy(read = true) else row })
                    }
                    emit(NotificationCenterEffect.Open(item.groupId, item.channel, item.gameId))
                }
                is SaqzResult.Failure -> update { it.copy(busy = false, actionFailed = true) }
            }
        }
    }
}
