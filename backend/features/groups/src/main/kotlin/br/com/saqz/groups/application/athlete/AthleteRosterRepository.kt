package br.com.saqz.groups.application.athlete

import java.util.UUID

interface AthleteRosterRepository {
    fun list(actorId: UUID, groupId: UUID, filter: AthleteRosterFilter): List<AthleteRosterEntry>

    fun findOwnProfile(actor: UUID): OwnAthleteProfile?
}
