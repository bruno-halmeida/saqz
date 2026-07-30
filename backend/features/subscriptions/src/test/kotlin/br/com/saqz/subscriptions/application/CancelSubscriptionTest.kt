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
    private val transaction = object : SubscriptionsTransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

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
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = periodEnd,
                status = SubscriptionStatus.ACTIVE,
            ),
        )

        val result = CancelSubscription(repo, gateway, transaction, clock).execute(ownerId)

        val success = assertIs<CancelSubscriptionResult.Success>(result)
        assertEquals(fixedNow, success.subscription.canceledAt)
        assertEquals(periodEnd, success.subscription.currentPeriodEnd)
        assertEquals(SubscriptionStatus.ACTIVE, success.subscription.status)
        assertEquals(listOf("sub_1"), gateway.canceledIds)
    }

    @Test
    fun `cancel keeps the pending upgrade charge mapping so a late payment webhook can still find the row`() {
        val repo = FakeSubscriptionRepository()
        val gateway = FakeAsaasGateway()
        repo.save(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_1",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = periodEnd,
                status = SubscriptionStatus.ACTIVE,
                pendingUpgradePlan = Plan.ORGANIZADOR,
                pendingUpgradeChargeId = "pay_upg",
            ),
        )

        val result = CancelSubscription(repo, gateway, transaction, clock).execute(ownerId)

        val success = assertIs<CancelSubscriptionResult.Success>(result)
        assertEquals(fixedNow, success.subscription.canceledAt)
        assertEquals(Plan.ORGANIZADOR, success.subscription.pendingUpgradePlan)
        assertEquals("pay_upg", success.subscription.pendingUpgradeChargeId)
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
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = periodEnd,
                status = SubscriptionStatus.ACTIVE,
                canceledAt = fixedNow.minusSeconds(60),
            ),
        )

        assertEquals(
            CancelSubscriptionResult.AlreadyCanceled,
            CancelSubscription(repo, gateway, transaction, clock).execute(ownerId),
        )
        assertTrue(gateway.canceledIds.isEmpty())
    }

    @Test
    fun `cancel returns not found when owner has no subscription`() {
        val gateway = FakeAsaasGateway()
        assertEquals(
            CancelSubscriptionResult.NotFound,
            CancelSubscription(FakeSubscriptionRepository(), gateway, transaction, clock).execute(ownerId),
        )
        assertNull(FakeSubscriptionRepository().findByOwnerUserId(ownerId))
        assertTrue(gateway.canceledIds.isEmpty())
    }

    @Test
    fun `cancel runs inside a transaction using the row-locking lookup`() {
        val repo = FakeSubscriptionRepository()
        val gateway = FakeAsaasGateway()
        repo.save(
            Subscription(
                ownerUserId = ownerId,
                plan = Plan.TITULAR,
                cycle = SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus_1",
                asaasSubscriptionId = "sub_1",
                billingType = AsaasBillingType.PIX,
                currentPeriodEnd = periodEnd,
                status = SubscriptionStatus.ACTIVE,
            ),
        )
        val recordingTransaction = RecordingTransactionRunner()

        CancelSubscription(repo, gateway, recordingTransaction, clock).execute(ownerId)

        assertTrue(recordingTransaction.wrapped)
        assertTrue(repo.lockedLookups.contains(ownerId))
    }

    private class FakeSubscriptionRepository : SubscriptionRepository {
        private val byOwner = linkedMapOf<UUID, Subscription>()
        val lockedLookups = mutableListOf<UUID>()

        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            byOwner.values.firstOrNull { it.asaasSubscriptionId == asaasSubscriptionId }

        override fun findByOwnerUserId(ownerUserId: UUID) = byOwner[ownerUserId]
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID): Subscription? {
            lockedLookups += ownerUserId
            return byOwner[ownerUserId]
        }

        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = save(subscription)
        override fun save(subscription: Subscription) {
            byOwner[subscription.ownerUserId] = subscription
        }
    }

    private class RecordingTransactionRunner : SubscriptionsTransactionRunner {
        var wrapped = false
        override fun <T> inTransaction(block: () -> T): T {
            wrapped = true
            return block()
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
