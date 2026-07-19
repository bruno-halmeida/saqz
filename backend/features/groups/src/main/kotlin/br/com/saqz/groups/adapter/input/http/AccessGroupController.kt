package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.create.CreateGroupField
import br.com.saqz.groups.application.create.CreateGroupResult
import br.com.saqz.groups.application.create.CreatedGroup
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateGroupRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("timeZone") val timeZone: String,
)

data class CreateGroupResponse(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val version: Long,
    val role: GroupRole,
)

class InvalidGroupRequestException(
    val fieldErrors: Map<String, List<String>>,
) : RuntimeException()

@RestController
class AccessGroupController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val createGroup: CreateGroup,
) {
    @PostMapping("/api/groups")
    fun create(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: CreateGroupRequest,
    ): ResponseEntity<CreateGroupResponse> {
        val actor = actorResolver.resolve(identity)
        val requestId = runCatching { UUID.fromString(request.requestId) }.getOrNull()
            ?: throw InvalidGroupRequestException(
                mapOf("requestId" to listOf("must be a UUID")),
            )
        return when (val result = createGroup.execute(actor, requestId, request.name, request.timeZone)) {
            is CreateGroupResult.Success -> ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result.group.toResponse())
            is CreateGroupResult.Invalid -> throw InvalidGroupRequestException(
                result.fields.associate { field ->
                    when (field) {
                        CreateGroupField.NAME ->
                            "name" to listOf("must be between 2 and 80 characters without controls")
                        CreateGroupField.TIME_ZONE ->
                            "timeZone" to listOf("must be a valid IANA identifier")
                    }
                },
            )
        }
    }
}

private fun CreatedGroup.toResponse() = CreateGroupResponse(id, name, timeZone, version, role)
