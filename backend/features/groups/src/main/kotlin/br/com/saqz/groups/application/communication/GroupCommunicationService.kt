package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupRole
import java.util.UUID

class GroupCommunicationService(
    private val transaction: TransactionRunner,
    private val groups: GroupReadRepository,
    private val repository: GroupCommunicationRepository,
) {
    fun messages(actor: UUID, groupId: UUID, channel: MessageChannel, before: Long?): CommunicationResult<CommunicationPage<GroupMessage>> =
        inGroup(actor, groupId) {
            if (channel in setOf(MessageChannel.REMINDER, MessageChannel.CHARGE) || (before != null && before <= 0)) return@inGroup invalid()
            CommunicationResult.Success(page(repository.messages(groupId, channel, before)) { it.sequence })
        }

    fun publish(actor: UUID, groupId: UUID, channel: MessageChannel, requestId: UUID, body: String): CommunicationResult<GroupMessage> =
        inGroup(actor, groupId) { role ->
            if (channel in setOf(MessageChannel.REMINDER, MessageChannel.CHARGE)) return@inGroup invalid()
            if (channel == MessageChannel.NOTICE && role == GroupRole.ATHLETE) return@inGroup forbidden()
            val text = body.trim()
            if (text.length !in 1..2000 || text.any { it.isISOControl() && it !in "\n\t\r" }) return@inGroup invalid()
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
            val title = repository.reminderTitle(groupId, gameId) ?: return@inGroup invalid()
            CommunicationResult.Success(repository.publish(
                groupId, actor, MessageChannel.REMINDER, requestId, "Confirme sua presença: $title", gameId,
            ))
        }

    fun inbox(actor: UUID, before: Long?): CommunicationResult<CommunicationPage<GroupNotification>> =
        if (before != null && before <= 0) invalid()
        else CommunicationResult.Success(page(repository.inbox(actor, before)) { it.sequence })

    fun markRead(actor: UUID, sequence: Long) = repository.markRead(actor, sequence)
    fun preferences(actor: UUID) = repository.preferences(actor)
    fun savePreferences(actor: UUID, preferences: NotificationPreferences) = repository.savePreferences(actor, preferences)

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
