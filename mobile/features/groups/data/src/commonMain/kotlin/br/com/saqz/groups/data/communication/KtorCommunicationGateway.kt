package br.com.saqz.groups.data.communication

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.CommunicationGateway
import br.com.saqz.groups.domain.communication.CommunicationMessage
import br.com.saqz.groups.domain.communication.CommunicationPage
import br.com.saqz.groups.domain.communication.InAppNotification
import br.com.saqz.groups.domain.communication.NotificationPreferences
import br.com.saqz.groups.domain.communication.PushPreferences
import br.com.saqz.groups.domain.communication.WhatsAppPreferences
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class MessageDto(
    val id: String, val sequence: Long, val groupId: String, val authorId: String, val authorName: String,
    val channel: String, val body: String, val createdAt: String, val gameId: String? = null, val recipientCount: Int = 0,
) {
    fun domain(): CommunicationMessage? {
        val channel = CommunicationChannel.entries.find { it.name == channel } ?: return null
        if (listOf(id, groupId, authorId, authorName, body).any(String::isBlank) || sequence <= 0) return null
        return CommunicationMessage(id, sequence, GroupId(groupId), authorId, authorName, channel, body, createdAt, gameId, recipientCount)
    }
}
@Serializable private data class PageDto<T>(val items: List<T>, val nextCursor: Long? = null)
@Serializable private data class NotificationDto(val sequence: Long, val message: MessageDto, val read: Boolean)
@Serializable private data class PublishDto(val requestId: String, val body: String? = null)
@Serializable private data class PushPreferencesDto(
    val notices: Boolean, val messages: Boolean, val reminders: Boolean, val charges: Boolean,
) {
    fun domain() = PushPreferences(notices, messages, reminders, charges)
}
@Serializable private data class WhatsAppPreferencesDto(val notices: Boolean, val reminders: Boolean, val charges: Boolean) {
    fun domain() = WhatsAppPreferences(notices, reminders, charges)
}
@Serializable private data class PreferencesDto(
    val notices: Boolean, val messages: Boolean, val reminders: Boolean,
    val push: PushPreferencesDto? = null, val whatsapp: WhatsAppPreferencesDto? = null,
) {
    fun domain() = NotificationPreferences(notices, messages, reminders, push?.domain(), whatsapp?.domain())
}

class KtorCommunicationGateway(private val network: AuthenticatedNetworkClient) : CommunicationGateway {
    override suspend fun messages(groupId: GroupId, channel: CommunicationChannel, before: Long?) = network.execute(
        HttpMethod.Get, "api/groups/${groupId.value}/messages",
        PageDto.serializer(MessageDto.serializer()),
        NetworkRequest(query = mapOf("channel" to channel.name) + cursor(before)),
    ).communicationResult { page -> page.domain { it.domain() } }

    override suspend fun publish(groupId: GroupId, channel: CommunicationChannel, requestId: String, body: String) = network.execute(
        HttpMethod.Post, "api/groups/${groupId.value}/messages", MessageDto.serializer(),
        NetworkRequest(Json.encodeToString(PublishDto(requestId, body)), query = mapOf("channel" to channel.name)),
    ).communicationResult { it.domain() }

    override suspend fun remind(groupId: GroupId, gameId: String, requestId: String) = network.execute(
        HttpMethod.Post, "api/groups/${groupId.value}/games/$gameId/notify-pending", MessageDto.serializer(),
        NetworkRequest(Json.encodeToString(PublishDto(requestId))),
    ).communicationResult { it.domain() }

    override suspend fun inbox(before: Long?) = network.execute(
        HttpMethod.Get, "api/me/notifications", PageDto.serializer(NotificationDto.serializer()), NetworkRequest(query = cursor(before)),
    ).communicationResult { page -> page.domain { dto -> dto.message.domain()?.let { InAppNotification(dto.sequence, it, dto.read) } } }

    override suspend fun markRead(sequence: Long) = network.executeNoContent(
        HttpMethod.Put, "api/me/notifications/$sequence/read",
    ).communicationResult { Unit }

    override suspend fun preferences() = network.execute(
        HttpMethod.Get, "api/me/notification-preferences", PreferencesDto.serializer(),
    ).communicationResult { it.domain() }

    override suspend fun savePreferences(preferences: NotificationPreferences) = network.execute(
        HttpMethod.Put, "api/me/notification-preferences", PreferencesDto.serializer(),
        NetworkRequest(Json.encodeToString(PreferencesDto(
            preferences.notices, preferences.messages, preferences.reminders,
            preferences.push?.let { PushPreferencesDto(it.notices, it.messages, it.reminders, it.charges) },
            preferences.whatsapp?.let { WhatsAppPreferencesDto(it.notices, it.reminders, it.charges) },
        ))),
    ).communicationResult { it.domain() }
}

private fun cursor(before: Long?) = before?.let { mapOf("before" to it.toString()) }.orEmpty()
private fun <T, R> PageDto<T>.domain(transform: (T) -> R?): CommunicationPage<R>? {
    val mapped = items.mapNotNull(transform)
    return if (mapped.size == items.size && (nextCursor == null || nextCursor > 0)) CommunicationPage(mapped, nextCursor) else null
}
internal fun <T, R> NetworkResult<T>.communicationResult(transform: (T) -> R?): SaqzResult<R, CommunicationError> = when (this) {
    is NetworkResult.Success -> transform(value)?.let { SaqzResult.Success(it) }
        ?: SaqzResult.Failure(CommunicationError(DataError.InvalidResponse))
    is NetworkResult.Failure -> SaqzResult.Failure(CommunicationError(error.domain()))
}
private fun NetworkError.domain(): DataError = when (this) {
    is NetworkError.ApiProblemError -> problem.status.dataError()
    is NetworkError.HttpStatus -> status.dataError()
    NetworkError.Timeout -> DataError.Timeout
    NetworkError.Connectivity -> DataError.Connectivity
    NetworkError.InvalidResponse -> DataError.InvalidResponse
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
    NetworkError.Unknown, NetworkError.Unavailable -> DataError.Unknown
}
private fun Int.dataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
