package br.com.saqz.subscriptions.data.subscription

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.subscriptions.domain.subscription.*
import br.com.saqz.network.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class PlanTransport { TITULAR, ORGANIZADOR, ILIMITADO }

@Serializable
internal enum class SubscriptionCycleTransport { MONTHLY, ANNUAL }

@Serializable
internal enum class BillingTypeTransport { PIX, CREDIT_CARD }

@Serializable
internal enum class SubscriptionStatusTransport { ACTIVE, PAST_DUE, CANCELED }

@Serializable
internal data class PlanResponseTransport(
    val id: PlanTransport,
    val name: String,
    val monthlyPriceCents: Long,
    val annualPriceCents: Long,
    val maxGroups: Int? = null,
    val maxAthletes: Int? = null,
    val multiAdmin: Boolean,
    val reports: Boolean,
    val whatsappSla: Boolean,
)

@Serializable
private data class ValidateCouponRequestTransport(
    val code: String,
    val planId: PlanTransport,
    val cycle: SubscriptionCycleTransport,
)

@Serializable
internal data class ValidateCouponResponseTransport(
    val status: String,
    val code: String? = null,
    val planId: PlanTransport? = null,
    val cycle: SubscriptionCycleTransport? = null,
    val discountPercent: Int? = null,
    val listPriceCents: Long? = null,
    val finalPriceCents: Long? = null,
    val validUntil: String? = null,
)

@Serializable
internal data class SubscriptionUsageTransport(
    val groupsUsed: Int,
    val groupsLimit: Int? = null,
)

@Serializable
internal data class MySubscriptionTransport(
    val status: SubscriptionStatusTransport,
    val entitled: Boolean,
    val plan: PlanTransport,
    val cycle: SubscriptionCycleTransport,
    val pendingPlan: PlanTransport? = null,
    val pendingPlanEffectiveAt: String? = null,
    val currentPeriodEnd: String,
    val paymentMethod: BillingTypeTransport? = null,
    val usage: SubscriptionUsageTransport,
    val readOnly: Boolean,
    val pastDueSince: String? = null,
    val canceledAt: String? = null,
    // VUL-196: o endpoint ainda não expõe — chega nulo até o backend adicionar.
    val cardLast4: String? = null,
    val cardBrand: String? = null,
)

@Serializable
private data class CreditCardTransport(
    val holderName: String,
    val number: String,
    val expiryMonth: String,
    val expiryYear: String,
    val ccv: String,
)

@Serializable
private data class CreditCardHolderInfoTransport(
    val name: String,
    val email: String,
    val cpfCnpj: String,
    val postalCode: String,
    val addressNumber: String,
    val phone: String,
    val addressComplement: String? = null,
    val mobilePhone: String? = null,
)

@Serializable
private data class CreateSubscriptionRequestTransport(
    val requestId: String,
    val planId: PlanTransport,
    val cycle: SubscriptionCycleTransport,
    val billingType: BillingTypeTransport,
    val name: String,
    val email: String,
    val cpfCnpj: String,
    val couponCode: String? = null,
    val creditCard: CreditCardTransport? = null,
    val creditCardHolderInfo: CreditCardHolderInfoTransport? = null,
)

@Serializable
internal data class CreatedSubscriptionTransport(
    val ownerUserId: String,
    val planId: PlanTransport,
    val cycle: SubscriptionCycleTransport,
    val status: SubscriptionStatusTransport,
    val asaasSubscriptionId: String,
    val currentPeriodEnd: String,
    val billingType: BillingTypeTransport,
    val pixCopyPaste: String? = null,
    val invoiceUrl: String? = null,
)

@Serializable
private data class ChangePlanRequestTransport(
    val requestId: String,
    val targetPlanId: PlanTransport,
)

@Serializable
internal data class ChangePlanResultTransport(
    val planId: PlanTransport,
    val pendingPlanId: PlanTransport? = null,
    val pendingPlanEffectiveAt: String? = null,
    val pendingUpgradePlanId: PlanTransport? = null,
    val status: SubscriptionStatusTransport,
    val chargedCents: Long? = null,
    val pixCopyPaste: String? = null,
    val invoiceUrl: String? = null,
)

