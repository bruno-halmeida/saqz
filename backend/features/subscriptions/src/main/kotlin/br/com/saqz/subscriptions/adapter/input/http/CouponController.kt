package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.subscriptions.application.ValidateCoupon
import br.com.saqz.subscriptions.application.ValidateCouponResult
import br.com.saqz.subscriptions.domain.CouponPlatform
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

data class ValidateCouponRequest @JsonCreator constructor(
    @JsonProperty("code") val code: String? = null,
    @JsonProperty("planId") val planId: String? = null,
    @JsonProperty("cycle") val cycle: String? = null,
    /** WEB (padrão, o site), ANDROID ou IOS. */
    @JsonProperty("platform") val platform: String? = null,
)

data class ValidateCouponResponse(
    val status: String,
    val code: String? = null,
    val planId: Plan? = null,
    val cycle: SubscriptionCycle? = null,
    val discountPercent: Int? = null,
    val listPriceCents: Long? = null,
    val finalPriceCents: Long? = null,
    val validUntil: Instant? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val campaign: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val durationCycles: Int? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val storePriceCents: Long? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val googlePlayOfferId: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val appStoreOfferCode: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val appStoreRedeemUrl: String? = null,
)

class InvalidCouponRequestException(val fieldErrors: Map<String, List<String>>) : RuntimeException()

/** Contrato com os apps em docs/subscriptions/google-play.md (seção Cupons). */
@RestController
class CouponController(
    private val validateCoupon: ValidateCoupon,
    private val actors: AuthenticatedActorResolver? = null,
    private val appAppleId: String = "",
) {
    @PostMapping("/coupons/validate")
    fun validate(
        @AuthenticationPrincipal identity: RequestIdentity?,
        @RequestBody request: ValidateCouponRequest,
    ): ValidateCouponResponse {
        val code = request.code?.takeIf { it.isNotBlank() }
            ?: throw InvalidCouponRequestException(mapOf("code" to listOf("is required")))
        val planId = request.planId?.takeIf { it.isNotBlank() }
            ?: throw InvalidCouponRequestException(mapOf("planId" to listOf("is required")))
        val platform = request.platform?.takeIf { it.isNotBlank() }?.let { raw ->
            CouponPlatform.entries.firstOrNull { it.name == raw.trim().uppercase() }
                ?: throw InvalidCouponRequestException(mapOf("platform" to listOf("must be WEB, ANDROID or IOS")))
        } ?: CouponPlatform.WEB
        val userId = identity?.let { actors?.resolve(it)?.userId }

        return when (val result = validateCoupon.execute(code, planId, request.cycle, platform, userId)) {
            is ValidateCouponResult.Applied -> ValidateCouponResponse(
                status = "APPLIED",
                code = result.code,
                planId = result.planId,
                cycle = result.cycle,
                discountPercent = result.discountPercent,
                listPriceCents = result.listPriceCents,
                finalPriceCents = result.finalPriceCents,
                campaign = result.campaign,
                durationCycles = result.durationCycles,
                storePriceCents = result.storePriceCents,
                googlePlayOfferId = result.googlePlayOfferId,
                appStoreOfferCode = result.appStoreOfferCode,
                appStoreRedeemUrl = result.appStoreOfferCode?.let(::redeemUrl),
            )
            ValidateCouponResult.NotFound -> ValidateCouponResponse(status = "NOT_FOUND")
            is ValidateCouponResult.Expired -> ValidateCouponResponse(
                status = "EXPIRED",
                code = result.code,
                validUntil = result.validUntil,
            )
            ValidateCouponResult.InvalidPlan -> throw InvalidCouponRequestException(
                mapOf("planId" to listOf("must be TITULAR, ORGANIZADOR or ILIMITADO")),
            )
            ValidateCouponResult.InvalidCycle -> throw InvalidCouponRequestException(
                mapOf("cycle" to listOf("must be MONTHLY or ANNUAL")),
            )
            ValidateCouponResult.PlanNotCovered -> ValidateCouponResponse(status = "INVALID_PLAN")
            ValidateCouponResult.CycleNotCovered -> ValidateCouponResponse(status = "INVALID_CYCLE")
            ValidateCouponResult.NotEligible -> ValidateCouponResponse(status = "NOT_ELIGIBLE")
            ValidateCouponResult.Exhausted -> ValidateCouponResponse(status = "EXHAUSTED")
            ValidateCouponResult.NotAvailableOnPlatform -> ValidateCouponResponse(status = "NOT_AVAILABLE_ON_PLATFORM")
            ValidateCouponResult.PendingOnPlatform -> ValidateCouponResponse(status = "PENDING_ON_PLATFORM")
        }
    }

    private fun redeemUrl(code: String) =
        "https://apps.apple.com/redeem?ctx=offercodes&id=${URLEncoder.encode(appAppleId, StandardCharsets.UTF_8)}" +
            "&code=${URLEncoder.encode(code, StandardCharsets.UTF_8)}"
}
