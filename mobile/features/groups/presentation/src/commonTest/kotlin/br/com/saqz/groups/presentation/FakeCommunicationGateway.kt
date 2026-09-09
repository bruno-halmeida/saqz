package br.com.saqz.groups.presentation

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.CommunicationGateway
import br.com.saqz.groups.domain.communication.CommunicationMessage
import br.com.saqz.groups.domain.communication.CommunicationPage
import br.com.saqz.groups.domain.communication.InAppNotification
import br.com.saqz.groups.domain.communication.NotificationPreferences

data class PublicationCall(val group: GroupId, val channel: CommunicationChannel, val requestId: String, val body: String)
class FakeCommunicationGateway : CommunicationGateway {
    var messagesResult: SaqzResult<CommunicationPage<CommunicationMessage>, CommunicationError> = SaqzResult.Success(CommunicationPage(emptyList(), null))
    var publishResult: SaqzResult<CommunicationMessage, CommunicationError> = SaqzResult.Success(sampleCommunicationMessage())
    var publishBlock: (suspend () -> SaqzResult<CommunicationMessage, CommunicationError>)? = null
    var reminderResult = publishResult
    var remindBlock: (suspend () -> SaqzResult<CommunicationMessage, CommunicationError>)? = null
    var inboxResult: SaqzResult<CommunicationPage<InAppNotification>, CommunicationError> = SaqzResult.Success(CommunicationPage(emptyList(), null))
    var preferencesResult: SaqzResult<NotificationPreferences, CommunicationError> = SaqzResult.Success(NotificationPreferences())
    var saveResult: SaqzResult<NotificationPreferences, CommunicationError>? = null
    var readResult: SaqzResult<Unit, CommunicationError> = SaqzResult.Success(Unit)
    val publications = mutableListOf<PublicationCall>()
    val reminders = mutableListOf<Triple<GroupId, String, String>>()
    val reads = mutableListOf<Long>()
    val preferences = mutableListOf<NotificationPreferences>()
    val cursors = mutableListOf<Long?>()
    override suspend fun messages(groupId: GroupId, channel: CommunicationChannel, before: Long?): SaqzResult<CommunicationPage<CommunicationMessage>, CommunicationError> {
        cursors += before
        return messagesResult
    }
    override suspend fun publish(groupId: GroupId, channel: CommunicationChannel, requestId: String, body: String): SaqzResult<CommunicationMessage, CommunicationError> {
        publications += PublicationCall(groupId, channel, requestId, body)
        return publishBlock?.invoke() ?: publishResult
    }
    override suspend fun remind(groupId: GroupId, gameId: String, requestId: String): SaqzResult<CommunicationMessage, CommunicationError> {
        reminders += Triple(groupId, gameId, requestId)
        return remindBlock?.invoke() ?: reminderResult
    }
    override suspend fun inbox(before: Long?): SaqzResult<CommunicationPage<InAppNotification>, CommunicationError> { cursors += before; return inboxResult }
    override suspend fun markRead(sequence: Long): SaqzResult<Unit, CommunicationError> { reads += sequence; return readResult }
    override suspend fun preferences() = preferencesResult
    override suspend fun savePreferences(preferences: NotificationPreferences): SaqzResult<NotificationPreferences, CommunicationError> {
        this.preferences += preferences
        return saveResult ?: SaqzResult.Success(preferences)
    }
}
fun sampleCommunicationMessage() = CommunicationMessage("message-1", 1, GroupId("group-1"), "me", "Ana", CommunicationChannel.CHAT, "Vamos jogar?", "2026-09-09T12:00:00Z")
