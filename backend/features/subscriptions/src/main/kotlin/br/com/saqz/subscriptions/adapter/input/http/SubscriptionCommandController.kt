package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.CancelSubscription
import br.com.saqz.subscriptions.application.CancelSubscriptionResult
import br.com.saqz.subscriptions.application.ChangePlan
import br.com.saqz.subscriptions.application.ChangePlanCommand
import br.com.saqz.subscriptions.application.ChangePlanResult
import br.com.saqz.subscriptions.application.CreateSubscription
import br.com.saqz.subscriptions.application.CreateSubscriptionCommand
import br.com.saqz.subscriptions.application.CreateSubscriptionResult
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

fun interface SubscriptionActorResolver {
    fun resolve(identity: RequestIdentity): UUID
}

data class CreateSubscriptionRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String?,
    @JsonProperty("planId") val planId: String?,
    @JsonProperty("cycle") val cycle: String?,
    @JsonProperty("billingType") val billingType: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("email") val email: String?,
    @JsonProperty("cpfCnpj") val cpfCnpj: String?,
    @JsonProperty("couponCode") val couponCode: String? = null,
)

data class ChangePlanRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String?,
    @JsonProperty("targetPlanId") val targetPlanId: String?,
)

data class CreateSubscriptionResponse(
    val ownerUserId: UUID,
    val planId: String,
    val cycle: String,
    val status: String,
    val asaasSubscriptionId: String,
    val currentPeriodEnd: Instant,
    val billingType: String,
    val pixCopyPaste: String?,
    val invoiceUrl: String?,
)

data class ChangePlanResponse(
    val planId: String,
    val pendingPlanId: String?,
    val pendingPlanEffectiveAt: Instant?,
    val pendingUpgradePlanId: String? = null,
    val status: String,
    val chargedCents: Long? = null,
    val pixCopyPaste: String? = null,
    val invoiceUrl: String? = null,
)

data class CancelSubscriptionResponse(
    val status: String,
    val canceledAt: Instant,
    val currentPeriodEnd: Instant,
)

class InvalidSubscriptionRequestException(
    val fieldErrors: Map<String, List<String>>,
) : RuntimeException()

class SubscriptionConflictException : RuntimeException()
class CouponNotFoundException : RuntimeException()
class CouponExpiredException : RuntimeException()
class CouponAlreadyRedeemedException : RuntimeException()
class DowngradeBlockedException : RuntimeException()

