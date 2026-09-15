package br.com.saqz.subscriptions.application

import java.time.Instant
import java.util.UUID

enum class TrialOfferMode { ON, OFF, COUPON_ONLY }

data class TrialCoupon(
    val id: UUID,
    val code: String,
    val campaign: String?,
    val trialDays: Int,
    val validUntil: Instant?,
    val maxUses: Int?,
    val active: Boolean,
    val uses: Long,
) {
    fun isAvailable(now: Instant) = active && (validUntil == null || now < validUntil) && (maxUses == null || uses < maxUses)
}

/** The granting operation runs within the group-creation transaction and owner lock. */
interface TrialOffer {
    fun isAvailable(ownerId: UUID): Boolean
    fun grant(ownerId: UUID, start: (Int) -> Unit): Boolean

    object Open : TrialOffer {
        override fun isAvailable(ownerId: UUID) = true
        override fun grant(ownerId: UUID, start: (Int) -> Unit): Boolean { start(14); return true }
    }
}

enum class TrialCouponSelection { APPLIED, UNAVAILABLE, INELIGIBLE }

interface TrialCampaignStore : TrialOffer {
    fun isPreauthorized(ownerId: UUID): Boolean
    fun enroll(ownerId: UUID, eligible: () -> Boolean): Boolean
    fun mode(): TrialOfferMode
    fun setMode(mode: TrialOfferMode)
    fun list(): List<TrialCoupon>
    fun create(code: String, campaign: String?, trialDays: Int, validUntil: Instant?, maxUses: Int?): TrialCoupon?
    fun deactivate(id: UUID): Boolean
    fun selected(ownerId: UUID): TrialCoupon?
    fun select(ownerId: UUID, code: String, eligible: () -> Boolean): TrialCouponSelection
}
