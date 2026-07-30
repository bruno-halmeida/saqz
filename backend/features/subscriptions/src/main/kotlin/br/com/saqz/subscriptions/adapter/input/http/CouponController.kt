package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.ValidateCoupon
import br.com.saqz.subscriptions.application.ValidateCouponResult
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class ValidateCouponRequest(
    val code: String? = null,
    val planId: String? = null,
    val cycle: String? = null,
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
)

class InvalidCouponRequestException(val fieldErrors: Map<String, List<String>>) : RuntimeException()

@RestController
class CouponController(
    private val validateCoupon: ValidateCoupon,
) {
    @PostMapping("/coupons/validate")
    fun validate(@RequestBody request: ValidateCouponRequest): ValidateCouponResponse {
        val code = request.code?.takeIf { it.isNotBlank() }
            ?: throw InvalidCouponRequestException(mapOf("code" to listOf("is required")))
        val planId = request.planId?.takeIf { it.isNotBlank() }
            ?: throw InvalidCouponRequestException(mapOf("planId" to listOf("is required")))

        return when (val result = validateCoupon.execute(code, planId, request.cycle)) {
            is ValidateCouponResult.Applied -> ValidateCouponResponse(
                status = "APPLIED",
                code = result.code,
                planId = result.planId,
                cycle = result.cycle,
                discountPercent = result.discountPercent,
                listPriceCents = result.listPriceCents,
                finalPriceCents = result.finalPriceCents,
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
        }
    }
}
