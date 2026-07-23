package br.com.saqz.groups.application.athlete

import java.util.UUID

class GetOwnAthleteProfile(
    private val rosterRepository: AthleteRosterRepository,
) {
    fun execute(actor: UUID): GetOwnAthleteProfileResult =
        rosterRepository.findOwnProfile(actor)?.let { GetOwnAthleteProfileResult.Success(it) }
            ?: GetOwnAthleteProfileResult.NotFound
}
