package br.com.saqz.subscriptions.domain.subscription

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class Plan { Titular, Organizador, Ilimitado }

enum class SubscriptionCycle { Monthly, Annual }

enum class BillingType { Pix, CreditCard }

enum class SubscriptionStatus { Active, PastDue, Canceled }

data class PlanDetails(
    val id: Plan,
    val name: String,
    val monthlyPriceCents: Long,
    val annualPriceCents: Long,
    val maxGroups: Int?,
    val maxAthletes: Int?,
    val multiAdmin: Boolean,
    val reports: Boolean,
    val whatsappSla: Boolean,
)

sealed interface CouponValidation {
    data class Applied(
        val code: String,
        val planId: Plan,
        val cycle: SubscriptionCycle,
        val discountPercent: Int,
        val listPriceCents: Long,
        val finalPriceCents: Long,
        val validUntil: String?,
    ) : CouponValidation

    data object NotFound : CouponValidation

    data object Expired : CouponValidation
}

data class SubscriptionUsage(val groupsUsed: Int, val groupsLimit: Int?)

data class MySubscription(
    val status: SubscriptionStatus,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val pendingPlan: Plan?,
    val pendingPlanEffectiveAt: String?,
    val currentPeriodEnd: String,
    val paymentMethod: BillingType?,
    val usage: SubscriptionUsage,
    val readOnly: Boolean,
    val pastDueSince: String?,
    val canceledAt: String?,
)

data class CreateSubscriptionCommand(
    val requestId: String,
    val planId: Plan,
    val cycle: SubscriptionCycle,
    val billingType: BillingType,
    val name: String,
    val email: String,
    val cpfCnpj: String,
    val couponCode: String? = null,
)

data class CreatedSubscription(
    val ownerUserId: String,
    val planId: Plan,
    val cycle: SubscriptionCycle,
    val status: SubscriptionStatus,
    val asaasSubscriptionId: String,
    val currentPeriodEnd: String,
    val billingType: BillingType,
    val pixCopyPaste: String?,
    val invoiceUrl: String?,
)

data class ChangePlanCommand(val requestId: String, val targetPlanId: Plan)

data class ChangePlanResult(
    val planId: Plan,
    val pendingPlanId: Plan?,
    val pendingPlanEffectiveAt: String?,
    val pendingUpgradePlanId: Plan?,
    val status: SubscriptionStatus,
    val chargedCents: Long?,
    val pixCopyPaste: String?,
    val invoiceUrl: String?,
)

data class CanceledSubscription(
    val status: SubscriptionStatus,
    val canceledAt: String,
    val currentPeriodEnd: String,
)

data class Receipt(
    val asaasEventId: String,
    val asaasPaymentId: String?,
    val valueCents: Long?,
    val confirmedAt: String?,
    val processedAt: String,
)

sealed interface SubscriptionError : SaqzError {
    data class Validation(val error: DataError.Validation) : SubscriptionError
    data object NotFound : SubscriptionError // 404 SUBSCRIPTION_NOT_FOUND
    data object Conflict : SubscriptionError // 409 SUBSCRIPTION_CONFLICT
    data object CouponNotFound : SubscriptionError // 404 COUPON_NOT_FOUND (create flow)
    data object CouponExpired : SubscriptionError // 410 COUPON_EXPIRED
    data object CouponAlreadyRedeemed : SubscriptionError // 409 COUPON_ALREADY_REDEEMED
    data object DowngradeBlocked : SubscriptionError // 409 DOWNGRADE_BLOCKED
    data class Data(val error: DataError) : SubscriptionError
}

interface SubscriptionGateway {
    suspend fun plans(): SaqzResult<List<PlanDetails>, SubscriptionError>

    suspend fun validateCoupon(
        code: String,
        planId: Plan,
        cycle: SubscriptionCycle = SubscriptionCycle.Monthly,
    ): SaqzResult<CouponValidation, SubscriptionError>

    suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError>

    suspend fun create(command: CreateSubscriptionCommand): SaqzResult<CreatedSubscription, SubscriptionError>

    suspend fun changePlan(command: ChangePlanCommand): SaqzResult<ChangePlanResult, SubscriptionError>

    suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError>

    suspend fun receipts(): SaqzResult<List<Receipt>, SubscriptionError>
}
