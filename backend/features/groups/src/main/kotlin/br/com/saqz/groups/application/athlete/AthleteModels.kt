package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
import br.com.saqz.groups.domain.GroupRole
import java.time.Instant
import java.util.UUID

data class AthleteMembership(
    val userId: UUID,
    val displayName: AccessName,
    val role: GroupRole,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
    val nickname: String? = null,
    val secondaryPosition: AthletePosition? = null,
    val level: AthleteLevel? = null,
    val preferredSide: AthletePreferredSide? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

data class UpdateAthleteCommand(
    val groupId: UUID,
    val userId: UUID,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
    val nickname: String? = null,
    val secondaryPosition: AthletePosition? = null,
    val level: AthleteLevel? = null,
    val preferredSide: AthletePreferredSide? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

data class UpdateOwnAthleteProfileCommand(
    val groupId: UUID,
    val userId: UUID,
    val nickname: String?,
    val position: AthletePosition?,
    val secondaryPosition: AthletePosition?,
    val level: AthleteLevel?,
    val preferredSide: AthletePreferredSide?,
    val heightCm: Int?,
)

sealed interface UpdateOwnAthleteProfileResult {
    data class Success(val athlete: AthleteMembership) : UpdateOwnAthleteProfileResult

    data object GroupNotFound : UpdateOwnAthleteProfileResult

    data class Invalid(val fieldErrors: Map<String, List<String>>) : UpdateOwnAthleteProfileResult
}

sealed interface UpdateAthleteResult {
    data class Success(val athlete: AthleteMembership) : UpdateAthleteResult

    data object GroupNotFound : UpdateAthleteResult

    data object AccessForbidden : UpdateAthleteResult

    data class Invalid(val fieldErrors: Map<String, List<String>>) : UpdateAthleteResult
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
    val nickname: String? = null,
    val secondaryPosition: AthletePosition? = null,
    val level: AthleteLevel? = null,
    val preferredSide: AthletePreferredSide? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
    val joinedAt: Instant = Instant.EPOCH,
    val role: GroupRole = GroupRole.ATHLETE,
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
    val nickname: String? = null,
    val secondaryPosition: AthletePosition? = null,
    val level: AthleteLevel? = null,
    val preferredSide: AthletePreferredSide? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
    val joinedAt: Instant = Instant.EPOCH,
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
