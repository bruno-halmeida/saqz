package br.com.saqz.groups.presentation.communication

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.domain.communication.NotificationPreferences
import br.com.saqz.groups.presentation.GroupUiError

enum class NotificationSettingsChannel { APP, PUSH, WHATSAPP }

@Immutable
data class NotificationCenterState(
    val loading: Boolean = true,
    val error: GroupUiError? = null,
    val items: List<NotificationUi> = emptyList(),
    val nextCursor: Long? = null,
    val busy: Boolean = false,
    val actionFailed: Boolean = false,
    val preferences: NotificationPreferences = NotificationPreferences(),
    val saved: Boolean = false,
    val settingsChannel: NotificationSettingsChannel = NotificationSettingsChannel.APP,
)
@Immutable
data class NotificationUi(
    val sequence: Long, val groupId: String, val channel: CommunicationChannel, val gameId: String?,
    val content: ThreadMessageUi, val read: Boolean,
)
sealed interface NotificationCenterIntent {
    data object Refresh : NotificationCenterIntent
    data object More : NotificationCenterIntent
    data class SelectChannel(val channel: NotificationSettingsChannel) : NotificationCenterIntent
    data object Save : NotificationCenterIntent
    data class Open(val sequence: Long) : NotificationCenterIntent
    data class Preferences(val value: NotificationPreferences) : NotificationCenterIntent
}
sealed interface NotificationCenterEffect {
    data class Open(val groupId: String, val channel: CommunicationChannel, val gameId: String?) : NotificationCenterEffect
}
