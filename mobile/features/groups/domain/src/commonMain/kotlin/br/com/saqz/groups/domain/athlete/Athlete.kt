package br.com.saqz.groups.domain.athlete

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.group.GroupRole

enum class AthletePosition { LIBERO, PONTA, CENTRAL, OPOSTO, LEVANTADOR }

enum class AthleteLevel { INICIANTE, INTERMEDIARIO, AVANCADO }

enum class AthletePreferredSide { DIREITA, ESQUERDA, TANTO_FAZ }

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
    val nickname: String? = null,
    val secondaryPosition: AthletePosition? = null,
    val level: AthleteLevel? = null,
    val preferredSide: AthletePreferredSide? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
    /** ISO-8601 vindo da API; a apresentação é dona de formatá-lo. */
    val joinedAt: String = "",
    val role: GroupRole = GroupRole.ATHLETE,
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
    val nickname: String? = null,
    val secondaryPosition: AthletePosition? = null,
    val level: AthleteLevel? = null,
    val preferredSide: AthletePreferredSide? = null,
    val heightCm: Int? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

data class UpdateAthleteCommand(
    val groupId: GroupId,
    val userId: String,
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
    val groupId: GroupId,
    val nickname: String?,
    val position: AthletePosition?,
    val secondaryPosition: AthletePosition?,
    val level: AthleteLevel?,
    val preferredSide: AthletePreferredSide?,
    val heightCm: Int?,
)

data class AthleteStats(
    val games: Int,
    val attendanceRate: Int?,
    val absences: Int,
)

data class OwnAthleteMembership(
    val groupId: GroupId,
    val groupName: String,
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
    val joinedAt: String = "",
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

    /** Compatibilidade com o port do cadastro de atleta enquanto o fluxo 3j/3k evolui. */
    suspend fun updateOwnProfile(
        command: UpdateOwnAthleteProfileCommand,
    ): SaqzResult<Athlete, AthleteError> = updateOwnPosition(command.groupId, command.position)

    suspend fun updateAthlete(command: UpdateAthleteCommand): SaqzResult<Athlete, AthleteError>

    suspend fun stats(groupId: GroupId, userId: String): SaqzResult<AthleteStats, AthleteError>

    suspend fun removeAthlete(groupId: GroupId, userId: String): SaqzResult<Unit, AthleteError>

    suspend fun ownProfile(): SaqzResult<OwnAthleteProfile, AthleteError>
}
