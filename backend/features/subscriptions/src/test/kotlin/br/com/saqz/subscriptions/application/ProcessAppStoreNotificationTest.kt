package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreRenewalInfo
import br.com.saqz.subscriptions.domain.AppStoreTransaction
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessAppStoreNotificationTest {
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val verifier = ScriptedAppStoreVerifier()
    private val store = InMemoryAppStoreSubscriptions(owners = setOf(owner))
    private val useCase = ProcessAppStoreNotification(verifier, store, store, InlineTransactions)

    @Test
    fun `a notification for a purchase the app has not sent yet creates the subscription`() {
        verifier.notifications["n1"] = notification(appStoreTransaction(owner), type = "SUBSCRIBED")

        assertEquals(ProcessAppStoreNotificationResult.Accepted, useCase.execute("n1"))

        assertEquals(owner, store.rows.getValue("2000000001").ownerUserId)
    }

    @Test
    fun `auto renew turned off is recorded from the renewal info`() {
        verifier.notifications["n1"] = notification(appStoreTransaction(owner))
        verifier.notifications["n2"] = notification(
            appStoreTransaction(owner),
            type = "DID_CHANGE_RENEWAL_STATUS",
            renewal = renewal(autoRenew = false),
        )

        useCase.execute("n1")
        useCase.execute("n2")

        assertEquals(false, store.rows.getValue("2000000001").autoRenew)
    }

    @Test
    fun `a refund revokes the subscription`() {
        val refundedAt = Instant.parse("2026-10-05T10:00:00Z")
        verifier.notifications["n1"] = notification(appStoreTransaction(owner))
        verifier.notifications["n2"] = notification(
            appStoreTransaction(owner, revocationDate = refundedAt, signedAt = refundedAt),
            type = "REFUND",
        )

        useCase.execute("n1")
        useCase.execute("n2")

        assertFalse(store.rows.getValue("2000000001").isEntitlingAt(refundedAt.plusSeconds(1)))
    }

    @Test
    fun `a redelivered notification is applied once`() {
        verifier.notifications["n1"] = notification(appStoreTransaction(owner))
        useCase.execute("n1")
        store.rows.clear()

        useCase.execute("n1")

        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `an unknown owner is accepted and ignored until the app links the purchase`() {
        verifier.notifications["n1"] = notification(appStoreTransaction(owner = null))

        assertEquals(ProcessAppStoreNotificationResult.Accepted, useCase.execute("n1"))
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `a test notification without a transaction is accepted`() {
        verifier.notifications["test"] = notification(transaction = null, type = "TEST")

        assertEquals(ProcessAppStoreNotificationResult.Accepted, useCase.execute("test"))
    }

    @Test
    fun `an unverifiable payload is invalid`() {
        assertEquals(ProcessAppStoreNotificationResult.Invalid, useCase.execute("forged"))
    }

    private fun notification(
        transaction: AppStoreTransaction?,
        type: String = "DID_RENEW",
        renewal: AppStoreRenewalInfo? = null,
    ) = AppStoreNotification(
        notificationUuid = UUID.randomUUID(),
        type = type,
        subtype = null,
        environment = AppStoreEnvironment.SANDBOX,
        signedAt = Instant.parse("2026-10-01T12:00:00Z"),
        transaction = transaction,
        renewalInfo = renewal,
    )

    private fun renewal(autoRenew: Boolean) = AppStoreRenewalInfo(
        originalTransactionId = "2000000001",
        autoRenew = autoRenew,
        autoRenewProductId = AppStoreProduct.ORGANIZADOR_MENSAL.productId,
        inBillingRetry = false,
        gracePeriodExpiresAt = null,
        signedAt = Instant.parse("2026-10-02T12:00:00Z"),
    )
}
