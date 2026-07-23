package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.session.CompleteSessionProfile
import br.com.saqz.access.application.session.CompleteSessionProfileResult
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class SessionUserResponse(
    val id: UUID,
    val email: String?,
    val displayName: String,
    val phone: String?,
    val phoneRequired: Boolean,
)

data class SessionMembershipResponse(
    val groupId: UUID,
    val groupName: String,
    val role: String,
)

data class AccessSessionResponse(
    val user: SessionUserResponse,
    val memberships: List<SessionMembershipResponse>,
)

data class UpdateSessionProfileRequest @JsonCreator constructor(
    @JsonProperty("phone") val phone: String,
    @JsonProperty("displayName") val displayName: String? = null,
)

class EmailNotVerifiedException : RuntimeException()

class InvalidDisplayNameException : RuntimeException()

class InvalidPhoneException : RuntimeException()

@RestController
class AccessSessionController(
    private val bootstrapSession: BootstrapSession,
    private val completeSessionProfile: CompleteSessionProfile,
) {
    @PutMapping("/api/session")
    fun session(@AuthenticationPrincipal identity: RequestIdentity): AccessSessionResponse =
        when (val result = bootstrapSession.execute(identity)) {
            BootstrapSessionResult.EmailNotVerified -> throw EmailNotVerifiedException()
            BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            is BootstrapSessionResult.Success -> result.session.toResponse()
        }

    @PatchMapping("/api/session/profile")
    fun updateProfile(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: UpdateSessionProfileRequest,
    ): AccessSessionResponse =
        when (
            val result = completeSessionProfile.execute(identity.subject, request.phone, request.displayName)
        ) {
            CompleteSessionProfileResult.InvalidPhone -> throw InvalidPhoneException()
            CompleteSessionProfileResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            is CompleteSessionProfileResult.Success -> result.session.toResponse()
        }
}

private fun SessionView.toResponse() = AccessSessionResponse(
    user = SessionUserResponse(
        id = user.id,
        email = user.email,
        displayName = user.displayName.value,
        phone = user.phone?.value,
        phoneRequired = user.phone == null,
    ),
    memberships = memberships.map {
        SessionMembershipResponse(
            groupId = it.groupId,
            groupName = it.groupName.value,
            role = it.role,
        )
    },
)
