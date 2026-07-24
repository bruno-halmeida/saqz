package br.com.saqz.groups.domain.athlete

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.group.GroupRole

enum class AthletePosition { LIBERO, PONTA, CENTRAL, OPOSTO, LEVANTADOR }

enum class AthleteMembershipType { MENSALISTA, AVULSO }

enum class AthleteFinancialStatus { EM_DIA, PENDENTE, DESCONHECIDO }

data class AthleteRosterEntry(
    val userId: String,
    val displayName: String,
    val phone: String?,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
    val financialStatus: AthleteFinancialStatus,
)

data class AthleteRosterFilter(
    val search: String? = null,
    val membershipType: AthleteMembershipType? = null,
    val position: AthletePosition? = null,
    val financialStatus: AthleteFinancialStatus? = null,
    val includeInactive: Boolean = false,
)

data class Athlete(
    val userId: String,
    val displayName: String,
    val role: GroupRole,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
)

data class UpdateAthleteCommand(
    val groupId: GroupId,
    val userId: String,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
)

data class OwnAthleteMembership(
    val groupId: GroupId,
    val groupName: String,
    val role: GroupRole,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
)

data class OwnAthleteProfile(
    val userId: String,
    val displayName: String,
    val phone: String?,
    val memberships: List<OwnAthleteMembership>,
)

sealed interface AthleteError : SaqzError {
    data class Validation(val details: ValidationDetails) : AthleteError
    data class DataFailure(val error: DataError) : AthleteError
}

interface AthleteGateway {
    suspend fun roster(
        groupId: GroupId,
        filter: AthleteRosterFilter,
    ): SaqzResult<List<AthleteRosterEntry>, AthleteError>

    suspend fun updateOwnPosition(
        groupId: GroupId,
        position: AthletePosition?,
    ): SaqzResult<Athlete, AthleteError>

    suspend fun updateAthlete(command: UpdateAthleteCommand): SaqzResult<Athlete, AthleteError>

    suspend fun removeAthlete(groupId: GroupId, userId: String): SaqzResult<Unit, AthleteError>

    suspend fun ownProfile(): SaqzResult<OwnAthleteProfile, AthleteError>
}
