package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GetGroupResult
import br.com.saqz.groups.application.read.GroupView
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class GroupReadResponse(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val role: GroupRole,
    val version: Long,
)

class GroupNotFoundException : RuntimeException()

class AccessForbiddenException : RuntimeException()

@RestController
class AccessGroupReadController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val getGroup: GetGroup,
) {
    @GetMapping("/api/groups/{groupId}")
    fun get(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): ResponseEntity<GroupReadResponse> {
        val actor = actorResolver.resolve(identity)
        val parsedGroupId = runCatching { UUID.fromString(groupId) }.getOrNull()
            ?: throw GroupNotFoundException()
        return when (val result = getGroup.execute(actor, parsedGroupId)) {
            GetGroupResult.GroupNotFound -> throw GroupNotFoundException()
            GetGroupResult.AccessForbidden -> throw AccessForbiddenException()
            is GetGroupResult.Success -> ResponseEntity
                .ok()
                .eTag(result.group.version.toString())
                .body(result.group.toResponse())
        }
    }
}

private fun GroupView.toResponse() = GroupReadResponse(
    id = id,
    name = name.value,
    timeZone = timeZone.value,
    role = role,
    version = version,
)
