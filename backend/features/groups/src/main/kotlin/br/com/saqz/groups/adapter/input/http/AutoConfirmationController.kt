package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.attendance.AutoConfirmAttendance
import br.com.saqz.groups.application.attendance.AutoConfirmationOptInUpdate
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AutoConfirmationRequest @JsonCreator constructor(
    @JsonProperty("enabled") val enabled: Boolean?,
)

data class AutoConfirmationResponse(val enabled: Boolean)

@RestController
class AutoConfirmationController(
    private val actors: VerifiedGroupActorResolver,
    private val autoConfirmation: AutoConfirmAttendance,
) {
    @PutMapping("/api/groups/{groupId}/athletes/me/auto-confirmation")
    fun update(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @RequestBody request: AutoConfirmationRequest,
    ): AutoConfirmationResponse {
        val enabled = request.enabled ?: invalid("enabled")
        val group = runCatching { UUID.fromString(groupId) }.getOrElse { throw GroupNotFoundException() }
        return when (val result = autoConfirmation.updateOwnOptIn(group, actors.resolve(identity), enabled)) {
            is AutoConfirmationOptInUpdate.Success -> AutoConfirmationResponse(result.enabled)
            AutoConfirmationOptInUpdate.GroupNotFound -> throw GroupNotFoundException()
            AutoConfirmationOptInUpdate.NotMensalista -> invalid("enabled", "only MENSALISTA members may opt in")
            AutoConfirmationOptInUpdate.FeatureDisabled -> invalid("enabled", "group auto-confirmation is disabled")
        }
    }

    private fun invalid(field: String, message: String = "is required or invalid"): Nothing =
        throw InvalidGroupRequestException(mapOf(field to listOf(message)), status = 422)
}
