package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.invite.manage.ExpireInvite
import br.com.saqz.access.application.invite.manage.ExpireInviteResult
import br.com.saqz.access.application.invite.manage.RotateInvite
import br.com.saqz.access.application.invite.manage.RotateInviteResult
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

data class InviteUrlResponse(val inviteUrl: URI)

@RestController
class AccessInviteManagementController(
    private val bootstrapSession: BootstrapSession,
    private val rotateInvite: RotateInvite,
    private val expireInvite: ExpireInvite,
) {
    @PostMapping("/api/groups/{groupId}/invite")
    fun rotate(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): InviteUrlResponse = when (val result = rotateInvite.execute(actor(identity), parseId(groupId))) {
        RotateInviteResult.GroupNotFound -> throw GroupNotFoundException()
        RotateInviteResult.AccessForbidden -> throw AccessForbiddenException()
        is RotateInviteResult.Success -> InviteUrlResponse(result.inviteUrl)
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

    private fun actor(identity: RequestIdentity): UUID = when (val result = bootstrapSession.execute(identity)) {
        BootstrapSessionResult.EmailNotVerified -> throw EmailNotVerifiedException()
        BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
        is BootstrapSessionResult.Success -> result.session.user.id
    }

    private fun parseId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
        ?: throw GroupNotFoundException()
}
