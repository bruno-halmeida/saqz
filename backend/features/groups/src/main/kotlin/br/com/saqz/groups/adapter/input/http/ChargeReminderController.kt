package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.communication.*
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ChargeReminderBody @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID,
    @JsonProperty("chargeIds") val chargeIds: List<UUID>,
)
@RestController
class ChargeReminderController(private val actors: VerifiedGroupActorResolver, private val service: ChargeReminderService) {
    @PostMapping("/api/groups/{groupId}/charges/notify")
    fun send(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID, @RequestBody body: ChargeReminderBody) =
        when (val result = service.send(actors.resolve(identity), groupId, body.requestId, body.chargeIds)) {
            is CommunicationResult.Success -> result.value
            is CommunicationResult.Failure -> when (result.reason) {
                CommunicationError.NOT_FOUND -> throw GroupNotFoundException()
                CommunicationError.FORBIDDEN -> throw AccessForbiddenException()
                CommunicationError.CONFLICT -> throw VersionConflictException()
                CommunicationError.INVALID -> throw InvalidGroupRequestException(mapOf("chargeIds" to listOf("is invalid")), 422)
            }
        }
}
