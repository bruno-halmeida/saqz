package br.com.saqz.subscriptions.domain

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppStoreSubscriptionTest {
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val purchased = Instant.parse("2026-10-01T12:00:00Z")
    private val month = Duration.ofDays(30)

    @Test
    fun `entitles until expiry and stops right after`() {
        val subscription = started()

        assertTrue(subscription.isEntitlingAt(purchased.plus(month).minusSeconds(1)))
        assertFalse(subscription.isEntitlingAt(purchased.plus(month)))
    }

    @Test
    fun `a refund cuts access immediately even inside the paid period`() {
        val refunded = started().applying(
            transaction(revocationDate = purchased.plus(Duration.ofDays(3)), signedAt = purchased.plus(Duration.ofDays(3))),
            AppStoreProduct.ORGANIZADOR_MENSAL,
        )

        assertFalse(refunded.isEntitlingAt(purchased.plus(Duration.ofDays(4))))
    }

    @Test
    fun `a stale refund notice does not undo a later refund reversal`() {
        val reversed = started()
            .applying(transaction(revocationDate = purchased, signedAt = purchased.plusSeconds(10)), AppStoreProduct.ORGANIZADOR_MENSAL)
            .applying(transaction(revocationDate = null, signedAt = purchased.plusSeconds(20)), AppStoreProduct.ORGANIZADOR_MENSAL)
            .applying(transaction(revocationDate = purchased, signedAt = purchased.plusSeconds(10)), AppStoreProduct.ORGANIZADOR_MENSAL)

        assertNull(reversed.revokedAt)
    }

    @Test
    fun `a renewal moves the period forward and an older transaction never moves it back`() {
        val renewal = transaction(
            transactionId = "t2",
            purchaseDate = purchased.plus(month),
            expiresDate = purchased.plus(month).plus(month),
        )
        val renewed = started().applying(renewal, AppStoreProduct.ORGANIZADOR_MENSAL)
        val afterLateFirstPurchase = renewed.applying(transaction(), AppStoreProduct.ORGANIZADOR_MENSAL)

        assertEquals("t2", afterLateFirstPurchase.latestTransactionId)
        assertEquals(purchased.plus(month).plus(month), afterLateFirstPurchase.expiresAt)
    }

    @Test
    fun `an upgrade switches the plan to the new product`() {
        val upgrade = transaction(
            transactionId = "t2",
            productId = AppStoreProduct.ILIMITADO_MENSAL.productId,
            purchaseDate = purchased.plus(Duration.ofDays(10)),
            expiresDate = purchased.plus(Duration.ofDays(40)),
        )

        val upgraded = started().applying(upgrade, AppStoreProduct.ILIMITADO_MENSAL)

        assertEquals(Plan.ILIMITADO, upgraded.product.plan)
    }

    @Test
    fun `billing grace keeps access after expiry`() {
        val retrying = started().applying(
            renewal(inBillingRetry = true, gracePeriodExpiresAt = purchased.plus(month).plus(Duration.ofDays(16))),
        )

        assertTrue(retrying.isEntitlingAt(purchased.plus(month).plus(Duration.ofDays(15))))
        assertFalse(retrying.isEntitlingAt(purchased.plus(month).plus(Duration.ofDays(17))))
    }

    @Test
    fun `a scheduled downgrade shows as pending plan while auto renew stays on`() {
        val downgrading = started().applying(renewal(autoRenewProductId = AppStoreProduct.TITULAR_MENSAL.productId))

        assertEquals(Plan.TITULAR, downgrading.pendingPlan)
        assertNull(downgrading.applying(renewal(autoRenew = false, signedAt = purchased.plusSeconds(60))).pendingPlan)
    }

    @Test
    fun `turning auto renew off records when it happened and ignores older renewal info`() {
        val off = purchased.plus(Duration.ofDays(5))
        val canceled = started().applying(renewal(autoRenew = false, signedAt = off))

        assertEquals(off, canceled.autoRenewChangedAt)
        assertSame(canceled, canceled.applying(renewal(autoRenew = true, signedAt = off.minusSeconds(1))))
    }

    private fun started() = AppStoreSubscription.startedBy(transaction(), AppStoreProduct.ORGANIZADOR_MENSAL, owner)

    private fun transaction(
        transactionId: String = "t1",
        productId: String = AppStoreProduct.ORGANIZADOR_MENSAL.productId,
        purchaseDate: Instant = purchased,
        expiresDate: Instant = purchased.plus(month),
        revocationDate: Instant? = null,
        signedAt: Instant = purchaseDate,
    ) = AppStoreTransaction(
        transactionId = transactionId,
        originalTransactionId = "t1",
        productId = productId,
        purchaseDate = purchaseDate,
        expiresDate = expiresDate,
        revocationDate = revocationDate,
        appAccountToken = owner,
        environment = AppStoreEnvironment.SANDBOX,
        priceMillis = 59_900,
        currency = "BRL",
        signedAt = signedAt,
    )

    private fun renewal(
        autoRenew: Boolean = true,
        autoRenewProductId: String = AppStoreProduct.ORGANIZADOR_MENSAL.productId,
        inBillingRetry: Boolean = false,
        gracePeriodExpiresAt: Instant? = null,
        signedAt: Instant = purchased.plusSeconds(30),
    ) = AppStoreRenewalInfo(
        originalTransactionId = "t1",
        autoRenew = autoRenew,
        autoRenewProductId = autoRenewProductId,
        inBillingRetry = inBillingRetry,
        gracePeriodExpiresAt = gracePeriodExpiresAt,
        signedAt = signedAt,
    )
}
