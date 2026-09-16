package br.com.saqz.groups.application.communication

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

/** Ordem das seções no corpo: confirmados, lista de espera e fora. */
data class ReminderRoster(val confirmed: List<String>, val waitlisted: List<String>, val declined: List<String>)

data class ReminderGame(val localDate: LocalDate, val localTime: LocalTime, val venue: String)

data class ReminderCandidate(
    val gameId: UUID,
    val groupId: UUID,
    val ownerId: UUID,
    val game: ReminderGame,
)
data class PushPreferences(val notices: Boolean = true, val messages: Boolean = true, val reminders: Boolean = true, val charges: Boolean = true)
data class WhatsAppPreferences(val notices: Boolean = false, val reminders: Boolean = false, val charges: Boolean = false)
data class NotificationPreferences(
    val notices: Boolean = true, val messages: Boolean = true, val reminders: Boolean = true,
    val push: PushPreferences = PushPreferences(notices, messages, reminders, reminders),
    val whatsapp: WhatsAppPreferences = WhatsAppPreferences(),
)
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
    fun reminderGame(groupId: UUID, gameId: UUID): ReminderGame?
    fun reminderRoster(groupId: UUID, gameId: UUID): ReminderRoster
    fun reminderCandidates(): List<ReminderCandidate>
    fun inbox(actor: UUID, before: Long?): List<GroupNotification>
    fun markRead(actor: UUID, sequence: Long)
    fun preferences(actor: UUID): NotificationPreferences
    fun savePreferences(actor: UUID, preferences: NotificationPreferences): NotificationPreferences
}
