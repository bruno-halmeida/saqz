package br.com.saqz.groups.application.profile

import java.time.Instant
import java.util.UUID

data class ProfileStatsAggregate(
    val games: Int,
    val eligibleGames: Int,
    val groups: Int,
)

data class ProfileStats(
    val games: Int,
    val attendanceRate: Int?,
    val groups: Int,
)

fun interface ProfileStatsRepository {
    fun find(userId: UUID, now: Instant): ProfileStatsAggregate
}

class GetProfileStats(
    private val repository: ProfileStatsRepository,
    private val now: () -> Instant,
) {
    fun execute(userId: UUID): ProfileStats {
        val aggregate = repository.find(userId, now())
        val attendanceRate = aggregate.eligibleGames
            .takeIf { it > 0 }
            ?.let { (aggregate.games * 100L / it).toInt() }
        return ProfileStats(aggregate.games, attendanceRate, aggregate.groups)
    }
}
