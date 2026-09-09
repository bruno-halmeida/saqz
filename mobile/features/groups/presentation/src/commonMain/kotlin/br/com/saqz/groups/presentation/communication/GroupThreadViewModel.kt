package br.com.saqz.groups.presentation.communication

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.CommunicationGateway
import br.com.saqz.groups.domain.communication.CommunicationMessage
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class GroupThreadViewModel(
    private val groupId: String,
    notices: Boolean,
    private val saved: SavedStateHandle,
    private val gateway: CommunicationGateway,
    private val groups: GroupGateway,
) : MviViewModel<GroupThreadState, GroupThreadIntent, GroupThreadEffect>(GroupThreadState(draft = saved["draft"] ?: "")) {
    private val channel = if (notices) CommunicationChannel.NOTICE else CommunicationChannel.CHAT
    private var generation = 0
    init { load() }

    override fun onIntent(intent: GroupThreadIntent) {
        when (intent) {
            GroupThreadIntent.Refresh -> if (!state.value.sending) load()
            GroupThreadIntent.More -> if (!state.value.sending && !state.value.paging && state.value.nextCursor != null) load(more = true)
            GroupThreadIntent.Send -> send()
            is GroupThreadIntent.Draft -> if (!state.value.sending) {
                val text = intent.text.take(2000)
                if (text != state.value.draft) saved["requestId"] = null
                saved["draft"] = text
                update { it.copy(draft = text, sendFailed = false) }
            }
        }
    }

    private fun load(more: Boolean = false) {
        val request = ++generation
        val before = if (more) state.value.nextCursor else null
        update { it.copy(loading = !more, paging = more, error = null, pageFailed = false) }
        viewModelScope.launch {
            if (!more) {
                val group = groups.read(GroupId(groupId))
                if (request != generation) return@launch
                if (group is SaqzResult.Failure) {
                    update { it.copy(loading = false, canPost = false, error = group.error.toUiError()) }
                    return@launch
                }
                val role = (group as SaqzResult.Success).value.group.role
                update { it.copy(canPost = channel == CommunicationChannel.CHAT || role != GroupRole.ATHLETE) }
            }
            when (val result = gateway.messages(GroupId(groupId), channel, before)) {
                is SaqzResult.Success -> if (request == generation) update {
                    it.copy(
                        loading = false, paging = false,
                        messages = ((if (more) it.messages else emptyList()) + result.value.items.map(CommunicationMessage::toThreadUi))
                            .distinctBy { message -> message.id },
                        nextCursor = result.value.nextCursor,
                    )
                }
                is SaqzResult.Failure -> if (request == generation) update {
                    it.copy(loading = false, paging = false, pageFailed = more, error = if (more) null else result.error.toUiError())
                }
            }
        }
    }

    private fun send() {
        val current = state.value
        if (!current.canPost || current.loading || current.paging || current.sending) return
        if (current.draft.isBlank()) return
        val body = current.draft.trim()
        val requestId = saved.get<String>("requestId") ?: Uuid.random().toString().also { saved["requestId"] = it }
        update { it.copy(sending = true, sendFailed = false) }
        viewModelScope.launch {
            when (val result = gateway.publish(GroupId(groupId), channel, requestId, body)) {
                is SaqzResult.Failure -> update { it.copy(sending = false, sendFailed = true) }
                is SaqzResult.Success -> {
                    saved["draft"] = ""
                    saved["requestId"] = null
                    update { it.copy(sending = false, draft = "", messages =
                        (listOf(result.value.toThreadUi()) + it.messages).distinctBy { message -> message.id }) }
                }
            }
        }
    }
}

internal fun CommunicationMessage.toThreadUi() = ThreadMessageUi(id, authorName, body, communicationTime(createdAt))
internal fun communicationTime(value: String): String {
    val time = runCatching { Instant.parse(value).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull() ?: return value
    return "${time.day.toString().padStart(2, '0')}/${(time.month.ordinal + 1).toString().padStart(2, '0')} " +
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
internal fun CommunicationError.toUiError() = when (cause) {
    DataError.Forbidden -> GroupUiError.AccessDenied
    DataError.NotFound -> GroupUiError.NotFound
    else -> GroupUiError.Unknown
}