@Serializable
internal data class CanceledSubscriptionTransport(
    val status: SubscriptionStatusTransport,
    val canceledAt: String,
    val currentPeriodEnd: String,
)

@Serializable
internal data class ReceiptTransport(
    val asaasEventId: String,
    val asaasPaymentId: String? = null,
    val valueCents: Long? = null,
    val confirmedAt: String? = null,
    val processedAt: String,
)

@Serializable
internal data class ReceiptListTransport(val receipts: List<ReceiptTransport>)

class KtorSubscriptionGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : SubscriptionGateway {
    private val json = Json { explicitNulls = false }

    override suspend fun plans() = retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
        network.execute(HttpMethod.Get, "plans", ListSerializer(PlanResponseTransport.serializer()))
    }.mapSubscription { list -> list.map(PlanResponseTransport::toDomain) }

    override suspend fun validateCoupon(code: String, planId: Plan, cycle: SubscriptionCycle) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "coupons/validate",
                ValidateCouponResponseTransport.serializer(),
                NetworkRequest(
                    json.encodeToString(
                        ValidateCouponRequestTransport(code, planId.toTransport(), cycle.toTransport()),
                    ),
                ),
            )
        }.toCouponValidationResult()

    override suspend fun mySubscription() = retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
        network.execute(HttpMethod.Get, "subscriptions/me", MySubscriptionTransport.serializer())
    }.mapSubscription { it.toDomain() }

    override suspend fun create(command: CreateSubscriptionCommand) =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "subscriptions",
                CreatedSubscriptionTransport.serializer(),
                NetworkRequest(json.encodeToString(command.toRequest())),
            )
        }.mapSubscription { it.toDomain() }

    override suspend fun changePlan(command: ChangePlanCommand) =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "subscriptions/me/change-plan",
                ChangePlanResultTransport.serializer(),
                NetworkRequest(json.encodeToString(command.toRequest())),
            )
        }.mapSubscription { it.toDomain() }

    override suspend fun cancel() = network.execute(
        HttpMethod.Post,
        "subscriptions/me/cancel",
        CanceledSubscriptionTransport.serializer(),
    ).mapSubscription { it.toDomain() }

    override suspend fun receipts(limit: Int, offset: Int) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "subscriptions/me/receipts",
                ReceiptListTransport.serializer(),
                NetworkRequest(query = mapOf("limit" to limit.toString(), "offset" to offset.toString())),
            )
        }.mapSubscription { it.receipts.map(ReceiptTransport::toDomain) }
}

private fun String.safety() = if (isBlank()) RetrySafety.Never else RetrySafety.IdempotentWrite

private fun CreateSubscriptionCommand.toRequest() = CreateSubscriptionRequestTransport(
    requestId = requestId,
    planId = planId.toTransport(),
    cycle = cycle.toTransport(),
    billingType = billingType.toTransport(),
    name = name,
    email = email,
    cpfCnpj = cpfCnpj,
    couponCode = couponCode,
    creditCard = creditCard?.toRequest(),
    creditCardHolderInfo = creditCardHolderInfo?.toRequest(),
)

private fun CreditCardInfo.toRequest() = CreditCardTransport(
    holderName = holderName,
    number = number,
    expiryMonth = expiryMonth,
    expiryYear = expiryYear,
    ccv = ccv,
)

private fun CreditCardHolderInfo.toRequest() = CreditCardHolderInfoTransport(
    name = name,
    email = email,
    cpfCnpj = cpfCnpj,
    postalCode = postalCode,
    addressNumber = addressNumber,
    phone = phone,
    addressComplement = addressComplement,
    mobilePhone = mobilePhone,
)

private fun ChangePlanCommand.toRequest() = ChangePlanRequestTransport(requestId, targetPlanId.toTransport())

private fun NetworkResult<ValidateCouponResponseTransport>.toCouponValidationResult():
    SaqzResult<CouponValidation, SubscriptionError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toSubscriptionError())
    is NetworkResult.Success -> value.toDomain()?.let { SaqzResult.Success(it) } ?: invalidResponse()
}

