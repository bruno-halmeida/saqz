package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.membership.AccessMembership
import br.com.saqz.groups.application.membership.ChangeMemberRole
import br.com.saqz.groups.application.membership.ChangeMemberRoleResult
import br.com.saqz.groups.application.membership.ListAccessMemberships
import br.com.saqz.groups.application.membership.ListAccessMembershipsResult
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.PersistedMembershipRole
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ChangeMemberRoleRequest @JsonCreator constructor(
    @JsonProperty("role") val role: String,
)

data class AccessMembershipResponse(
    val userId: UUID,
    val displayName: String,
    val role: GroupRole,
)

@RestController
class AccessMembershipController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val listAccessMemberships: ListAccessMemberships,
    private val changeMemberRole: ChangeMemberRole,
) {
    @GetMapping("/api/groups/{groupId}/memberships")
    fun list(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): List<AccessMembershipResponse> {
        val parsedGroupId = parseId(groupId)
        return when (val result = listAccessMemberships.execute(actor(identity), parsedGroupId)) {
            ListAccessMembershipsResult.GroupNotFound -> throw GroupNotFoundException()
            ListAccessMembershipsResult.AccessForbidden -> throw AccessForbiddenException()
            is ListAccessMembershipsResult.Success -> result.memberships.map(AccessMembership::toResponse)
        }
    }

    @PutMapping("/api/groups/{groupId}/memberships/{userId}/role")
    fun change(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("userId") userId: String,
        @RequestBody request: ChangeMemberRoleRequest,
    ): AccessMembershipResponse {
        val role = runCatching { PersistedMembershipRole.valueOf(request.role) }.getOrNull()
            ?: throw InvalidGroupRequestException(
                mapOf("role" to listOf("must be ADMIN or ATHLETE")),
            )
        return when (val result = changeMemberRole.execute(actor(identity), parseId(groupId), parseId(userId), role)) {
            ChangeMemberRoleResult.GroupNotFound -> throw GroupNotFoundException()
            ChangeMemberRoleResult.AccessForbidden,
            ChangeMemberRoleResult.OwnerImmutable,
            -> throw AccessForbiddenException()
            is ChangeMemberRoleResult.Success -> result.membership.toResponse()
        }
    }

    private fun actor(identity: RequestIdentity): UUID = actorResolver.resolve(identity)

    private fun parseId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
        ?: throw GroupNotFoundException()
}

private fun AccessMembership.toResponse() = AccessMembershipResponse(userId, displayName.value, role)
