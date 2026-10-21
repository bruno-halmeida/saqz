package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.communication.CommunicationPage
import br.com.saqz.groups.application.communication.GroupMessage
import br.com.saqz.groups.application.communication.GroupNotification
import br.com.saqz.groups.application.photo.MemberPhotoDirectory
import br.com.saqz.groups.application.photo.NoMemberPhotos
import br.com.saqz.groups.application.photo.tagsOf
import br.com.saqz.groups.application.communication.CommunicationError
import br.com.saqz.groups.application.communication.CommunicationResult
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.NotificationPreferences
import br.com.saqz.groups.application.communication.PushPreferences
import br.com.saqz.groups.application.communication.WhatsAppPreferences
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class PublishGroupMessageRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String?,
    @JsonProperty("body") val body: String?,
)
data class NotifyPendingRequest @JsonCreator constructor(@JsonProperty("requestId") val requestId: String?)
data class NotificationPreferencesRequest @JsonCreator constructor(
    @JsonProperty("notices") val notices: Boolean?,
    @JsonProperty("messages") val messages: Boolean?,
    @JsonProperty("reminders") val reminders: Boolean?,
    @JsonProperty("push") val push: PushPreferencesRequest? = null,
    @JsonProperty("whatsapp") val whatsapp: WhatsAppPreferencesRequest? = null,
)

data class PushPreferencesRequest @JsonCreator constructor(
    @JsonProperty("notices") val notices: Boolean?, @JsonProperty("messages") val messages: Boolean?,
    @JsonProperty("reminders") val reminders: Boolean?, @JsonProperty("charges") val charges: Boolean?,
)
data class WhatsAppPreferencesRequest @JsonCreator constructor(
    @JsonProperty("notices") val notices: Boolean?, @JsonProperty("reminders") val reminders: Boolean?,
    @JsonProperty("charges") val charges: Boolean?,
)

@RestController
class GroupCommunicationController(
    private val actors: VerifiedGroupActorResolver,
    private val service: GroupCommunicationService,
    private val photos: MemberPhotoDirectory = NoMemberPhotos,
) {
    @GetMapping("/api/groups/{groupId}/messages")
    fun messages(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: UUID,
        @RequestParam channel: String,
        @RequestParam(required = false) before: Long?,
    ): CommunicationPage<GroupMessage> {
        val actor = actors.resolve(identity)
        val page = service.messages(actor, groupId, channel(channel), before).value()
        val tags = photos.tagsOf(actor, groupId, page.items.map { it.authorId })
        return page.copy(items = page.items.map { it.copy(photoTag = tags[it.authorId]) })
    }

    @PostMapping("/api/groups/{groupId}/messages")
    fun publish(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: UUID,
        @RequestParam channel: String,
        @RequestBody request: PublishGroupMessageRequest,
    ): GroupMessage {
        val actor = actors.resolve(identity)
        val message = service.publish(actor, groupId, channel(channel), requestId(request.requestId), request.body ?: invalid("body", "is required")).value()
        // Quem publica é membro: o aviso volta já com a foto dele, sem esperar o Atualizar.
        return message.copy(photoTag = photos.tagsOf(actor, groupId, listOf(message.authorId))[message.authorId])
    }

    @PostMapping("/api/groups/{groupId}/games/{gameId}/notify-pending")
    fun remind(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: UUID,
        @PathVariable gameId: UUID,
        @RequestBody request: NotifyPendingRequest,
    ) = service.remind(actors.resolve(identity), groupId, gameId, requestId(request.requestId)).value()

    @GetMapping("/api/me/notifications")
    fun inbox(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam(required = false) before: Long?,
    ): CommunicationPage<GroupNotification> {
        val actor = actors.resolve(identity)
        val page = service.inbox(actor, before).value()
        val tags = page.items.groupBy { it.message.groupId }
            .mapValues { (group, items) -> photos.tagsOf(actor, group, items.map { it.message.authorId }) }
        return page.copy(
            items = page.items.map {
                it.copy(message = it.message.copy(photoTag = tags[it.message.groupId]?.get(it.message.authorId)))
            },
        )
    }

    @PutMapping("/api/me/notifications/{sequence}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable sequence: Long) =
        service.markRead(actors.resolve(identity), sequence)

    @GetMapping("/api/me/notification-preferences")
    fun preferences(@AuthenticationPrincipal identity: RequestIdentity) = service.preferences(actors.resolve(identity))

    @PutMapping("/api/me/notification-preferences")
    fun preferences(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody request: NotificationPreferencesRequest) =
        service.savePreferences(actors.resolve(identity), NotificationPreferences(
            request.notices ?: invalid(), request.messages ?: invalid(), request.reminders ?: invalid(),
            request.push?.let { PushPreferences(it.notices ?: invalid(), it.messages ?: invalid(), it.reminders ?: invalid(), it.charges ?: invalid()) }
                ?: PushPreferences(request.notices, request.messages, request.reminders, request.reminders),
            request.whatsapp?.let { WhatsAppPreferences(it.notices ?: invalid(), it.reminders ?: invalid(), it.charges ?: invalid()) }
                ?: service.preferences(actors.resolve(identity)).whatsapp,
        ))

    private fun channel(raw: String) = runCatching { MessageChannel.valueOf(raw) }.getOrNull()
        ?: invalid("channel", "must be one of ${MessageChannel.entries.joinToString()}")
    private fun requestId(raw: String?) = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: invalid("requestId", "must be a valid UUID")
    private fun invalid(): Nothing = throw InvalidGroupRequestException(mapOf("message" to listOf("is invalid")), 422)
    private fun invalid(field: String, message: String): Nothing =
        throw InvalidGroupRequestException(mapOf(field to listOf(message)), 422)
    private fun <T> CommunicationResult<T>.value(): T = when (this) {
        is CommunicationResult.Success -> value
        is CommunicationResult.Failure -> when (reason) {
            CommunicationError.NOT_FOUND -> throw GroupNotFoundException()
            CommunicationError.FORBIDDEN -> throw AccessForbiddenException()
            CommunicationError.INVALID -> invalid()
            CommunicationError.CONFLICT -> throw VersionConflictException()
            CommunicationError.BODY_BLANK -> invalid("body", "must not be blank")
            CommunicationError.BODY_TOO_LONG -> invalid("body", "must be at most 2000 characters")
            CommunicationError.BODY_CONTROL_CHARACTERS -> invalid("body", "must not contain control characters")
            CommunicationError.CHANNEL_NOT_PUBLISHABLE -> invalid("channel", "is not available for publishing")
            CommunicationError.OBJECTIONABLE -> throw InvalidGroupRequestException(mapOf("body" to listOf("objectionable")), 422)
        }
    }
}
