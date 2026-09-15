package br.com.saqz.groups.presentation.ui.finance.sheets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.ChargeReminderGateway
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ChargeReminderState(val sending: Boolean = false, val failed: Boolean = false, val sent: Int? = null)
sealed interface ChargeReminderIntent {
    data class Send(val chargeIds: List<String>) : ChargeReminderIntent
    data object Reset : ChargeReminderIntent
}
@OptIn(ExperimentalUuidApi::class)
class ChargeReminderViewModel(
    private val groupId: String,
    private val gateway: ChargeReminderGateway,
    private val saved: SavedStateHandle,
) :
    MviViewModel<ChargeReminderState, ChargeReminderIntent, Nothing>(ChargeReminderState()) {
    override fun onIntent(intent: ChargeReminderIntent) {
        if (state.value.sending) return
        when (intent) {
            ChargeReminderIntent.Reset -> {
                if (state.value.sent != null) { saved["request"] = null; saved["charges"] = null }
                update { ChargeReminderState() }
            }
            is ChargeReminderIntent.Send -> send(intent.chargeIds)
        }
    }
    private fun send(ids: List<String>) {
        if (state.value.sent != null || ids.isEmpty() || ids.any(String::isBlank)) return
        val selection = ids.distinct().sorted()
        val request = if (saved.get<List<String>>("charges") == selection) saved.get<String>("request") else null
        val requestId = request ?: Uuid.random().toString()
        saved["charges"] = selection
        saved["request"] = requestId
        update { ChargeReminderState(sending = true) }
        viewModelScope.launch {
            when (val result = gateway.send(GroupId(groupId), requestId, selection)) {
                is SaqzResult.Success -> update { ChargeReminderState(sent = result.value.notificationCount) }
                is SaqzResult.Failure -> update { ChargeReminderState(failed = true) }
            }
        }
    }
}
