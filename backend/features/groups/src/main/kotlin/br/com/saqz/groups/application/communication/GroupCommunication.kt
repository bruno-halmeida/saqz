package br.com.saqz.groups.application.communication

import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.GameSystem
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
    /** Recebimento pelo app (FIN-013/FIN-014): pagamento, revisão e "Pagamento confirmado". */
    PAYMENT,
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
    /** Foto de perfil do autor para quem lê (MEM-006); preenchida na borda HTTP. */
    val photoTag: String? = null,
    /** Marca do evento (`event_kind`); `GAME_FEE_UNDEFINED` leva o app novo ao "Valor por jogo" (DEB-146). */
    val eventKind: String? = null,
)

data class CommunicationPage<T>(val items: List<T>, val nextCursor: Long?)
data class GroupNotification(val sequence: Long, val message: GroupMessage, val read: Boolean)

/**
 * Ordem das seções no corpo: confirmados, lista de espera e fora. A espera vem na ordem de promoção. [layout] nulo é
 * o jogo legado (sem sistema de jogo): o corpo sai como sempre, sem numeração nem vagas vazias.
 */
data class ReminderRoster(
    val confirmed: List<String>,
    val waitlisted: List<String>,
    val declined: List<String>,
    val layout: RosterLayout? = null,
)

data class RosterEntry(val name: String, val position: AthletePosition? = null)

/**
 * Lista de jogo com sistema (6-0 ou 5-1): as mesmas entradas de [ReminderRoster], com a posição de cada uma. [slots]
 * são as vagas por posição do jogo (`game_position_slots`), vazias no 6-0. [numberedWaitlist] só quando a ordem impressa
 * é a de promoção: promoção automática (FIFO) sem prioridade de mensalista.
 */
data class RosterLayout(
    val system: GameSystem,
    val slots: Map<AthletePosition, Int>,
    val confirmed: List<RosterEntry>,
    val waitlisted: List<RosterEntry>,
    val declined: List<RosterEntry>,
    val numberedWaitlist: Boolean,
)

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
    /** Prioridade de mensalista ligada no grupo: só então o lembrete mostra até quando ela vale. */
    val mensalistaPriority: Boolean = false,
)

/**
 * Como a lista de presença chega ao grupo do WhatsApp: nos horários fixos (9h, 12h30, 18h) ou a cada mudança dela. Só o
 * grupo do WhatsApp: push, central e DM seguem as regras de sempre. Também é a origem gravada no lembrete automático.
 */
enum class AttendanceUpdatesMode { SCHEDULED, ON_CHANGE }

/** Mensagens ON_CHANGE do grupo para o freio: a última e quantas desde o início do dia (São Paulo). */
data class OnChangeActivity(val lastMessageAt: Instant?, val today: Int)

data class ReminderCandidate(
    val gameId: UUID,
    val groupId: UUID,
    val ownerId: UUID,
    val game: ReminderGame,
)

/** Jogo que entrou na janela de presença, com a contagem do momento em que ela abre. */
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
    /** [reminderOrigin] só no lembrete automático; nulo é o "Avisar" manual e tudo que não é lembrete. */
    fun publish(
        groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID, body: String, gameId: UUID?,
        reminderOrigin: AttendanceUpdatesMode? = null,
    ): GroupMessage
    /**
     * Aviso de jogo cancelado (canal NOTICE, com o jogo vinculado): vai para a central e o push de quem estava
     * confirmado ou na espera, menos quem cancelou, e o trigger da fila leva o texto ao grupo do WhatsApp vinculado.
     */
    fun publishGameCancelled(groupId: UUID, actor: UUID, gameId: UUID, body: String): GroupMessage
    fun reminderGame(groupId: UUID, gameId: UUID): ReminderGame?
    fun reminderRoster(groupId: UUID, gameId: UUID): ReminderRoster
    /**
     * Próximo jogo publicado de cada grupo com início no futuro, mesmo com o prazo vencido: o lembrete automático. Com
     * [mode], só dos grupos nesse modo.
     */
    fun reminderCandidates(mode: AttendanceUpdatesMode? = null): List<ReminderCandidate>

    /** Trava e lê a assinatura da última lista publicada do jogo no modo ON_CHANGE. */
    fun attendanceDigest(gameId: UUID): String?
    fun saveAttendanceDigest(gameId: UUID, digest: String)

    /** O freio é do grupo, não do jogo: mensagens ON_CHANGE do grupo, a última e quantas desde [dayStart]. */
    fun onChangeActivity(groupId: UUID, dayStart: Instant): OnChangeActivity

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
