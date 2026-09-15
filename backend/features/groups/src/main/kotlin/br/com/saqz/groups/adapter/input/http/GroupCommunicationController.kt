package br.com.saqz.groups.adapter.input.http

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
) {
    @GetMapping("/api/groups/{groupId}/messages")
    fun messages(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: UUID,
        @RequestParam channel: String,
        @RequestParam(required = false) before: Long?,
    ) = service.messages(actors.resolve(identity), groupId, channel(channel), before).value()

    @PostMapping("/api/groups/{groupId}/messages")
    fun publish(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: UUID,
        @RequestParam channel: String,
        @RequestBody request: PublishGroupMessageRequest,
    ) = service.publish(actors.resolve(identity), groupId, channel(channel), requestId(request.requestId), request.body ?: invalid()).value()

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
    ) = service.inbox(actors.resolve(identity), before).value()

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

    private fun channel(raw: String) = runCatching { MessageChannel.valueOf(raw) }.getOrNull() ?: invalid()
    private fun requestId(raw: String?) = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: invalid()
    private fun invalid(): Nothing = throw InvalidGroupRequestException(mapOf("message" to listOf("is invalid")), 422)
    private fun <T> CommunicationResult<T>.value(): T = when (this) {
        is CommunicationResult.Success -> value
        is CommunicationResult.Failure -> when (reason) {
            CommunicationError.NOT_FOUND -> throw GroupNotFoundException()
            CommunicationError.FORBIDDEN -> throw AccessForbiddenException()
            CommunicationError.INVALID -> invalid()
            CommunicationError.CONFLICT -> throw VersionConflictException()
        }
    }
}
