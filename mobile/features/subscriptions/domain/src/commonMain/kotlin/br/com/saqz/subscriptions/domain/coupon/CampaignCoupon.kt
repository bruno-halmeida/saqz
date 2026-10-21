package br.com.saqz.subscriptions.domain.coupon

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle

/** A loja em que o cupom vai ser usado: o backend devolve a oferta do Play ou o código da Apple. */
enum class CouponPlatform { Android, Ios }

/** Por que o cupom não vale para este plano, ciclo, conta ou loja. */
enum class CouponRejection {
    NotFound,
    Expired,
    InvalidPlan,
    InvalidCycle,
    NotEligible,
    Exhausted,
    NotAvailableOnPlatform,

    /** A oferta ainda está sendo criada na loja: o cupom libera em alguns minutos. */
    PendingOnPlatform,
}

/**
 * Cupom de campanha aceito para um plano e um ciclo. No Android a compra usa a oferta
 * [googlePlayOfferId] do plano base; no iPhone a App Store abre [appStoreRedeemUrl] com o código.
 */
data class CampaignCoupon(
    val code: String,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val discountPercent: Int,
    val durationCycles: Int,
    val campaign: String?,
    val listPriceCents: Long?,
    val storePriceCents: Long?,
    val validUntil: String?,
    val googlePlayOfferId: String? = null,
    val appStoreOfferCode: String? = null,
    val appStoreRedeemUrl: String? = null,
)

sealed interface CouponValidation {
    data class Applied(val coupon: CampaignCoupon) : CouponValidation
    data class Rejected(val reason: CouponRejection) : CouponValidation
}

/** `POST /coupons/validate`: diz se o cupom vale para o plano e o ciclo nesta loja. Não reserva nada. */
interface CampaignCouponGateway {
    suspend fun validate(
        code: String,
        plan: Plan,
        cycle: SubscriptionCycle,
        platform: CouponPlatform,
    ): SaqzResult<CouponValidation, DataError>
}
