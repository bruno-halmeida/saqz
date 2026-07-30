package br.com.saqz.sharedkernel.subscription

import java.util.UUID

/**
 * Cross-feature port for counting groups owned by a user.
 * Implemented by :features:groups; consumed by :features:subscriptions.
 */
fun interface OwnedGroupCounter {
    fun countOwnedGroups(ownerUserId: UUID): Int
}
