package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class SessionUserResponse(
    val id: UUID,
    val email: String?,
    val displayName: String,
)

data class SessionMembershipResponse(
    val groupId: UUID,
    val groupName: String,
    val role: GroupRole,
)

data class AccessSessionResponse(
    val user: SessionUserResponse,
    val memberships: List<SessionMembershipResponse>,
)

class EmailNotVerifiedException : RuntimeException()

class InvalidDisplayNameException : RuntimeException()

@RestController
class AccessSessionController(
    private val bootstrapSession: BootstrapSession,
) {
    @PutMapping("/api/session")
    fun session(@AuthenticationPrincipal identity: RequestIdentity): AccessSessionResponse =
        when (val result = bootstrapSession.execute(identity)) {
            BootstrapSessionResult.EmailNotVerified -> throw EmailNotVerifiedException()
            BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            is BootstrapSessionResult.Success -> result.session.toResponse()
        }
}

private fun SessionView.toResponse() = AccessSessionResponse(
    user = SessionUserResponse(
        id = user.id,
        email = user.email,
        displayName = user.displayName.value,
    ),
    memberships = memberships.map {
        SessionMembershipResponse(
            groupId = it.groupId,
            groupName = it.groupName.value,
            role = it.role,
        )
    },
)