private fun ValidateCouponResponseTransport.toDomain(): CouponValidation? = when (status) {
    "APPLIED" -> toAppliedDomain()
    "NOT_FOUND" -> CouponValidation.NotFound
    "EXPIRED" -> CouponValidation.Expired
    else -> null
}

private data class AppliedIdentity(val code: String, val plan: Plan, val cycle: SubscriptionCycle)

private data class AppliedPricing(val discountPercent: Int, val listPriceCents: Long, val finalPriceCents: Long)

private fun ValidateCouponResponseTransport.toAppliedDomain(): CouponValidation.Applied? {
    val identity = toAppliedIdentity() ?: return null
    val pricing = toAppliedPricing() ?: return null
    return CouponValidation.Applied(
        identity.code, identity.plan, identity.cycle,
        pricing.discountPercent, pricing.listPriceCents, pricing.finalPriceCents,
        validUntil,
    )
}

private fun ValidateCouponResponseTransport.toAppliedIdentity(): AppliedIdentity? {
    val appliedCode = code ?: return null
    val plan = planId?.toDomain() ?: return null
    val appliedCycle = cycle?.toDomain() ?: return null
    return AppliedIdentity(appliedCode, plan, appliedCycle)
}

private fun ValidateCouponResponseTransport.toAppliedPricing(): AppliedPricing? {
    val discount = discountPercent ?: return null
    val listPrice = listPriceCents ?: return null
    val finalPrice = finalPriceCents ?: return null
    return AppliedPricing(discount, listPrice, finalPrice)
}

private inline fun <T, R> NetworkResult<T>.mapSubscription(mapper: (T) -> R): SaqzResult<R, SubscriptionError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toSubscriptionError())
        is NetworkResult.Success -> SaqzResult.Success(mapper(value))
    }

private fun PlanResponseTransport.toDomain() = PlanDetails(
    id = id.toDomain(),
    name = name,
    monthlyPriceCents = monthlyPriceCents,
    annualPriceCents = annualPriceCents,
    maxGroups = maxGroups,
    maxAthletes = maxAthletes,
    multiAdmin = multiAdmin,
    reports = reports,
    whatsappSla = whatsappSla,
)

private fun MySubscriptionTransport.toDomain() = MySubscription(
    status = status.toDomain(),
    entitled = entitled,
    plan = plan.toDomain(),
    cycle = cycle.toDomain(),
    pendingPlan = pendingPlan?.toDomain(),
    pendingPlanEffectiveAt = pendingPlanEffectiveAt,
    currentPeriodEnd = currentPeriodEnd,
    paymentMethod = paymentMethod?.toDomain(),
    usage = usage.toDomain(),
    readOnly = readOnly,
    pastDueSince = pastDueSince,
    canceledAt = canceledAt,
    cardLast4 = cardLast4,
    cardBrand = cardBrand,
)

private fun SubscriptionUsageTransport.toDomain() = SubscriptionUsage(groupsUsed, groupsLimit)

private fun CreatedSubscriptionTransport.toDomain() = CreatedSubscription(
    ownerUserId = ownerUserId,
    planId = planId.toDomain(),
    cycle = cycle.toDomain(),
    status = status.toDomain(),
    asaasSubscriptionId = asaasSubscriptionId,
    currentPeriodEnd = currentPeriodEnd,
    billingType = billingType.toDomain(),
    pixCopyPaste = pixCopyPaste,
    invoiceUrl = invoiceUrl,
)

private fun ChangePlanResultTransport.toDomain() = ChangePlanResult(
    planId = planId.toDomain(),
    pendingPlanId = pendingPlanId?.toDomain(),
    pendingPlanEffectiveAt = pendingPlanEffectiveAt,
    pendingUpgradePlanId = pendingUpgradePlanId?.toDomain(),
    status = status.toDomain(),
    chargedCents = chargedCents,
    pixCopyPaste = pixCopyPaste,
    invoiceUrl = invoiceUrl,
)

private fun CanceledSubscriptionTransport.toDomain() =
    CanceledSubscription(status.toDomain(), canceledAt, currentPeriodEnd)

