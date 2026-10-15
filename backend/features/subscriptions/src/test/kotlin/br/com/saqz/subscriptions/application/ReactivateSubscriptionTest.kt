package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactivateSubscriptionTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val periodEnd = Instant.parse("2026-11-12T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val transaction = object : SubscriptionsTransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private fun canceled(
        billingType: AsaasBillingType? = AsaasBillingType.PIX,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        end: Instant = periodEnd,
        firstConfirmedAt: Instant? = Instant.parse("2026-10-12T12:00:00Z"),
        canceledAt: Instant? = Instant.parse("2026-10-14T12:00:00Z"),
    ) = Subscription(
        ownerUserId = ownerId, plan = Plan.TITULAR, cycle = SubscriptionCycle.MONTHLY,
        asaasCustomerId = "cus_1", asaasSubscriptionId = "sub_old", billingType = billingType,
        currentPeriodEnd = end, status = status, canceledAt = canceledAt, firstConfirmedAt = firstConfirmedAt,
    )

    private fun useCase(
        repo: Repo, gateway: Gateway, tokens: CreditCardTokenLookup = CreditCardTokenLookup { null },
    ) = ReactivateSubscription(repo, gateway, transaction, clock, cardTokens = tokens)

    @Test
    fun `reactivating inside the paid period creates the new subscription charging only at period end`() {
        val repo = Repo(canceled())
        val gateway = Gateway()

        val result = useCase(repo, gateway).execute(ownerId, "203.0.113.9")

        val success = assertIs<ReactivateSubscriptionResult.Success>(result)
        assertEquals(LocalDate.parse("2026-11-12"), gateway.created.single().nextDueDate)
        assertEquals("sub_new_1", success.subscription.asaasSubscriptionId)
        assertNull(success.subscription.canceledAt)
        assertEquals(SubscriptionStatus.ACTIVE, success.subscription.status)
        assertEquals(periodEnd, success.subscription.currentPeriodEnd)
        assertEquals(success.subscription, repo.find())
    }

    @Test
    fun `reactivating after the cancel webhook already flipped the row to CANCELED still works`() {
        val repo = Repo(canceled(status = SubscriptionStatus.CANCELED))

        val result = useCase(repo, Gateway()).execute(ownerId, null)

        assertEquals(SubscriptionStatus.ACTIVE, assertIs<ReactivateSubscriptionResult.Success>(result).subscription.status)
    }

    @Test
    fun `reactivating twice does not create a second asaas subscription`() {
        val repo = Repo(canceled())
        val gateway = Gateway()
        val reactivate = useCase(repo, gateway)

        assertIs<ReactivateSubscriptionResult.Success>(reactivate.execute(ownerId, null))
        assertEquals(ReactivateSubscriptionResult.NotCanceled, reactivate.execute(ownerId, null))

        assertEquals(1, gateway.created.size)
    }

    @Test
    fun `a retry after a local failure reuses the same idempotency key for the same cancellation`() {
        val repo = Repo(canceled())
        val gateway = Gateway()
        useCase(repo, gateway).execute(ownerId, null)
        repo.put(canceled())
        useCase(repo, gateway).execute(ownerId, null)

        assertEquals(1, gateway.created.map { it.idempotencyKey }.toSet().size)
        assertEquals("subscription-reactivate:$ownerId:sub_old", gateway.created.first().idempotencyKey)
    }

    @Test
    fun `a subscription that was never canceled is refused without calling asaas`() {
        val gateway = Gateway()
        val result = useCase(Repo(canceled(canceledAt = null)), gateway).execute(ownerId, null)

        assertEquals(ReactivateSubscriptionResult.NotCanceled, result)
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `after the paid period ended it is refused and asks to subscribe again`() {
        val gateway = Gateway()
        val result = useCase(
            Repo(canceled(status = SubscriptionStatus.CANCELED, end = now.minusSeconds(60))), gateway,
        ).execute(ownerId, null)

        assertEquals(ReactivateSubscriptionResult.AccessEnded, result)
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `never paid is refused`() {
        val result = useCase(Repo(canceled(firstConfirmedAt = null)), Gateway()).execute(ownerId, null)

        assertEquals(ReactivateSubscriptionResult.AccessEnded, result)
    }

    @Test
    fun `no subscription is not found`() {
        assertEquals(ReactivateSubscriptionResult.NotFound, useCase(Repo(null), Gateway()).execute(ownerId, null))
    }

    @Test
    fun `last day of the paid period charges today because asaas rejects past due dates`() {
        val gateway = Gateway()
        useCase(Repo(canceled(end = Instant.parse("2026-10-15T20:00:00Z"))), gateway).execute(ownerId, null)

        assertEquals(LocalDate.parse("2026-10-15"), gateway.created.single().nextDueDate)
    }

    @Test
    fun `card subscriptions reuse the saved token and send the payer ip`() {
        val gateway = Gateway()
        val result = useCase(
            Repo(canceled(billingType = AsaasBillingType.CREDIT_CARD)), gateway,
            CreditCardTokenLookup { id -> "tok_for_$id" },
        ).execute(ownerId, "203.0.113.9")

        assertIs<ReactivateSubscriptionResult.Success>(result)
        assertEquals("tok_for_sub_old", gateway.created.single().creditCardToken)
        assertEquals("203.0.113.9", gateway.created.single().remoteIp)
    }

    @Test
    fun `a card subscription without a saved token is refused before calling asaas`() {
        val gateway = Gateway()
        val result = useCase(Repo(canceled(billingType = AsaasBillingType.CREDIT_CARD)), gateway).execute(ownerId, null)

        assertEquals(ReactivateSubscriptionResult.CardUnavailable, result)
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `a scheduled downgrade is what the first charge applies`() {
        val gateway = Gateway()
        val repo = Repo(canceled().copy(plan = Plan.ORGANIZADOR, pendingPlan = Plan.TITULAR))
        useCase(repo, gateway).execute(ownerId, null)

        assertEquals(Plan.TITULAR, gateway.created.single().plan)
        assertEquals(Plan.TITULAR, repo.find()?.pendingPlan)
    }

    private class Repo(initial: Subscription?) : SubscriptionRepository {
        private var current: Subscription? = initial
        fun find() = current
        fun put(subscription: Subscription) { current = subscription }
        override fun findByAsaasSubscriptionId(asaasSubscriptionId: String) =
            current?.takeIf { it.asaasSubscriptionId == asaasSubscriptionId }
        override fun findByOwnerUserId(ownerUserId: UUID) = current
        override fun findByOwnerUserIdForUpdate(ownerUserId: UUID) = current
        override fun findByPendingUpgradeChargeId(chargeId: String) = null
        override fun findByLastConfirmedPaymentId(paymentId: String) = null
        override fun lockOwner(ownerUserId: UUID) = Unit
        override fun insert(subscription: Subscription) = save(subscription)
        override fun save(subscription: Subscription) { current = subscription }
    }

    private class Gateway : AsaasGateway {
        data class Created(
            val plan: Plan, val valueCents: Long, val nextDueDate: LocalDate, val idempotencyKey: String,
            val creditCardToken: String?, val remoteIp: String?,
        )
        val created = mutableListOf<Created>()

        override fun createSubscriptionStartingAt(
            asaasCustomerId: String, plan: Plan, cycle: SubscriptionCycle, valueCents: Long,
            billingType: AsaasBillingType, nextDueDate: LocalDate, idempotencyKey: String,
            creditCardToken: String?, remoteIp: String?,
        ): String {
            // Como o gateway real: a mesma chave devolve a mesma assinatura.
            created.firstOrNull { it.idempotencyKey == idempotencyKey }?.let { return "sub_new_1" }
            created += Created(plan, valueCents, nextDueDate, idempotencyKey, creditCardToken, remoteIp)
            return "sub_new_${created.size}"
        }

        override fun createCustomer(ownerUserId: UUID, name: String, email: String, cpfCnpj: String) = error("unused")
        override fun createSubscription(
            asaasCustomerId: String, plan: Plan, cycle: SubscriptionCycle, valueCents: Long,
            billingType: AsaasBillingType, idempotencyKey: String, creditCard: CreditCardDetails?,
            creditCardHolderInfo: CreditCardHolderInfo?, remoteIp: String?,
        ) = error("unused")
        override fun updateSubscriptionValue(asaasSubscriptionId: String, valueCents: Long) = error("unused")
        override fun cancelSubscription(asaasSubscriptionId: String) = error("unused")
        override fun createOneOffCharge(
            asaasCustomerId: String, valueCents: Long, description: String, idempotencyKey: String,
        ) = error("unused")
        override fun regeneratePixPayload(asaasChargeId: String) = error("unused")
        override fun findLatestPaymentIdForSubscription(asaasSubscriptionId: String) = null
        override fun findPaymentInvoiceUrl(asaasPaymentId: String) = null
        override fun findPayment(asaasPaymentId: String) = null
    }
}
