package br.com.saqz.sharedkernel.subscription

import java.time.Instant
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
}
