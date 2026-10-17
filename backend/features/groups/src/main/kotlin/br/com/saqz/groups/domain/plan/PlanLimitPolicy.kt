package br.com.saqz.groups.domain.plan

import java.util.UUID

object PlanLimitPolicy {
    fun canCreateGroup(ownedGroupCount: Int, groupLimit: Int?): Boolean {
        require(ownedGroupCount >= 0)
        if (groupLimit == null) return true
        return ownedGroupCount < groupLimit
    }

    /**
     * SAI-005: o limite vale por grupo e conta só quem é membro agora daquele grupo (dono fora).
     * Quem já é membro nunca é bloqueado.
     */
    fun canEnterAsAthlete(
        currentMemberIds: Set<UUID>,
        athleteId: UUID,
        athleteLimit: Int?,
    ): Boolean {
        if (athleteId in currentMemberIds) return true
        if (athleteLimit == null) return true
        return currentMemberIds.size < athleteLimit
    }
}
