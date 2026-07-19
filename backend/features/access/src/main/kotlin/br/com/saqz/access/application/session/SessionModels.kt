package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import java.util.UUID

data class UserAccount(
    val id: UUID,
    val firebaseSubject: String,
    val email: String?,
    val displayName: AccessName,
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

sealed interface BootstrapSessionResult {
    data class Success(val session: SessionView) : BootstrapSessionResult

    data object EmailNotVerified : BootstrapSessionResult

    data object InvalidDisplayName : BootstrapSessionResult
}
