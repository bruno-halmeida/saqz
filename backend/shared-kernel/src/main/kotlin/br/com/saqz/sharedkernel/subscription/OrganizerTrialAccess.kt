package br.com.saqz.sharedkernel.subscription

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class TrialStatus { AVAILABLE, ACTIVE, EXPIRED, INELIGIBLE, SUBSCRIBED }

data class OrganizerTrialAccess(
    val status: TrialStatus,
    val startedAt: Instant?,
    val endsAt: Instant?,
    val serverTime: Instant,
    val canCreateGroup: Boolean,
) {
    val readOnly: Boolean get() = status == TrialStatus.EXPIRED
}

fun interface OrganizerTrialAccessLookup {
    fun forOwner(ownerId: UUID): OrganizerTrialAccess
}

interface GroupPlanOwnerLookup {
    fun ownerOf(groupId: UUID): UUID?
    fun ownerForMember(groupId: UUID, actorId: UUID): UUID?

    /** Dono ou admin do grupo: quem marca jogo e, por isso, vê até quando o plano deixa marcar. */
    fun isOrganizer(groupId: UUID, actorId: UUID): Boolean

    /** Fuso do grupo: é nele que o dia do fim do plano começa à 0h. */
    fun zoneOf(groupId: UUID): ZoneId
}
