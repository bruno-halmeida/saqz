package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.invite.manage.ExpireInvite
import br.com.saqz.groups.application.invite.manage.ExpireInviteResult
import br.com.saqz.groups.application.invite.manage.GetInviteMetadata
import br.com.saqz.groups.application.invite.manage.GetInviteMetadataResult
import br.com.saqz.groups.application.invite.manage.InviteMetadataView
import br.com.saqz.groups.application.invite.manage.RotateInvite
import br.com.saqz.groups.application.invite.manage.RotateInviteResult
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.util.UUID

data class InviteUrlResponse(val inviteUrl: URI, val expiresAt: Instant)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InviteMetadataResponse(
    val active: Boolean,
    val expiresAt: Instant?,
    val createdAt: Instant?,
    val createdByName: String?,
)

@RestController
class AccessInviteManagementController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val rotateInvite: RotateInvite,
    private val expireInvite: ExpireInvite,
    private val getInviteMetadata: GetInviteMetadata,
) {
    @PostMapping("/api/groups/{groupId}/invite")
    fun rotate(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): InviteUrlResponse = when (val result = rotateInvite.execute(actor(identity), parseId(groupId))) {
        RotateInviteResult.GroupNotFound -> throw GroupNotFoundException()
        RotateInviteResult.AccessForbidden -> throw AccessForbiddenException()
        is RotateInviteResult.Success -> InviteUrlResponse(result.inviteUrl, result.expiresAt)
    }

    @GetMapping("/api/groups/{groupId}/invite")
    fun getMetadata(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): InviteMetadataResponse = when (val result = getInviteMetadata.execute(actor(identity), parseId(groupId))) {
        GetInviteMetadataResult.GroupNotFound -> throw GroupNotFoundException()
        GetInviteMetadataResult.AccessForbidden -> throw AccessForbiddenException()
        is GetInviteMetadataResult.Success -> result.metadata.toResponse()
    }

    @DeleteMapping("/api/groups/{groupId}/invite")
    fun expire(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): ResponseEntity<Void> = when (expireInvite.execute(actor(identity), parseId(groupId))) {
        ExpireInviteResult.GroupNotFound -> throw GroupNotFoundException()
        ExpireInviteResult.AccessForbidden -> throw AccessForbiddenException()
        ExpireInviteResult.Success -> ResponseEntity.noContent().build()
    }

    private fun actor(identity: RequestIdentity): UUID = actorResolver.resolve(identity)

    private fun parseId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
        ?: throw GroupNotFoundException()
}

private fun InviteMetadataView.toResponse() = InviteMetadataResponse(
    active = active,
    expiresAt = expiresAt,
    createdAt = createdAt,
    createdByName = createdByName,
)
