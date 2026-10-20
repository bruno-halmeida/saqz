package br.com.saqz.groups.application.communication

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class MessageChannel {
    CHAT,
    NOTICE,
    REMINDER,
    GAME_OPEN,
    CHARGE,
    ATTENDANCE_WINDOW,
    WAITLIST_DEADLINE,
    FIRST_MEMBER,
    FIRST_CONFIRMATION,
    ENTRY_REQUEST,
    TRIAL_ENDING,
    AUTO_CONFIRMATION,
}

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

/** Ordem das seções no corpo: confirmados, lista de espera e fora. A espera vem na ordem de promoção. */
data class ReminderRoster(val confirmed: List<String>, val waitlisted: List<String>, val declined: List<String>)

/**
 * [confirmationDeadline] e [capacity] vêm preenchidos no lembrete automático e no "Avisar" manual; sem eles o
 * corpo do lembrete omite as linhas de prazo e vagas (aviso de jogo liberado).
 */
data class ReminderGame(
    val localDate: LocalDate,
    val localTime: LocalTime,
    val venue: String,
    val confirmationDeadline: Instant? = null,
    val capacity: Int? = null,
)

data class ReminderCandidate(
    val gameId: UUID,
    val groupId: UUID,
    val ownerId: UUID,
    val game: ReminderGame,
)

/** Jogo que entrou na janela das 24 h, com a contagem do momento em que ela abre. */
data class AttendanceWindowCandidate(
    val gameId: UUID,
    val groupId: UUID,
    val ownerId: UUID,
    val game: ReminderGame,
    val confirmed: Int,
    val capacity: Int,
    val waitlisted: Int,
)
data class PushPreferences(val notices: Boolean = true, val messages: Boolean = true, val reminders: Boolean = true, val charges: Boolean = true)
/** Cobrança no WhatsApp particular nasce ligada (decisão de 13/10/2026); os outros canais de DM seguem inertes. */
data class WhatsAppPreferences(val notices: Boolean = false, val reminders: Boolean = false, val charges: Boolean = true)
data class NotificationPreferences(
    val notices: Boolean = true, val messages: Boolean = true, val reminders: Boolean = true,
    val push: PushPreferences = PushPreferences(notices, messages, reminders, reminders),
    val whatsapp: WhatsAppPreferences = WhatsAppPreferences(),
)
/** OBJECTIONABLE: o texto passou na validação de formato mas caiu no filtro de conteúdo ofensivo. */
enum class CommunicationError {
    NOT_FOUND, FORBIDDEN, INVALID, CONFLICT, OBJECTIONABLE,
    /** Causas de corpo/canal recusado que o cliente precisa distinguir (campo `body` ou `channel`). */
    BODY_BLANK, BODY_TOO_LONG, BODY_CONTROL_CHARACTERS, CHANNEL_NOT_PUBLISHABLE,
}
sealed interface CommunicationResult<out T> {
    data class Success<T>(val value: T) : CommunicationResult<T>
    data class Failure(val reason: CommunicationError) : CommunicationResult<Nothing>
}

interface GroupCommunicationRepository {
    fun lockGroup(groupId: UUID): Boolean
    fun messages(viewer: UUID, groupId: UUID, channel: MessageChannel, before: Long?): List<GroupMessage>
    fun findRequest(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID): GroupMessage?
    fun publish(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID, body: String, gameId: UUID?): GroupMessage
    /**
     * Aviso de jogo cancelado (canal NOTICE, com o jogo vinculado): vai para a central e o push de quem estava
     * confirmado ou na espera, menos quem cancelou, e o trigger da fila leva o texto ao grupo do WhatsApp vinculado.
     */
    fun publishGameCancelled(groupId: UUID, actor: UUID, gameId: UUID, body: String): GroupMessage
    fun reminderGame(groupId: UUID, gameId: UUID): ReminderGame?
    fun reminderRoster(groupId: UUID, gameId: UUID): ReminderRoster
    /** Próximo jogo publicado de cada grupo com início no futuro, mesmo com o prazo vencido: o lembrete automático. */
    fun reminderCandidates(): List<ReminderCandidate>

    /** Próximo jogo publicado de cada grupo com prazo E início no futuro: o aviso de jogo liberado (GAME_OPEN). */
    fun openGameCandidates(): List<ReminderCandidate>

    /** O próximo jogo aberto de um grupo: a mesma regra de [reminderCandidates], para um grupo só. */
    fun reminderCandidate(groupId: UUID): ReminderCandidate?
    fun attendanceWindowCandidates(): List<AttendanceWindowCandidate>
    fun inbox(actor: UUID, before: Long?): List<GroupNotification>
    fun markRead(actor: UUID, sequence: Long)
    fun preferences(actor: UUID): NotificationPreferences
    fun savePreferences(actor: UUID, preferences: NotificationPreferences): NotificationPreferences
}
