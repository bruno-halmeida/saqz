package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.session.CompleteSessionProfile
import br.com.saqz.access.application.session.CompleteSessionProfileResult
import br.com.saqz.access.application.session.DeleteAccount
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.hasVerifiedEmail
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class SessionUserResponse(
    val id: UUID,
    val email: String?,
    val displayName: String,
    val nickname: String?,
    val phone: String?,
    val phoneRequired: Boolean,
    val phoneVisibility: String,
    val city: String?,
    val emailVerified: Boolean,
    val photoUrl: String?,
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

class UpdateSessionProfileRequest {
    @get:JsonProperty("displayName")
    @set:JsonProperty("displayName")
    var displayName: String? = null
        set(value) {
            field = value
            displayNameProvided = true
        }

    @get:JsonProperty("nickname")
    @set:JsonProperty("nickname")
    var nickname: String? = null
        set(value) {
            field = value
            nicknameProvided = true
        }

    @get:JsonProperty("phone")
    @set:JsonProperty("phone")
    var phone: String? = null
        set(value) {
            field = value
            phoneProvided = true
        }

    @get:JsonProperty("city")
    @set:JsonProperty("city")
    var city: String? = null
        set(value) {
            field = value
            cityProvided = true
        }

    @get:JsonProperty("phoneVisibility")
    @set:JsonProperty("phoneVisibility")
    var phoneVisibility: String? = null
        set(value) {
            field = value
            phoneVisibilityProvided = true
        }

    @get:JsonIgnore
    internal var displayNameProvided = false
    @get:JsonIgnore
    internal var nicknameProvided = false
    @get:JsonIgnore
    internal var phoneProvided = false
    @get:JsonIgnore
    internal var cityProvided = false
    @get:JsonIgnore
    internal var phoneVisibilityProvided = false
}

class InvalidDisplayNameException : RuntimeException()

class AccountSuspendedException : RuntimeException()

class InvalidPhoneException : RuntimeException()

class InvalidSessionProfileFieldException(val field: String) : RuntimeException()

class AccountNotFoundException : RuntimeException()

@RestController
class AccessSessionController(
    private val bootstrapSession: BootstrapSession,
    private val completeSessionProfile: CompleteSessionProfile,
    private val deleteAccount: DeleteAccount,
) {
    @PutMapping("/api/session")
    fun session(@AuthenticationPrincipal identity: RequestIdentity): AccessSessionResponse =
        when (val result = bootstrapSession.execute(identity)) {
            BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            BootstrapSessionResult.Suspended -> throw AccountSuspendedException()
            is BootstrapSessionResult.Success -> result.session.toResponse(identity.hasVerifiedEmail())
        }

    @PatchMapping("/api/session/profile")
    fun updateProfile(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: UpdateSessionProfileRequest,
    ): AccessSessionResponse =
        when (
            val result =
                completeSessionProfile.execute(
                    subject = identity.subject,
                    rawPhone = request.phone,
                    rawDisplayName = request.displayName,
                    rawNickname = request.nickname,
                    rawCity = request.city,
                    rawPhoneVisibility = request.phoneVisibility,
                    phoneProvided = request.phoneProvided,
                    displayNameProvided = request.displayNameProvided,
                    nicknameProvided = request.nicknameProvided,
                    cityProvided = request.cityProvided,
                    phoneVisibilityProvided = request.phoneVisibilityProvided,
                )
        ) {
            CompleteSessionProfileResult.InvalidPhone -> throw InvalidPhoneException()
            CompleteSessionProfileResult.InvalidDisplayName -> throw InvalidDisplayNameException()
            CompleteSessionProfileResult.InvalidNickname -> throw InvalidSessionProfileFieldException("nickname")
            CompleteSessionProfileResult.InvalidCity -> throw InvalidSessionProfileFieldException("city")
            CompleteSessionProfileResult.InvalidPhoneVisibility ->
                throw InvalidSessionProfileFieldException("phoneVisibility")
            CompleteSessionProfileResult.AccountNotFound -> throw AccountNotFoundException()
            is CompleteSessionProfileResult.Success -> result.session.toResponse(identity.hasVerifiedEmail())
        }

    @DeleteMapping("/api/session")
    fun delete(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<Void> {
        deleteAccount.execute(identity.subject)
        return ResponseEntity.noContent().build()
    }
}

private fun SessionView.toResponse(emailVerified: Boolean) = AccessSessionResponse(
    user = SessionUserResponse(
        id = user.id,
        email = user.email,
        displayName = user.displayName.value,
        nickname = user.nickname,
        phone = user.phone?.value,
        phoneRequired = user.phone == null,
        phoneVisibility = user.phoneVisibility,
        city = user.city,
        emailVerified = emailVerified,
        // O digest vai na URL para o cliente nao servir a foto antiga do cache
        // depois de uma troca: contador reiniciaria em 1 depois de uma remocao.
        photoUrl = user.photoDigest?.let { "$USER_PHOTO_PATH?v=$it" },
    ),
    memberships = memberships.map {
        SessionMembershipResponse(
            groupId = it.groupId,
            groupName = it.groupName.value,
            role = it.role,
        )
    },
)
