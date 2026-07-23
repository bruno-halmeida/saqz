package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupRole
import java.util.UUID

data class AthleteMembership(
    val userId: UUID,
    val displayName: AccessName,
    val role: GroupRole,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
)

data class UpdateAthleteCommand(
    val groupId: UUID,
    val userId: UUID,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
)

sealed interface UpdateOwnAthleteProfileResult {
    data class Success(val athlete: AthleteMembership) : UpdateOwnAthleteProfileResult

    data object GroupNotFound : UpdateOwnAthleteProfileResult
}

sealed interface UpdateAthleteResult {
    data class Success(val athlete: AthleteMembership) : UpdateAthleteResult

    data object GroupNotFound : UpdateAthleteResult

    data object AccessForbidden : UpdateAthleteResult
}

sealed interface RemoveAthleteResult {
    data object Success : RemoveAthleteResult

    data object GroupNotFound : RemoveAthleteResult

    data object AccessForbidden : RemoveAthleteResult

    data object OwnerImmutable : RemoveAthleteResult
}

enum class FinancialStatus {
    EM_DIA,
    PENDENTE,
    DESCONHECIDO,
}

data class AthleteRosterFilter(
    val search: String? = null,
    val membershipType: AthleteMembershipType? = null,
    val position: AthletePosition? = null,
    val financialStatus: FinancialStatus? = null,
    val includeInactive: Boolean = false,
)

data class AthleteRosterEntry(
    val userId: UUID,
    val displayName: AccessName,
    val phone: String?,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
    val financialStatus: FinancialStatus,
)

sealed interface ListAthletesResult {
    data class Success(val athletes: List<AthleteRosterEntry>) : ListAthletesResult

    data object GroupNotFound : ListAthletesResult

    data object AccessForbidden : ListAthletesResult
}

data class OwnAthleteMembership(
    val groupId: UUID,
    val groupName: AccessName,
    val role: GroupRole,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
)

data class OwnAthleteProfile(
    val userId: UUID,
    val displayName: AccessName,
    val phone: String?,
    val memberships: List<OwnAthleteMembership>,
)

sealed interface GetOwnAthleteProfileResult {
    data class Success(val profile: OwnAthleteProfile) : GetOwnAthleteProfileResult

    data object NotFound : GetOwnAthleteProfileResult
}
