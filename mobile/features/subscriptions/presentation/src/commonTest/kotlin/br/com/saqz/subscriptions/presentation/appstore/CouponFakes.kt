package br.com.saqz.subscriptions.presentation.appstore

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.coupon.CampaignCoupon
import br.com.saqz.subscriptions.domain.coupon.CampaignCouponGateway
import br.com.saqz.subscriptions.domain.coupon.CouponPlatform
import br.com.saqz.subscriptions.domain.coupon.CouponValidation
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle

internal data class CouponRequest(val code: String, val plan: Plan, val cycle: SubscriptionCycle, val platform: CouponPlatform)

/** O backend do cupom ainda não está no ar: a tela é testada contra o contrato de `POST /coupons/validate`. */
internal class FakeCouponGateway : CampaignCouponGateway {
    var result: (CouponRequest) -> SaqzResult<CouponValidation, DataError> = { SaqzResult.Success(CouponValidation.Applied(coupon(it))) }
    val requests = mutableListOf<CouponRequest>()

    override suspend fun validate(code: String, plan: Plan, cycle: SubscriptionCycle, platform: CouponPlatform) =
        CouponRequest(code, plan, cycle, platform).also { requests += it }.let(result)
}

internal fun coupon(request: CouponRequest) = CampaignCoupon(
    code = request.code,
    plan = request.plan,
    cycle = request.cycle,
    discountPercent = 30,
    durationCycles = 3,
    campaign = "Verão",
    listPriceCents = 5_990,
    storePriceCents = 4_193,
    validUntil = "2026-12-31T23:59:59Z",
    googlePlayOfferId = if (request.platform == CouponPlatform.Android) "cupom-verao30" else null,
    appStoreOfferCode = if (request.platform == CouponPlatform.Ios) "VERAO30X" else null,
    appStoreRedeemUrl = if (request.platform == CouponPlatform.Ios) REDEEM_URL else null,
)

internal const val REDEEM_URL = "https://apps.apple.com/redeem?ctx=offercodes&id=6700000000&code=VERAO30X"
