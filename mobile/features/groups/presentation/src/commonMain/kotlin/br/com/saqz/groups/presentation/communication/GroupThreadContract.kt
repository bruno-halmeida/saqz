package br.com.saqz.groups.presentation.communication

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class GroupThreadState(
    val loading: Boolean = true,
    val error: GroupUiError? = null,
    val messages: List<ThreadMessageUi> = emptyList(),
    val nextCursor: Long? = null,
    val paging: Boolean = false,
    val canPost: Boolean = false,
    val draft: String = "",
    val sending: Boolean = false,
    val sendFailed: Boolean = false,
    val pageFailed: Boolean = false,
)
@Immutable
data class ThreadMessageUi(val id: String, val author: String, val body: String, val time: String)
sealed interface GroupThreadIntent {
    data object Refresh : GroupThreadIntent
    data object More : GroupThreadIntent
    data object Send : GroupThreadIntent
    data class Draft(val text: String) : GroupThreadIntent
}
sealed interface GroupThreadEffect
