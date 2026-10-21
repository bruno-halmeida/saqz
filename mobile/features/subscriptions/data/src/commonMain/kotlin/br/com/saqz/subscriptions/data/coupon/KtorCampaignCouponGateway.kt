package br.com.saqz.subscriptions.data.coupon

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import br.com.saqz.subscriptions.domain.coupon.CampaignCoupon
import br.com.saqz.subscriptions.domain.coupon.CampaignCouponGateway
import br.com.saqz.subscriptions.domain.coupon.CouponPlatform
import br.com.saqz.subscriptions.domain.coupon.CouponRejection
import br.com.saqz.subscriptions.domain.coupon.CouponValidation
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ValidateCouponTransport(val code: String, val planId: String, val cycle: String, val platform: String)

/** `status` fica texto: um status novo do backend vira "não encontrado", não erro de leitura. */
@Serializable
internal data class CouponValidationTransport(
    val status: String,
    val code: String? = null,
    val planId: String? = null,
    val cycle: String? = null,
    val discountPercent: Int? = null,
    val durationCycles: Int? = null,
    val campaign: String? = null,
    val listPriceCents: Long? = null,
    val storePriceCents: Long? = null,
    val validUntil: String? = null,
    val googlePlayOfferId: String? = null,
    val appStoreOfferCode: String? = null,
    val appStoreRedeemUrl: String? = null,
)

class KtorCampaignCouponGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : CampaignCouponGateway {
    // Validar não grava nada no backend: repetir na falha de rede é seguro.
    override suspend fun validate(
        code: String,
        plan: Plan,
        cycle: SubscriptionCycle,
        platform: CouponPlatform,
    ): SaqzResult<CouponValidation, DataError> {
        val body = ValidateCouponTransport(code, plan.transport(), cycle.transport(), platform.transport())
        val result = retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "coupons/validate",
                CouponValidationTransport.serializer(),
                NetworkRequest(Json.encodeToString(ValidateCouponTransport.serializer(), body)),
            )
        }
        return when (result) {
            is NetworkResult.Success -> SaqzResult.Success(result.value.toDomain(plan, cycle))
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.toDataError())
        }
    }
}

private fun CouponValidationTransport.toDomain(plan: Plan, cycle: SubscriptionCycle): CouponValidation =
    when (status) {
        "APPLIED" -> CouponValidation.Applied(
            CampaignCoupon(
                code = code.orEmpty(),
                plan = planId?.toPlan() ?: plan,
                cycle = this.cycle?.toCycle() ?: cycle,
                discountPercent = discountPercent ?: 0,
                durationCycles = durationCycles ?: 0,
                campaign = campaign,
                listPriceCents = listPriceCents,
                storePriceCents = storePriceCents,
                validUntil = validUntil,
                googlePlayOfferId = googlePlayOfferId,
                appStoreOfferCode = appStoreOfferCode,
                appStoreRedeemUrl = appStoreRedeemUrl,
            ),
        )
        "EXPIRED" -> CouponValidation.Rejected(CouponRejection.Expired)
        "INVALID_PLAN" -> CouponValidation.Rejected(CouponRejection.InvalidPlan)
        "INVALID_CYCLE" -> CouponValidation.Rejected(CouponRejection.InvalidCycle)
        "NOT_ELIGIBLE" -> CouponValidation.Rejected(CouponRejection.NotEligible)
        "EXHAUSTED" -> CouponValidation.Rejected(CouponRejection.Exhausted)
        "NOT_AVAILABLE_ON_PLATFORM" -> CouponValidation.Rejected(CouponRejection.NotAvailableOnPlatform)
        "PENDING_ON_PLATFORM" -> CouponValidation.Rejected(CouponRejection.PendingOnPlatform)
        else -> CouponValidation.Rejected(CouponRejection.NotFound)
    }

private fun Plan.transport() = name.uppercase()

private fun SubscriptionCycle.transport() = if (this == SubscriptionCycle.Annual) "ANNUAL" else "MONTHLY"

private fun CouponPlatform.transport() = if (this == CouponPlatform.Ios) "IOS" else "ANDROID"

private fun String.toPlan() = Plan.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

private fun String.toCycle() = when (this) {
    "ANNUAL" -> SubscriptionCycle.Annual
    "MONTHLY" -> SubscriptionCycle.Monthly
    else -> null
}

private fun NetworkError.toDataError(): DataError = when (this) {
    is NetworkError.ApiProblemError -> problem.status.toDataError()
    is NetworkError.HttpStatus -> status.toDataError()
    NetworkError.Timeout -> DataError.Timeout
    NetworkError.Connectivity -> DataError.Connectivity
    NetworkError.InvalidResponse -> DataError.InvalidResponse
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
    NetworkError.Unavailable -> DataError.Server
    NetworkError.Unknown -> DataError.Unknown
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
