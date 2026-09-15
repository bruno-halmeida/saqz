package br.com.saqz.groups.application.communication

import java.time.Instant
import java.util.UUID

enum class MessageChannel { CHAT, NOTICE, REMINDER, CHARGE }

data class GroupMessage(
    val id: UUID,
    val sequence: Long,
    val groupId: UUID,
    val authorId: UUID,
    val authorName: String,
    val channel: MessageChannel,
    val body: String,
    val gameId: UUID?,
    val recipientCount: Int,
    val createdAt: Instant,
)

data class CommunicationPage<T>(val items: List<T>, val nextCursor: Long?)
data class GroupNotification(val sequence: Long, val message: GroupMessage, val read: Boolean)
data class NotificationPreferences(val notices: Boolean = true, val messages: Boolean = true, val reminders: Boolean = true)
enum class CommunicationError { NOT_FOUND, FORBIDDEN, INVALID, CONFLICT }
sealed interface CommunicationResult<out T> {
    data class Success<T>(val value: T) : CommunicationResult<T>
    data class Failure(val reason: CommunicationError) : CommunicationResult<Nothing>
}

interface GroupCommunicationRepository {
    fun lockGroup(groupId: UUID): Boolean
    fun messages(groupId: UUID, channel: MessageChannel, before: Long?): List<GroupMessage>
    fun findRequest(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID): GroupMessage?
    fun publish(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID, body: String, gameId: UUID?): GroupMessage
    fun reminderTitle(groupId: UUID, gameId: UUID): String?
    fun inbox(actor: UUID, before: Long?): List<GroupNotification>
    fun markRead(actor: UUID, sequence: Long)
    fun preferences(actor: UUID): NotificationPreferences
    fun savePreferences(actor: UUID, preferences: NotificationPreferences): NotificationPreferences
}
