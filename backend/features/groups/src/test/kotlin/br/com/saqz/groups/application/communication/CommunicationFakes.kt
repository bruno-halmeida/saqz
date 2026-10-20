package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import java.time.Instant
import java.util.UUID

class CountingTransaction : TransactionRunner {
    var calls = 0
    override fun <T> inTransaction(block: () -> T): T {
        calls += 1
        return block()
    }
}

/** Grupo em que [role] é o papel de todo mundo; `role = null` significa "não é membro". */
class FixedRoleGroups(private val groupId: UUID, var role: GroupRole?) : GroupReadRepository {
    override fun find(key: GroupReadKey): GroupReadSnapshot? =
        if (key.groupId == groupId) {
            GroupReadSnapshot(groupId, AccessName.from("Grupo"), IanaTimeZone.from("UTC"), role, 1)
        } else null
}

data class PublishedMessage(
    val groupId: UUID, val actor: UUID, val channel: MessageChannel, val requestId: UUID, val body: String, val gameId: UUID?,
)

class MemoryCommunicationRepository : GroupCommunicationRepository {
    var groupExists = true
    val published = mutableListOf<PublishedMessage>()
    val cancelled = mutableListOf<List<Any>>()
    val stored = mutableMapOf<Triple<UUID, MessageChannel, UUID>, GroupMessage>()
    var pageOfMessages: List<GroupMessage> = emptyList()
    var pageOfInbox: List<GroupNotification> = emptyList()
    var gameForReminder: ReminderGame? = null
    var roster = ReminderRoster(emptyList(), emptyList(), emptyList())
    var reminders = emptyList<ReminderCandidate>()
    var openGames = emptyList<ReminderCandidate>()
    var single: ReminderCandidate? = null
    var windows = emptyList<AttendanceWindowCandidate>()
    val read = mutableListOf<Pair<UUID, Long>>()
    var preferences = NotificationPreferences()
    val savedPreferences = mutableListOf<Pair<UUID, NotificationPreferences>>()
    var lastBefore: Long? = Long.MIN_VALUE

    override fun lockGroup(groupId: UUID) = groupExists
    override fun messages(viewer: UUID, groupId: UUID, channel: MessageChannel, before: Long?): List<GroupMessage> {
        lastBefore = before
        return pageOfMessages
    }
    override fun findRequest(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID) =
        stored[Triple(actor, channel, requestId)]
    override fun publish(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID, body: String, gameId: UUID?): GroupMessage {
        published += PublishedMessage(groupId, actor, channel, requestId, body, gameId)
        return message(groupId, actor, channel, body, gameId, sequence = published.size.toLong())
    }
    override fun publishGameCancelled(groupId: UUID, actor: UUID, gameId: UUID, body: String): GroupMessage {
        cancelled += listOf(groupId, actor, gameId, body)
        return message(groupId, actor, MessageChannel.NOTICE, body, gameId, sequence = 1)
    }
    override fun reminderGame(groupId: UUID, gameId: UUID) = gameForReminder
    override fun reminderRoster(groupId: UUID, gameId: UUID) = roster
    override fun reminderCandidates() = reminders
    override fun openGameCandidates() = openGames
    override fun reminderCandidate(groupId: UUID) = single
    override fun attendanceWindowCandidates() = windows
    override fun inbox(actor: UUID, before: Long?): List<GroupNotification> {
        lastBefore = before
        return pageOfInbox
    }
    override fun markRead(actor: UUID, sequence: Long) { read += actor to sequence }
    override fun preferences(actor: UUID) = preferences
    override fun savePreferences(actor: UUID, preferences: NotificationPreferences): NotificationPreferences {
        savedPreferences += actor to preferences
        return preferences
    }
}

fun message(
    groupId: UUID, author: UUID, channel: MessageChannel, body: String, gameId: UUID? = null, sequence: Long = 1, id: UUID = UUID.randomUUID(),
) = GroupMessage(id, sequence, groupId, author, "Autor", channel, body, gameId, 0, Instant.parse("2026-09-20T12:00:00Z"))
