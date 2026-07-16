package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.group.read.GetGroup
import br.com.saqz.access.application.group.read.GetGroupResult
import br.com.saqz.access.application.group.read.GroupView
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.domain.GroupRole
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
    private val bootstrapSession: BootstrapSession,
    private val getGroup: GetGroup,
) {
    @GetMapping("/api/groups/{groupId}")
    fun get(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): ResponseEntity<GroupReadResponse> {
        val actor = when (val result = bootstrapSession.execute(identity)) {
            BootstrapSessionResult.EmailNotVerified -> throw EmailNotVerifiedException()
            BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            is BootstrapSessionResult.Success -> result.session.user.id
        }
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
