package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.sharedkernel.moderation.ObjectionableText
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

private val SAO_PAULO = ZoneId.of("America/Sao_Paulo")

/** Freio do modo ON_CHANGE: no mínimo 15 min entre mensagens e no máximo 12 por dia (São Paulo) por grupo. */
const val ON_CHANGE_DEBOUNCE_MINUTES = 15L
const val ON_CHANGE_DAILY_LIMIT = 12

/** Publica quando a lista mudou (sem assinatura gravada também é mudança) e o freio deixa. */
fun shouldPublishChange(stored: String?, digest: String, activity: OnChangeActivity, now: Instant): Boolean {
    if (stored == digest) return false
    val last = activity.lastMessageAt
    if (last != null && now.isBefore(last.plusSeconds(ON_CHANGE_DEBOUNCE_MINUTES * 60))) return false
    return activity.today < ON_CHANGE_DAILY_LIMIT
}

class GroupCommunicationService(
    private val transaction: TransactionRunner,
    private val groups: GroupReadRepository,
    private val repository: GroupCommunicationRepository,
    /** Mesmo critério das escritas: grupo congelado (dono sem plano ativo) não recebe comunicação automática (DEB-074). */
    private val writeAccess: GroupWriteAccess = GroupWriteAccess.Unrestricted,
) {
    fun messages(actor: UUID, groupId: UUID, channel: MessageChannel, before: Long?): CommunicationResult<CommunicationPage<GroupMessage>> =
        inGroup(actor, groupId) {
            if (channel in setOf(MessageChannel.REMINDER, MessageChannel.GAME_OPEN, MessageChannel.CHARGE, MessageChannel.ATTENDANCE_WINDOW, MessageChannel.WAITLIST_DEADLINE, MessageChannel.FIRST_MEMBER, MessageChannel.FIRST_CONFIRMATION, MessageChannel.TRIAL_ENDING, MessageChannel.AUTO_CONFIRMATION) || (before != null && before <= 0)) return@inGroup invalid()
            CommunicationResult.Success(page(repository.messages(actor, groupId, channel, before)) { it.sequence })
        }

    fun publish(actor: UUID, groupId: UUID, channel: MessageChannel, requestId: UUID, body: String): CommunicationResult<GroupMessage> =
        inGroup(actor, groupId) { role ->
            if (channel in setOf(MessageChannel.REMINDER, MessageChannel.GAME_OPEN, MessageChannel.CHARGE, MessageChannel.ATTENDANCE_WINDOW, MessageChannel.WAITLIST_DEADLINE, MessageChannel.FIRST_MEMBER, MessageChannel.FIRST_CONFIRMATION, MessageChannel.TRIAL_ENDING, MessageChannel.AUTO_CONFIRMATION)) return@inGroup CommunicationResult.Failure(CommunicationError.CHANNEL_NOT_PUBLISHABLE)
            if (channel == MessageChannel.NOTICE && role == GroupRole.ATHLETE) return@inGroup forbidden()
            val text = body.trim()
            if (text.isEmpty()) return@inGroup CommunicationResult.Failure(CommunicationError.BODY_BLANK)
            if (text.length > 2000) return@inGroup CommunicationResult.Failure(CommunicationError.BODY_TOO_LONG)
            if (text.any { it.isISOControl() && it !in "\n\t\r" }) return@inGroup CommunicationResult.Failure(CommunicationError.BODY_CONTROL_CHARACTERS)
            if (ObjectionableText.contains(text)) return@inGroup CommunicationResult.Failure(CommunicationError.OBJECTIONABLE)
            val existing = repository.findRequest(groupId, actor, channel, requestId)
            when {
                existing == null -> CommunicationResult.Success(repository.publish(groupId, actor, channel, requestId, text, null))
                existing.body == text -> CommunicationResult.Success(existing)
                else -> CommunicationResult.Failure(CommunicationError.CONFLICT)
            }
        }

    fun remind(actor: UUID, groupId: UUID, gameId: UUID, requestId: UUID): CommunicationResult<GroupMessage> =
        inGroup(actor, groupId) { role ->
            if (role == GroupRole.ATHLETE) return@inGroup forbidden()
            val existing = repository.findRequest(groupId, actor, MessageChannel.REMINDER, requestId)
            if (existing != null) return@inGroup if (existing.gameId == gameId) {
                CommunicationResult.Success(existing)
            } else CommunicationResult.Failure(CommunicationError.CONFLICT)
            val game = repository.reminderGame(groupId, gameId) ?: return@inGroup invalid()
            CommunicationResult.Success(repository.publish(
                groupId, actor, MessageChannel.REMINDER, requestId,
                reminderBody(game, repository.reminderRoster(groupId, gameId)), gameId,
            ))
        }

    /**
     * Lembrete automático (3 vezes por dia, às 9h, às 12h30 e às 18h) com a lista do próximo jogo de cada grupo, do
     * jogo publicado até o início — inclusive depois do prazo, para a lista final chegar ao grupo.
     * Agenda recorrente deixa vários jogos abertos ao mesmo tempo e o grupo não quer um lembrete por
     * jogo. O autor é o dono do grupo, então ele fica fora dos destinatários da notificação;
     * cada execução cria mensagem nova. Grupo congelado não recebe (DEB-074): o atleta não consegue confirmar, e o
     * lembrete, que também vai ao grupo do WhatsApp com o botão "Vou", manda confirmar. Roda também nos grupos em
     * ON_CHANGE, pelo push e pela central; a origem SCHEDULED faz o gatilho da fila deixá-lo fora do grupo do WhatsApp.
     */
    fun remindAutomatically(): Int = logPublished("attendance_reminder", "reminder_published", transaction.inTransaction {
        repository.reminderCandidates().filter { writeAccess.canWrite(it.groupId) }.map { candidate ->
            repository.publish(
                candidate.groupId, candidate.ownerId, MessageChannel.REMINDER, UUID.randomUUID(),
                reminderBody(candidate.game, repository.reminderRoster(candidate.groupId, candidate.gameId), Instant.now()),
                candidate.gameId, AttendanceUpdatesMode.SCHEDULED,
            )
        }
    })

    /**
     * Lista no grupo do WhatsApp a cada mudança (modo ON_CHANGE, a cada 5 min): mesmos candidatos e mesmo corpo do
     * [remindAutomatically], só dos grupos nesse modo e sem central nem push, e só quando a lista mudou desde a última publicada (a primeira
     * passada de um jogo publica). Freio anti-spam: 15 min entre mensagens e 12 por dia (São Paulo) por grupo, contando todos
     * os jogos; a assinatura é por jogo. A mudança
     * segurada pelo freio sai na próxima passada livre, porque a assinatura só é gravada quando publica.
     */
    fun remindOnChanges(now: Instant = Instant.now()): Int = logPublished("attendance_updates", "reminder_published", transaction.inTransaction {
        repository.reminderCandidates(AttendanceUpdatesMode.ON_CHANGE).filter { writeAccess.canWrite(it.groupId) }.mapNotNull { candidate ->
            val roster = repository.reminderRoster(candidate.groupId, candidate.gameId)
            val digest = rosterDigest(roster)
            val stored = repository.attendanceDigest(candidate.gameId)
            val dayStart = now.atZone(SAO_PAULO).toLocalDate().atStartOfDay(SAO_PAULO).toInstant()
            if (!shouldPublishChange(stored, digest, repository.onChangeActivity(candidate.groupId, dayStart), now)) {
                return@mapNotNull null
            }
            repository.saveAttendanceDigest(candidate.gameId, digest)
            repository.publish(
                candidate.groupId, candidate.ownerId, MessageChannel.REMINDER, UUID.randomUUID(),
                reminderBody(candidate.game, roster, now), candidate.gameId, AttendanceUpdatesMode.ON_CHANGE,
            )
        }
    })

    /**
     * Aviso diário de jogo liberado. Usa os mesmos candidatos do lembrete: o próximo jogo
     * publicado de cada grupo, com prazo e início no futuro. O dono assina o aviso, mas como ele
     * é do sistema também o recebe. O primeiro aviso de cada jogo vai para o grupo inteiro —
     * inclusive o mensalista que já nasce confirmado; os seguintes tocam só quem não respondeu.
     * GAME_OPEN nunca vira WhatsApp: é push e central. Grupo congelado não recebe (DEB-074).
     */
    fun announceOpenGames(): Int = logPublished("game_open", "game_open_published", transaction.inTransaction {
        repository.openGameCandidates().filter { writeAccess.canWrite(it.groupId) }.map { candidate ->
            repository.publish(
                candidate.groupId, candidate.ownerId, MessageChannel.GAME_OPEN, UUID.randomUUID(),
                openGameBody(candidate.game), candidate.gameId,
            )
        }
    })

    /**
     * Aviso de jogo liberado na hora da publicação, sem esperar o cron das 14h — só quando o jogo
     * publicado é o próximo aberto do grupo (a regra do [announceOpenGames]). Roda dentro da
     * transação da publicação, depois do auto-confirm; como é o primeiro aviso do jogo, alcança o
     * grupo inteiro. Grupo congelado não recebe (DEB-074); publicar nele já é barrado, então aqui é só defesa.
     */
    fun announceOpenGame(groupId: UUID, gameId: UUID): Boolean = transaction.inTransaction {
        val candidate = repository.reminderCandidate(groupId)?.takeIf { it.gameId == gameId && writeAccess.canWrite(groupId) }
            ?: return@inTransaction false
        repository.publish(
            candidate.groupId, candidate.ownerId, MessageChannel.GAME_OPEN, UUID.randomUUID(),
            openGameBody(candidate.game), candidate.gameId,
        )
        true
    }

    /**
     * Janela de presença (DEB-113): uma mensagem por jogo que entrou na janela (8 h antes do prazo de confirmação), para
     * quem ainda não respondeu (dono incluído). A view revalida a entrega até o fim da janela, que é o prazo.
     */
    fun openAttendanceWindows(): Int = logPublished("attendance_window", "window_opened", transaction.inTransaction {
        repository.attendanceWindowCandidates().map { candidate ->
            repository.publish(
                candidate.groupId, candidate.ownerId, MessageChannel.ATTENDANCE_WINDOW, UUID.randomUUID(),
                attendanceWindowBody(candidate.game, candidate.confirmed, candidate.capacity, candidate.waitlisted),
                candidate.gameId,
            )
        }
    })

    /** Cancelamento de jogo publicado: avisa confirmados e lista de espera (e o grupo do WhatsApp, se vinculado). */
    fun announceGameCancelled(groupId: UUID, actor: UUID, gameId: UUID, localDate: LocalDate, localTime: LocalTime): GroupMessage =
        transaction.inTransaction {
            repository.publishGameCancelled(groupId, actor, gameId, gameCancelledBody(localDate, localTime))
        }

    fun inbox(actor: UUID, before: Long?): CommunicationResult<CommunicationPage<GroupNotification>> =
        if (before != null && before <= 0) invalid()
        else CommunicationResult.Success(page(repository.inbox(actor, before)) { it.sequence })

    fun markRead(actor: UUID, sequence: Long) = repository.markRead(actor, sequence)
    fun preferences(actor: UUID) = repository.preferences(actor)
    fun savePreferences(actor: UUID, preferences: NotificationPreferences) = repository.savePreferences(actor, preferences)

    // Um `job_item` por mensagem, depois do commit: só ids, nunca o texto nem o nome do autor.
    private fun logPublished(job: String, event: String, messages: List<GroupMessage>): Int {
        messages.forEach { JobLog.item(job, event, "groupId" to it.groupId, "gameId" to it.gameId, "messageId" to it.id) }
        return messages.size
    }

    private fun <T> inGroup(actor: UUID, groupId: UUID, block: (GroupRole) -> CommunicationResult<T>): CommunicationResult<T> =
        transaction.inTransaction {
            if (!repository.lockGroup(groupId)) return@inTransaction CommunicationResult.Failure(CommunicationError.NOT_FOUND)
            val role = groups.find(GroupReadKey(actor, groupId))?.role
                ?: return@inTransaction CommunicationResult.Failure(CommunicationError.NOT_FOUND)
            block(role)
        }

    private fun <T> page(items: List<T>, sequence: (T) -> Long) = CommunicationPage(
        items.take(50), if (items.size > 50) sequence(items[49]) else null,
    )
    private fun invalid() = CommunicationResult.Failure(CommunicationError.INVALID)
    private fun forbidden() = CommunicationResult.Failure(CommunicationError.FORBIDDEN)
}
