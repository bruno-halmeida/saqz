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
import kotlin.test.assertNull

class RecoverUnconfirmedPaymentTest {
    private val now = Instant.parse("2026-08-18T16:40:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

    @Test
    fun `received pix activates a never-confirmed past due subscription`() {
        val pending = pendingSubscription()
        val subscriptions = MemorySubscriptions(pending)
        val useCase = RecoverUnconfirmedPayment(
            subscriptions,
            FixedAsaas(status = "RECEIVED"),
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(pending)

        assertEquals(SubscriptionStatus.ACTIVE, recovered.status)
        assertEquals(now, recovered.firstConfirmedAt)
        assertEquals("pay_1", recovered.lastConfirmedPaymentId)
        assertNull(recovered.pastDueSince)
        assertEquals(SubscriptionStatus.ACTIVE, subscriptions.findByOwnerUserId(ownerId)!!.status)
    }

    @Test
    fun `pending pix leaves the subscription past due`() {
        val pending = pendingSubscription()
        val subscriptions = MemorySubscriptions(pending)
        val useCase = RecoverUnconfirmedPayment(
            subscriptions,
            FixedAsaas(status = "PENDING"),
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(pending)

        assertEquals(SubscriptionStatus.PAST_DUE, recovered.status)
        assertNull(recovered.firstConfirmedAt)
        assertEquals(SubscriptionStatus.PAST_DUE, subscriptions.findByOwnerUserId(ownerId)!!.status)
    }

    @Test
    fun `already confirmed subscription is left untouched`() {
        val active = pendingSubscription().copy(
            status = SubscriptionStatus.ACTIVE,
            firstConfirmedAt = now.minusSeconds(60),
            pastDueSince = null,
        )
        val useCase = RecoverUnconfirmedPayment(
            MemorySubscriptions(active),
            FixedAsaas(status = "RECEIVED"),
            ImmediateTransaction(),
            clock,
        )

        val recovered = useCase.recoverIfPaid(active)

        assertEquals(active, recovered)
    }

    private fun pendingSubscription() = Subscription(
        ownerUserId = ownerId,
        plan = Plan.TITULAR,
        cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1",
        asaasSubscriptionId = "sub_1",
        billingType = AsaasBillingType.PIX,
        currentPeriodEnd = Instant.parse("2026-09-18T00:00:00Z"),
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = now.minusSeconds(600),
        firstConfirmedAt = null,
    )

    private class ImmediateTransaction : SubscriptionsTransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class MemorySubscriptions(initial: Subscription) : SubscriptionRepository {
        private var current: Subscription = initial
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) = null
        override fun findByOwnerUserId(ownerUserId: UUID) = current.takeIf { it.ownerUserId == ownerUserId }
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = findByOwnerUserId(ownerUserId)
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = error("unused")
        override fun save(subscription: Subscription) {
            current = subscription
        }
    }

    private class FixedAsaas(private val status: String) : AsaasGateway {
        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = error("unused")
        override fun createSubscription(
            asaasCustomerId: String,
            plan: Plan,
            cycle: SubscriptionCycle,
            valueCents: Long,
            billingType: AsaasBillingType,
            idempotencyKey: String,
            creditCard: CreditCardDetails?,
            creditCardHolderInfo: CreditCardHolderInfo?,
            remoteIp: String?,
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
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = "pay_1"
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
        override fun findPayment(asaasPaymentId: String) =
            AsaasPaymentSnapshot(id = asaasPaymentId, status = status, invoiceUrl = null)
    }
}
