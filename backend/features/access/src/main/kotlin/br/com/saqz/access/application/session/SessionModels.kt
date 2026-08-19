package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.subscription.PlanOwnerLookup
import java.util.UUID

data class UserAccount(
    val id: UUID,
    val firebaseSubject: String,
    val email: String?,
    val displayName: AccessName,
    val phone: PhoneNumber? = null,
    /** Digest da foto guardada, ou null quando a conta nao tem foto. */
    val photoDigest: String? = null,
    val nickname: String? = null,
    val city: String? = null,
    val phoneVisibility: String = "ADMINS",
)

data class SessionMembership(
    val groupId: UUID,
    val groupName: AccessName,
    val role: String,
)

data class SessionView(
    val user: UserAccount,
    val memberships: List<SessionMembership>,
    /** Paga um plano entitulador: owner da conta mesmo sem grupo criado. */
    val planOwner: Boolean = false,
)

fun SessionView.withPlanOwner(planOwners: PlanOwnerLookup): SessionView =
    copy(planOwner = planOwners.isPlanOwner(user.id))

data class SessionUpsert(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: AccessName,
)

enum class PhoneVisibility {
    EVERYONE,
    ADMINS,
    NOBODY,
}

/**
 * Ausência do claim conta como não confirmado: tanto a coluna `email_verified`
 * quanto o campo da resposta são não-nulos.
 */
fun RequestIdentity.hasVerifiedEmail(): Boolean = emailVerified == true

data class ProfileCompletion(
    val subject: String,
    val phone: PhoneNumber?,
    val displayName: AccessName?,
    val nickname: String? = null,
    val city: String? = null,
    val phoneVisibility: PhoneVisibility? = null,
    val phoneProvided: Boolean = true,
    val displayNameProvided: Boolean = displayName != null,
    val nicknameProvided: Boolean = false,
    val cityProvided: Boolean = false,
    val phoneVisibilityProvided: Boolean = false,
)

sealed interface BootstrapSessionResult {
    data class Success(val session: SessionView) : BootstrapSessionResult

    data object InvalidDisplayName : BootstrapSessionResult

    /** Conta suspensa pelo admin de plataforma: nenhuma sessão é criada ou atualizada. */
    data object Suspended : BootstrapSessionResult
}

sealed interface CompleteSessionProfileResult {
    data class Success(val session: SessionView) : CompleteSessionProfileResult

    /** Conta suspensa pelo admin de plataforma: nenhum campo é atualizado. */
    data object Suspended : CompleteSessionProfileResult

    data object InvalidPhone : CompleteSessionProfileResult

    data object InvalidDisplayName : CompleteSessionProfileResult

    data object InvalidNickname : CompleteSessionProfileResult

    data object InvalidCity : CompleteSessionProfileResult

    data object InvalidPhoneVisibility : CompleteSessionProfileResult

    data object AccountNotFound : CompleteSessionProfileResult
}