private fun ReceiptTransport.toDomain() = Receipt(asaasEventId, asaasPaymentId, valueCents, confirmedAt, processedAt)

private fun PlanTransport.toDomain() = when (this) {
    PlanTransport.TITULAR -> Plan.Titular
    PlanTransport.ORGANIZADOR -> Plan.Organizador
    PlanTransport.ILIMITADO -> Plan.Ilimitado
}

private fun Plan.toTransport() = when (this) {
    Plan.Titular -> PlanTransport.TITULAR
    Plan.Organizador -> PlanTransport.ORGANIZADOR
    Plan.Ilimitado -> PlanTransport.ILIMITADO
}

private fun SubscriptionCycleTransport.toDomain() = when (this) {
    SubscriptionCycleTransport.MONTHLY -> SubscriptionCycle.Monthly
    SubscriptionCycleTransport.ANNUAL -> SubscriptionCycle.Annual
}

private fun SubscriptionCycle.toTransport() = when (this) {
    SubscriptionCycle.Monthly -> SubscriptionCycleTransport.MONTHLY
    SubscriptionCycle.Annual -> SubscriptionCycleTransport.ANNUAL
}

private fun BillingTypeTransport.toDomain() = when (this) {
    BillingTypeTransport.PIX -> BillingType.Pix
    BillingTypeTransport.CREDIT_CARD -> BillingType.CreditCard
}

private fun BillingType.toTransport() = when (this) {
    BillingType.Pix -> BillingTypeTransport.PIX
    BillingType.CreditCard -> BillingTypeTransport.CREDIT_CARD
}

private fun SubscriptionStatusTransport.toDomain() = when (this) {
    SubscriptionStatusTransport.ACTIVE -> SubscriptionStatus.Active
    SubscriptionStatusTransport.PAST_DUE -> SubscriptionStatus.PastDue
    SubscriptionStatusTransport.CANCELED -> SubscriptionStatus.Canceled
}

internal fun NetworkError.toSubscriptionError(): SubscriptionError = when (this) {
    is NetworkError.ApiProblemError -> when {
        // VUL-196: shape pinado do Asaas (402), sem `code` — discrimina por `error`, não pelo
        // "problem" padrão do backend. `reason`/`message` sempre vêm juntos nesse contrato.
        problem.error == "card_declined" -> SubscriptionError.CardDeclined(
            reason = problem.reason.orEmpty(),
            message = problem.message.orEmpty(),
        )
        problem.code == "VALIDATION_FAILED" -> SubscriptionError.Validation(
            DataError.Validation(
                ValidationDetails(
                    globalMessages = emptyList(),
                    fieldMessages = problem.fieldErrors.orEmpty(),
                ),
            ),
        )
        problem.code == "SUBSCRIPTION_NOT_FOUND" -> SubscriptionError.NotFound
        problem.code == "SUBSCRIPTION_CONFLICT" -> SubscriptionError.Conflict
        problem.code == "SUBSCRIPTION_PENDING_CHECKOUT_MISMATCH" -> SubscriptionError.PendingCheckoutMismatch
        problem.code == "COUPON_NOT_FOUND" -> SubscriptionError.CouponNotFound
        problem.code == "COUPON_EXPIRED" -> SubscriptionError.CouponExpired
        problem.code == "COUPON_ALREADY_REDEEMED" -> SubscriptionError.CouponAlreadyRedeemed
        problem.code == "DOWNGRADE_BLOCKED" -> SubscriptionError.DowngradeBlocked
        else -> SubscriptionError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> SubscriptionError.Data(status.toDataError())
    NetworkError.Timeout -> SubscriptionError.Data(DataError.Timeout)
    NetworkError.Connectivity -> SubscriptionError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> SubscriptionError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> SubscriptionError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable -> SubscriptionError.Data(DataError.Server)
    NetworkError.Unknown -> SubscriptionError.Data(DataError.Unknown)
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

private fun <T> invalidResponse(): SaqzResult<T, SubscriptionError> =
    SaqzResult.Failure(SubscriptionError.Data(DataError.InvalidResponse))
