package br.com.saqz.sharedkernel.subscription

import java.util.UUID

/** Also checked by non-HTTP entry points (recurrence, billing, invitation redemption and the automatic attendance reminder and open-game notices). */
fun interface GroupWriteAccess {
    fun canWrite(groupId: UUID): Boolean

    fun requireWrite(groupId: UUID) {
        if (!canWrite(groupId)) throw SubscriptionRequiredException()
    }

    companion object {
        val Unrestricted = GroupWriteAccess { true }
    }
}
