package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CancelSubscriptionTest {
    private val fixedNow = Instant.parse("2026-07-30T12:00:00Z")
    private val periodEnd = Instant.parse("2026-08-30T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Test
    fun `cancel stops asaas billing and sets canceledAt keeping paid period`() {
        val repo = FakeSubscriptionRepository()
        val gateway = FakeAsaasGateway()
        repo.save(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_1",
                currentPeriodEnd = periodEnd,
                status = SubscriptionStatus.ACTIVE,
                pendingUpgradePlan = Plan.ORGANIZADOR,
                pendingUpgradeChargeId = "pay_upg",
            ),
        )

        val result = CancelSubscription(repo, gateway, clock).execute(ownerId)

        val success = assertIs<CancelSubscriptionResult.Success>(result)
        assertEquals(fixedNow, success.subscription.canceledAt)
        assertEquals(periodEnd, success.subscription.currentPeriodEnd)
        assertEquals(SubscriptionStatus.ACTIVE, success.subscription.status)
        assertNull(success.subscription.pendingUpgradePlan)
        assertNull(success.subscription.pendingUpgradeChargeId)
        assertEquals(listOf("sub_1"), gateway.canceledIds)
    }

    @Test
    fun `cancel is idempotent conflict when already canceled without calling asaas`() {
        val repo = FakeSubscriptionRepository()
        val gateway = FakeAsaasGateway()
        repo.save(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_1",
                currentPeriodEnd = periodEnd,
                status = SubscriptionStatus.ACTIVE,
                canceledAt = fixedNow.minusSeconds(60),
            ),
        )

        assertEquals(CancelSubscriptionResult.AlreadyCanceled, CancelSubscription(repo, gateway, clock).execute(ownerId))
        assertTrue(gateway.canceledIds.isEmpty())
    }

    @Test
    fun `cancel returns not found when owner has no subscription`() {
        val gateway = FakeAsaasGateway()
        assertEquals(
            CancelSubscriptionResult.NotFound,
            CancelSubscription(FakeSubscriptionRepository(), gateway, clock).execute(ownerId),
        )
        assertNull(FakeSubscriptionRepository().findByOwnerUserId(ownerId))
        assertTrue(gateway.canceledIds.isEmpty())
    }

    private class FakeSubscriptionRepository : SubscriptionRepository {
        private val byOwner = linkedMapOf<UUID, Subscription>()
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            byOwner.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }

        override fun findByOwnerUserId(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = save(subscription)
        override fun save(subscription: Subscription) {
            byOwner[subscription.ownerUserId] = subscription
        }
    }

    private class FakeAsaasGateway : AsaasGateway {
        val canceledIds = mutableListOf<String>()
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
        override fun cancelSubscription(asaasSubscriptionId: String) {
            canceledIds += asaasSubscriptionId
        }

        override fun createOneOffCharge(
            asaasCustomerId: String,
            valueCents: Long,
            description: String,
            idempotencyKey: String,
        ) = error("unused")

        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
    }
}
