package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.domain.AthletePosition
import java.util.UUID

interface AthleteRepository {
    fun find(groupId: UUID, userId: UUID): AthleteMembership?

    fun updatePosition(groupId: UUID, userId: UUID, position: AthletePosition?): AthleteMembership

    fun update(command: UpdateAthleteCommand): AthleteMembership

    fun remove(groupId: UUID, userId: UUID)
}
