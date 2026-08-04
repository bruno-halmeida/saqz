package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.subscription.OwnerPlanUsageLookup
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.CancelSubscription
import br.com.saqz.subscriptions.application.ChangePlan
import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.application.CreateSubscription
import br.com.saqz.subscriptions.application.SubscriptionRepository
import br.com.saqz.subscriptions.application.SubscriptionsTransactionRunner
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Achado do Codex no PR #100: `AlreadySubscribed` e `PendingCheckoutMismatch` precisam
 * lançar exceções DIFERENTES a partir daqui — é o único jeito de o mobile distinguir os
 * dois casos (VUL-119), já que o cliente sozinho não tem como inferir isso.
 */
class SubscriptionCommandControllerTest {
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val requestId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val fixedNow = Instant.parse("2026-07-30T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val identity = RequestIdentity("subject")

    @Test
    fun `pending checkout of a different plan throws its own exception, not the generic conflict`() {
        val controller = controllerWith(
            existing = Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = fixedNow,
            ),
        )

        assertThrows<PendingCheckoutMismatchException> {
            controller.create(identity, createRequest(planId = Plan.ILIMITADO.name, cycle = SubscriptionCycle.ANNUAL.name))
        }
    }

    @Test
    fun `already confirmed subscription keeps the generic conflict exception`() {
        val controller = controllerWith(
            existing = Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_old",
                asaasSubscriptionId = "sub_old",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = fixedNow,
                firstConfirmedAt = fixedNow,
            ),
        )

        assertThrows<SubscriptionConflictException> {
            controller.create(identity, createRequest(planId = Plan.TITULAR.name, cycle = SubscriptionCycle.MONTHLY.name))
        }
    }

    private fun controllerWith(existing: Subscription): SubscriptionCommandController {
        val transaction = object : SubscriptionsTransactionRunner {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        return SubscriptionCommandController(
            actors = SubscriptionActorResolver { ownerId },
            createSubscription = CreateSubscription(
                subscriptions = FixedSubscriptionRepository(existing),
                coupons = UnusedCouponRepository,
                asaasGateway = UnusedAsaasGateway,
                transaction = transaction,
                clock = clock,
            ),
            // Nunca chamados por `create()` — só existem porque o controller exige os 3 use cases.
            changePlan = ChangePlan(
                subscriptions = UnusedSubscriptionRepository,
                asaasGateway = UnusedAsaasGateway,
                usageLookup = OwnerPlanUsageLookup { error("unused") },
                coupons = UnusedCouponRepository,
                transaction = transaction,
                clock = clock,
            ),
            cancelSubscription = CancelSubscription(
                subscriptions = UnusedSubscriptionRepository,
                asaasGateway = UnusedAsaasGateway,
                transaction = transaction,
                clock = clock,
            ),
        )
    }

    private fun createRequest(planId: String, cycle: String) = CreateSubscriptionRequest(
        requestId = requestId.toString(),
        planId = planId,
        cycle = cycle,
        billingType = AsaasBillingType.PIX.name,
        name = "Bruno",
        email = "bruno@example.com",
        cpfCnpj = "52998224725",
    )

    private class FixedSubscriptionRepository(private val existing: Subscription) : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = null
        override fun findByOwnerUserId(ownerUserId: UUID) = existing
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = existing
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) = error("unused")
    }

    private object UnusedSubscriptionRepository : SubscriptionRepository {
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = error("unused")
        override fun findByOwnerUserId(ownerUserId: UUID) = error("unused")
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = error("unused")
        override fun findByPendingUpgradeChargeId(chargeId: String) = error("unused")
        override fun findByLastConfirmedPaymentId(paymentId: String) = error("unused")
        override fun lockOwner(ownerUserId: UUID) = error("unused")
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) = error("unused")
    }

    private object UnusedCouponRepository : CouponRepository {
        override fun findByCode(code: String): Coupon? = error("unused")
        override fun findById(couponId: UUID): Coupon? = error("unused")
        override fun hasRedemption(couponId: UUID, userId: UUID) = error("unused")
        override fun saveRedemption(redemption: CouponRedemption) = error("unused")
    }

    private object UnusedAsaasGateway : AsaasGateway {
        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = error("unused")
        override fun createSubscription(
            asaasCustomerId: String,
            plan: Plan,
            cycle: SubscriptionCycle,
            valueCents: Long,
            billingType: AsaasBillingType,
            idempotencyKey: String,
        ) = error("unused")
        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = error("unused")
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")
        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")
        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = error("unused")
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = error("unused")
    }
}
