package br.com.saqz.sharedkernel.subscription

import java.util.UUID

/**
 * Cross-feature port for plan entitlements.
 * `null` means unlimited; `0` means none allowed (e.g. no active subscription).
 */
interface SubscriptionLimits {
    fun groupLimitFor(ownerId: UUID): Int?

    fun athleteLimitFor(ownerId: UUID): Int?
}
