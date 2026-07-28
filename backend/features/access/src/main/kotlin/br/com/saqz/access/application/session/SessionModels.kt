package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber
import br.com.saqz.sharedkernel.RequestIdentity
import java.util.UUID

data class UserAccount(
    val id: UUID,
    val firebaseSubject: String,
    val email: String?,
    val displayName: AccessName,
    val phone: PhoneNumber? = null,
    /** Versao da foto guardada, ou null quando a conta nao tem foto. */
    val photoVersion: Long? = null,
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
    val emailVerified: Boolean,
    val displayName: AccessName,
)

/**
 * Ausência do claim conta como não confirmado: tanto a coluna `email_verified`
 * quanto o campo da resposta são não-nulos.
 */
fun RequestIdentity.hasVerifiedEmail(): Boolean = emailVerified == true

data class ProfileCompletion(
    val subject: String,
    val phone: PhoneNumber,
    val displayName: AccessName?,
)

sealed interface BootstrapSessionResult {
    data class Success(val session: SessionView) : BootstrapSessionResult

    data object InvalidDisplayName : BootstrapSessionResult
}

sealed interface CompleteSessionProfileResult {
    data class Success(val session: SessionView) : CompleteSessionProfileResult

    data object InvalidPhone : CompleteSessionProfileResult

    data object InvalidDisplayName : CompleteSessionProfileResult

    data object AccountNotFound : CompleteSessionProfileResult
}
