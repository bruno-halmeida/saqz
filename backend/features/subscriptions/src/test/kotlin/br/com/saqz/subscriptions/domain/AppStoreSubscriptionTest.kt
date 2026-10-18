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

    @Test
    fun `renewal info stores every field it carries`() {
        val grace = purchased.plus(Duration.ofDays(33))
        val signed = purchased.plusSeconds(60)

        val updated = started().applying(
            renewal(
                autoRenewProductId = AppStoreProduct.ILIMITADO_ANUAL.productId,
                inBillingRetry = true,
                gracePeriodExpiresAt = grace,
                signedAt = signed,
            ),
        )

        assertEquals(true, updated.autoRenew)
        assertEquals(AppStoreProduct.ILIMITADO_ANUAL.productId, updated.autoRenewProductId)
        assertEquals(AppStoreProduct.ILIMITADO_ANUAL, updated.autoRenewProduct)
        assertTrue(updated.inBillingRetry)
        assertEquals(grace, updated.gracePeriodExpiresAt)
        assertEquals(signed, updated.renewalSignedAt)
    }

    @Test
    fun `auto renew change time only moves when auto renew flips`() {
        val first = purchased.plusSeconds(60)
        val second = purchased.plusSeconds(120)
        val third = purchased.plusSeconds(180)
        val onceOff = started().applying(renewal(autoRenew = false, signedAt = first))

        val stillOff = onceOff.applying(renewal(autoRenew = false, signedAt = second))
        assertEquals(first, stillOff.autoRenewChangedAt)

        val backOn = stillOff.applying(renewal(autoRenew = true, signedAt = third))
        assertEquals(third, backOn.autoRenewChangedAt)
    }

    @Test
    fun `renewal info with the same signature time as the stored one is applied`() {
        val signed = purchased.plusSeconds(60)
        val stored = started().applying(renewal(autoRenew = true, signedAt = signed))

        val updated = stored.applying(renewal(autoRenew = false, signedAt = signed))

        assertEquals(false, updated.autoRenew)
    }

    @Test
    fun `pending plan needs a different plan and auto renew not turned off`() {
        val sameProduct = started().applying(renewal(autoRenewProductId = AppStoreProduct.ORGANIZADOR_ANUAL.productId))
        assertNull(sameProduct.pendingPlan)
        assertNull(started().pendingPlan)
        assertNull(started().applying(renewal(autoRenewProductId = "app.outro")).pendingPlan)
        assertEquals(Plan.ILIMITADO, started().applying(renewal(autoRenewProductId = AppStoreProduct.ILIMITADO_MENSAL.productId)).pendingPlan)
    }

    @Test
    fun `a transaction without expiry changes nothing`() {
        val subscription = started()

        val result = subscription.applying(
            transaction(transactionId = "t9", purchaseDate = purchased.plus(month)).copy(expiresDate = null),
            AppStoreProduct.ORGANIZADOR_MENSAL,
        )

        assertSame(subscription, result)
    }

    @Test
    fun `a transaction bought earlier than the latest only becomes a receipt`() {
        val subscription = started()

        val result = subscription.applying(
            transaction(transactionId = "t0", purchaseDate = purchased.minusSeconds(10)),
            AppStoreProduct.TITULAR_MENSAL,
        )

        assertSame(subscription, result)
    }

    @Test
    fun `a newer purchase replaces the latest transaction fields`() {
        val renewalDate = purchased.plus(month)

        val renewed = started().applying(
            transaction(transactionId = "t2", purchaseDate = renewalDate, expiresDate = renewalDate.plus(month)),
            AppStoreProduct.ORGANIZADOR_MENSAL,
        )

        assertEquals("t2", renewed.latestTransactionId)
        assertEquals(renewalDate, renewed.latestPurchaseDate)
        assertEquals(renewalDate, renewed.latestSignedAt)
        assertEquals(renewalDate.plus(month), renewed.expiresAt)
        assertEquals("t1", renewed.originalTransactionId)
    }

    @Test
    fun `transferring to another account drops the renewal state of the old one`() {
        val newOwner = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val withRenewal = started().applying(
            renewal(autoRenew = false, inBillingRetry = true, gracePeriodExpiresAt = purchased.plus(month)),
        )

        val moved = withRenewal.transferredTo(newOwner)

        assertEquals(newOwner, moved.ownerUserId)
        assertNull(moved.autoRenew)
        assertNull(moved.autoRenewProductId)
        assertNull(moved.autoRenewChangedAt)
        assertFalse(moved.inBillingRetry)
        assertNull(moved.gracePeriodExpiresAt)
        assertNull(moved.renewalSignedAt)
        assertEquals(withRenewal.latestTransactionId, moved.latestTransactionId)
        assertEquals(withRenewal.expiresAt, moved.expiresAt)
    }

    @Test
    fun `starting a subscription without an expiry date is refused`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            AppStoreSubscription.startedBy(
                transaction().copy(expiresDate = null),
                AppStoreProduct.ORGANIZADOR_MENSAL,
                owner,
            )
        }
    }

    @Test
    fun `grace period keeps access until it ends and a refund still cuts it`() {
        val grace = purchased.plus(month).plus(Duration.ofDays(16))
        val inGrace = started().applying(renewal(inBillingRetry = true, gracePeriodExpiresAt = grace))

        assertTrue(inGrace.isEntitlingAt(grace.minusSeconds(1)))
        assertFalse(inGrace.isEntitlingAt(grace))
        assertFalse(inGrace.copy(revokedAt = purchased).isEntitlingAt(purchased.plusSeconds(1)))
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
