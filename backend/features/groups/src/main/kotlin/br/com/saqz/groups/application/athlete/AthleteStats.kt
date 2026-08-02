package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupRole
import java.util.UUID

data class AthleteStatsAggregate(
    val games: Int,
    val eligibleGames: Int,
    val absences: Int,
)

data class AthleteStats(
    val games: Int,
    val attendanceRate: Int?,
    val absences: Int,
)

fun interface AthleteStatsRepository {
    fun find(groupId: UUID, userId: UUID): AthleteStatsAggregate?
}

sealed interface GetAthleteStatsResult {
    data class Success(val stats: AthleteStats) : GetAthleteStatsResult

    data object GroupNotFound : GetAthleteStatsResult

    data object AccessForbidden : GetAthleteStatsResult
}

class GetAthleteStats(
    private val groupReadRepository: GroupReadRepository,
    private val athleteRepository: AthleteRepository,
    private val statsRepository: AthleteStatsRepository,
) {
    fun execute(actor: UUID, groupId: UUID, userId: UUID): GetAthleteStatsResult {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return GetAthleteStatsResult.GroupNotFound
        val role = group.role ?: return GetAthleteStatsResult.GroupNotFound
        if (role == GroupRole.ATHLETE && userId != actor) {
            return GetAthleteStatsResult.AccessForbidden
        }
        athleteRepository.find(groupId, userId)
            ?: return GetAthleteStatsResult.GroupNotFound
        val aggregate = statsRepository.find(groupId, userId)
            ?: return GetAthleteStatsResult.GroupNotFound
        val attendanceRate = aggregate.eligibleGames
            .takeIf { it > 0 }
            ?.let { aggregate.confirmedGames() * 100 / it }
        return GetAthleteStatsResult.Success(
            AthleteStats(aggregate.games, attendanceRate, aggregate.absences),
        )
    }

    private fun AthleteStatsAggregate.confirmedGames(): Int = eligibleGames - absences
}
