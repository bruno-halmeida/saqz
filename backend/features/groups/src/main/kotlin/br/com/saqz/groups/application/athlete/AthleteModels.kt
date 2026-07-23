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