@RestController
class SubscriptionCommandController(
    private val actors: SubscriptionActorResolver,
    private val createSubscription: CreateSubscription,
    private val changePlan: ChangePlan,
    private val cancelSubscription: CancelSubscription,
) {
    @PostMapping("/subscriptions")
    fun create(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: CreateSubscriptionRequest,
    ): ResponseEntity<CreateSubscriptionResponse> {
        val ownerUserId = actors.resolve(identity)
        val command = CreateSubscriptionCommand(
            ownerUserId = ownerUserId,
            requestId = parseUuid(request.requestId, "requestId"),
            plan = parsePlan(request.planId, "planId"),
            cycle = parseCycle(request.cycle),
            billingType = parseBilling(request.billingType),
            name = request.name ?: invalid("name"),
            email = request.email ?: invalid("email"),
            cpfCnpj = request.cpfCnpj ?: invalid("cpfCnpj"),
            couponCode = request.couponCode,
        )
        return when (val result = createSubscription.execute(command)) {
            is CreateSubscriptionResult.Success -> ResponseEntity.status(HttpStatus.CREATED).body(
                CreateSubscriptionResponse(
                    ownerUserId = result.subscription.ownerUserId,
                    planId = result.subscription.plan.name,
                    cycle = result.subscription.cycle.name,
                    status = result.subscription.status.name,
                    asaasSubscriptionId = result.subscription.asaasSubscriptionId,
                    currentPeriodEnd = result.subscription.currentPeriodEnd,
                    billingType = result.billingType.name,
                    pixCopyPaste = result.pixCopyPaste,
                    invoiceUrl = result.invoiceUrl,
                ),
            )
            CreateSubscriptionResult.AlreadySubscribed -> throw SubscriptionConflictException()
            CreateSubscriptionResult.PendingCheckoutMismatch -> throw SubscriptionConflictException()
            CreateSubscriptionResult.CouponNotFound -> throw CouponNotFoundException()
            CreateSubscriptionResult.CouponExpired -> throw CouponExpiredException()
            CreateSubscriptionResult.CouponAlreadyRedeemed -> throw CouponAlreadyRedeemedException()
            CreateSubscriptionResult.InvalidCustomerDetails -> throw InvalidSubscriptionRequestException(
                mapOf(
                    "name" to listOf("is required"),
                    "email" to listOf("must be a valid email"),
                    "cpfCnpj" to listOf("must have 11 (CPF) or 14 (CNPJ) digits"),
                ),
            )
        }
    }

    @PostMapping("/subscriptions/me/change-plan")
    fun changePlan(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: ChangePlanRequest,
    ): ChangePlanResponse {
        val ownerUserId = actors.resolve(identity)
        val command = ChangePlanCommand(
            ownerUserId = ownerUserId,
            requestId = parseUuid(request.requestId, "requestId"),
            targetPlan = parsePlan(request.targetPlanId, "targetPlanId"),
        )
        return when (val result = changePlan.execute(command)) {
            is ChangePlanResult.UpgradePendingPayment -> ChangePlanResponse(
                planId = result.subscription.plan.name,
                pendingPlanId = null,
                pendingPlanEffectiveAt = null,
                pendingUpgradePlanId = result.subscription.pendingUpgradePlan?.name,
                status = result.subscription.status.name,
                chargedCents = result.chargedCents,
                pixCopyPaste = result.pixCopyPaste,
                invoiceUrl = result.invoiceUrl,
            )
            is ChangePlanResult.Upgraded -> ChangePlanResponse(
                planId = result.subscription.plan.name,
                pendingPlanId = null,
                pendingPlanEffectiveAt = null,
                status = result.subscription.status.name,
                chargedCents = result.chargedCents,
            )
            is ChangePlanResult.DowngradeScheduled -> ChangePlanResponse(
                planId = result.subscription.plan.name,
                pendingPlanId = result.subscription.pendingPlan?.name,
                pendingPlanEffectiveAt = result.subscription.pendingPlanEffectiveAt,
                status = result.subscription.status.name,
            )
            ChangePlanResult.NotFound, ChangePlanResult.NotActive -> throw SubscriptionNotFoundException()
            ChangePlanResult.SamePlan -> throw InvalidSubscriptionRequestException(
                mapOf("targetPlanId" to listOf("must differ from the current plan")),
            )
            ChangePlanResult.DowngradeBlockedByUsage -> throw DowngradeBlockedException()
            ChangePlanResult.UpgradePendingBlocksChange -> throw SubscriptionConflictException()
        }
    }

    @PostMapping("/subscriptions/me/cancel")
    fun cancel(@AuthenticationPrincipal identity: RequestIdentity): CancelSubscriptionResponse {
        val ownerUserId = actors.resolve(identity)
        return when (val result = cancelSubscription.execute(ownerUserId)) {
            is CancelSubscriptionResult.Success -> CancelSubscriptionResponse(
                status = result.subscription.status.name,
                canceledAt = requireNotNull(result.subscription.canceledAt),
                currentPeriodEnd = result.subscription.currentPeriodEnd,
            )
            CancelSubscriptionResult.NotFound -> throw SubscriptionNotFoundException()
            CancelSubscriptionResult.AlreadyCanceled -> throw SubscriptionConflictException()
        }
    }

    private fun parseUuid(raw: String?, field: String): UUID =
        raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: invalid(field)

    private fun parsePlan(raw: String?, field: String): Plan =
        raw?.let { runCatching { Plan.valueOf(it) }.getOrNull() } ?: invalid(field)

    private fun parseCycle(raw: String?): SubscriptionCycle =
        raw?.let { runCatching { SubscriptionCycle.valueOf(it) }.getOrNull() } ?: invalid("cycle")

    private fun parseBilling(raw: String?): AsaasBillingType =
        raw?.let { runCatching { AsaasBillingType.valueOf(it) }.getOrNull() } ?: invalid("billingType")

    private fun invalid(field: String): Nothing =
        throw InvalidSubscriptionRequestException(mapOf(field to listOf("is required or invalid")))
}
