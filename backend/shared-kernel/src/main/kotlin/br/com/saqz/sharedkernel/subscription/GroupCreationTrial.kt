package br.com.saqz.sharedkernel.subscription

import java.util.UUID

/** Called only under the owner's group-creation row lock and transaction. */
interface GroupCreationTrial {
    fun isEligible(ownerId: UUID): Boolean
    fun start(ownerId: UUID)

    object None : GroupCreationTrial {
        override fun isEligible(ownerId: UUID) = false
        override fun start(ownerId: UUID) = Unit
    }
}

fun interface OwnerGroupHistory {
    /** Includes soft-deleted groups: deletion must not reset trial eligibility. */
    fun hasEverOwnedGroup(ownerId: UUID): Boolean
}
