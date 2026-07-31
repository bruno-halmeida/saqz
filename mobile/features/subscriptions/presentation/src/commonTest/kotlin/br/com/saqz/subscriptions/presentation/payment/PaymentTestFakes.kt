package br.com.saqz.subscriptions.presentation.payment

import br.com.saqz.access.domain.session.AccessError
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.CanceledSubscription
import br.com.saqz.subscriptions.domain.subscription.ChangePlanCommand
import br.com.saqz.subscriptions.domain.subscription.ChangePlanResult
import br.com.saqz.subscriptions.domain.subscription.CouponValidation
import br.com.saqz.subscriptions.domain.subscription.CreateSubscriptionCommand
import br.com.saqz.subscriptions.domain.subscription.CreatedSubscription
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanDetails
import br.com.saqz.subscriptions.domain.subscription.Receipt
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus

internal class FakeSubscriptionGateway : SubscriptionGateway {
    var plansResult: SaqzResult<List<PlanDetails>, SubscriptionError> = SaqzResult.Success(emptyList())
    var couponResult: SaqzResult<CouponValidation, SubscriptionError> = SaqzResult.Success(CouponValidation.NotFound)
    var createResult: SaqzResult<CreatedSubscription, SubscriptionError> =
        SaqzResult.Failure(SubscriptionError.Conflict)
    var receiptsResults: List<SaqzResult<List<Receipt>, SubscriptionError>> =
        listOf(SaqzResult.Success(emptyList()))

    val createCalls = mutableListOf<CreateSubscriptionCommand>()
    var receiptsCallCount = 0
        private set

    override suspend fun plans() = plansResult

    override suspend fun validateCoupon(code: String, planId: Plan, cycle: SubscriptionCycle) = couponResult

    override suspend fun mySubscription(): SaqzResult<MySubscription, SubscriptionError> =
        error("not used by the payment screen")

    override suspend fun create(command: CreateSubscriptionCommand): SaqzResult<CreatedSubscription, SubscriptionError> {
        createCalls += command
        return createResult
    }

    override suspend fun changePlan(command: ChangePlanCommand): SaqzResult<ChangePlanResult, SubscriptionError> =
        error("not used by the payment screen")

    override suspend fun cancel(): SaqzResult<CanceledSubscription, SubscriptionError> =
        error("not used by the payment screen")

    override suspend fun receipts(): SaqzResult<List<Receipt>, SubscriptionError> {
        val index = receiptsCallCount.coerceAtMost(receiptsResults.lastIndex)
        receiptsCallCount++
        return receiptsResults[index]
    }
}

internal class FakeSessionGateway(
    private val result: SaqzResult<AccessSession, AccessError> = SaqzResult.Success(defaultSession),
) : SessionGateway {
    override suspend fun bootstrap() = result

    override suspend fun completeProfile(phone: String, displayName: String?): SaqzResult<AccessSession, AccessError> =
        result

    override suspend fun uploadPhoto(bytes: ByteArray, mediaType: String): SaqzResult<Unit, AccessError> =
        SaqzResult.Success(Unit)

    companion object {
        val defaultSession = AccessSession(
            user = AccessUser(id = "user-1", email = "ana@exemplo.com", displayName = "Ana Silva"),
            memberships = emptyList(),
        )
    }
}

internal fun createdPix(code: String = "00020126chavepix") = CreatedSubscription(
    ownerUserId = "owner-1",
    planId = Plan.Titular,
    cycle = SubscriptionCycle.Monthly,
    status = SubscriptionStatus.Active,
    asaasSubscriptionId = "sub-1",
    currentPeriodEnd = "2026-08-30T00:00:00Z",
    billingType = BillingType.Pix,
    pixCopyPaste = code,
    invoiceUrl = null,
)

internal fun fakeReceipt() = Receipt(
    asaasEventId = "evt-1",
    asaasPaymentId = "pay-1",
    valueCents = 4_990L,
    confirmedAt = "2026-07-01T00:00:00Z",
    processedAt = "2026-07-01T00:05:00Z",
)
