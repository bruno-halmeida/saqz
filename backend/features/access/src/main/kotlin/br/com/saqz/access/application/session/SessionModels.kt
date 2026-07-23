package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber
import java.util.UUID

data class UserAccount(
    val id: UUID,
    val firebaseSubject: String,
    val email: String?,
    val displayName: AccessName,
    val phone: PhoneNumber? = null,
)

data class SessionMembership(
    val groupId: UUID,
    val groupName: AccessName,
    val role: String,
)

data class SessionView(
    val user: UserAccount,
    val memberships: List<SessionMembership>,
)

data class SessionUpsert(
    val subject: String,
    val email: String?,
    val displayName: AccessName,
)

data class ProfileCompletion(
    val subject: String,
    val phone: PhoneNumber,
    val displayName: AccessName?,
)

sealed interface BootstrapSessionResult {
    data class Success(val session: SessionView) : BootstrapSessionResult

    data object EmailNotVerified : BootstrapSessionResult

    data object InvalidDisplayName : BootstrapSessionResult
}

sealed interface CompleteSessionProfileResult {
    data class Success(val session: SessionView) : CompleteSessionProfileResult

    data object InvalidPhone : CompleteSessionProfileResult

    data object InvalidDisplayName : CompleteSessionProfileResult

    data object AccountNotFound : CompleteSessionProfileResult
}
