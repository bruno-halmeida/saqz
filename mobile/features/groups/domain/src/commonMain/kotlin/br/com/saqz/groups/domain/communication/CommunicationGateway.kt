package br.com.saqz.groups.domain.communication

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class CommunicationChannel { CHAT, NOTICE, REMINDER }
data class CommunicationMessage(
    val id: String, val sequence: Long, val groupId: GroupId, val authorId: String, val authorName: String,
    val channel: CommunicationChannel, val body: String, val createdAt: String, val gameId: String? = null,
    val recipientCount: Int = 0,
)
data class CommunicationPage<T>(val items: List<T>, val nextCursor: Long?)
data class InAppNotification(val sequence: Long, val message: CommunicationMessage, val read: Boolean)
data class NotificationPreferences(val notices: Boolean = true, val messages: Boolean = true, val reminders: Boolean = true)
data class CommunicationError(val cause: DataError) : SaqzError

interface CommunicationGateway {
    suspend fun messages(
        groupId: GroupId, channel: CommunicationChannel, before: Long? = null,
    ): SaqzResult<CommunicationPage<CommunicationMessage>, CommunicationError>
    suspend fun publish(
        groupId: GroupId, channel: CommunicationChannel, requestId: String, body: String,
    ): SaqzResult<CommunicationMessage, CommunicationError>
    suspend fun remind(groupId: GroupId, gameId: String, requestId: String): SaqzResult<CommunicationMessage, CommunicationError>
    suspend fun inbox(before: Long? = null): SaqzResult<CommunicationPage<InAppNotification>, CommunicationError>
    suspend fun markRead(sequence: Long): SaqzResult<Unit, CommunicationError>
    suspend fun preferences(): SaqzResult<NotificationPreferences, CommunicationError>
    suspend fun savePreferences(preferences: NotificationPreferences): SaqzResult<NotificationPreferences, CommunicationError>
}
