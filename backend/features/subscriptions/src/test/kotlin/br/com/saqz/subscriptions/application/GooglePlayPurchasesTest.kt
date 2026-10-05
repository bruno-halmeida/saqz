package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GooglePlayPurchasesTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val other = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val api = FakeGooglePlayApi()
    private val store = InMemoryGooglePlay(owners = setOf(owner, other))
    private val submit = SubmitGooglePlayPurchase(api, store, InlineTransactions, clock)
    private val notifications = ProcessGooglePlayNotification("segredo", "app.saqz", api, store, store, InlineTransactions, clock)

    @Test
    fun `a verified purchase becomes the owner's subscription, an order and gets acknowledged`() {
        api.purchases["tok1"] = purchase("tok1", accountId = owner.toString())

        assertEquals(SubmitGooglePlayPurchaseResult.Accepted, submit.execute(owner, "app.saqz.organizador", "tok1"))

        val row = assertNotNull(store.rows["tok1"])
        assertEquals(GooglePlayProduct.ORGANIZADOR_MENSAL, row.product)
        assertTrue(row.isEntitlingAt(now))
        assertTrue(row.acknowledged)
        assertEquals(listOf("tok1"), api.acknowledged)
        assertEquals(setOf("GPA.1"), store.orders.keys)
    }

    @Test
    fun `an already acknowledged purchase is not acknowledged again`() {
        api.purchases["tok1"] = purchase("tok1", accountId = owner.toString(), acknowledged = true)

        submit.execute(owner, "app.saqz.organizador", "tok1")

        assertTrue(api.acknowledged.isEmpty())
    }

    @Test
    fun `unknown tokens, other products, pending purchases and wrong product ids are invalid`() {
        assertEquals(SubmitGooglePlayPurchaseResult.Invalid, submit.execute(owner, "app.saqz.organizador", "nope"))

        api.purchases["moedas"] = purchase("moedas", productId = "app.saqz.moedas")
        assertEquals(SubmitGooglePlayPurchaseResult.Invalid, submit.execute(owner, "app.saqz.moedas", "moedas"))

        api.purchases["pend"] = purchase("pend", state = GooglePlayState.PENDING)
        assertEquals(SubmitGooglePlayPurchaseResult.Invalid, submit.execute(owner, "app.saqz.organizador", "pend"))

        api.purchases["tok1"] = purchase("tok1")
        assertEquals(SubmitGooglePlayPurchaseResult.Invalid, submit.execute(owner, "app.saqz.ilimitado", "tok1"))
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `a purchase made by another account is refused`() {
        api.purchases["tok1"] = purchase("tok1", accountId = other.toString())

        assertEquals(SubmitGooglePlayPurchaseResult.OwnedByAnotherAccount, submit.execute(owner, "app.saqz.organizador", "tok1"))
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `a token without account id stays with whoever sent it first`() {
        api.purchases["tok1"] = purchase("tok1", accountId = null)

        submit.execute(owner, "app.saqz.organizador", "tok1")

        assertEquals(SubmitGooglePlayPurchaseResult.OwnedByAnotherAccount, submit.execute(other, "app.saqz.organizador", "tok1"))
        assertEquals(owner, store.rows.getValue("tok1").ownerUserId)
    }

    @Test
    fun `an upgrade retires the replaced token`() {
        api.purchases["old"] = purchase("old", accountId = owner.toString())
        submit.execute(owner, "app.saqz.organizador", "old")
        api.purchases["new"] = purchase("new", productId = "app.saqz.ilimitado", accountId = owner.toString(), linked = "old", orderId = "GPA.2")

        submit.execute(owner, "app.saqz.ilimitado", "new")

        assertFalse(store.rows.getValue("old").isEntitlingAt(now))
        assertTrue(store.rows.getValue("new").isEntitlingAt(now))
    }

    @Test
    fun `a notification refreshes the subscription from the API`() {
        api.purchases["tok1"] = purchase("tok1", accountId = owner.toString())
        submit.execute(owner, "app.saqz.organizador", "tok1")
        api.purchases["tok1"] = purchase("tok1", accountId = owner.toString(), state = GooglePlayState.ON_HOLD, acknowledged = true)

        assertEquals(ProcessGooglePlayNotificationResult.Accepted, notifications.execute("segredo", command("m1", "tok1")))

        assertEquals(GooglePlayState.ON_HOLD, store.rows.getValue("tok1").state)
        assertFalse(store.rows.getValue("tok1").isEntitlingAt(now))
    }

    @Test
    fun `a notification for a purchase the app has not sent yet creates it from the account id`() {
        api.purchases["tok1"] = purchase("tok1", accountId = owner.toString())

        notifications.execute("segredo", command("m1", "tok1"))

        assertEquals(owner, store.rows.getValue("tok1").ownerUserId)
        assertEquals(listOf("tok1"), api.acknowledged)
    }

    @Test
    fun `a redelivered notification acknowledges again if the first attempt failed`() {
        api.purchases["tok1"] = purchase("tok1", accountId = owner.toString())
        api.failAcknowledge = true
        runCatching { notifications.execute("segredo", command("m1", "tok1")) }
        api.failAcknowledge = false

        notifications.execute("segredo", command("m1", "tok1"))

        assertEquals(listOf("tok1"), api.acknowledged)
        assertTrue(store.rows.getValue("tok1").acknowledged)
    }

    @Test
    fun `unknown owners, other packages and test notifications are accepted and ignored`() {
        api.purchases["tok1"] = purchase("tok1", accountId = null)

        assertEquals(ProcessGooglePlayNotificationResult.Accepted, notifications.execute("segredo", command("m1", "tok1")))
        assertEquals(ProcessGooglePlayNotificationResult.Accepted, notifications.execute("segredo", command("m2", "tok1", "com.outro")))
        assertEquals(ProcessGooglePlayNotificationResult.Accepted, notifications.execute("segredo", command("m3", null)))
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `a wrong or missing webhook token is unauthorized, and a blank configured token refuses everything`() {
        assertEquals(ProcessGooglePlayNotificationResult.Unauthorized, notifications.execute("errado", command("m1", "tok1")))
        assertEquals(ProcessGooglePlayNotificationResult.Unauthorized, notifications.execute(null, command("m1", "tok1")))
        val unconfigured = ProcessGooglePlayNotification("", "app.saqz", api, store, store, InlineTransactions, clock)
        assertEquals(ProcessGooglePlayNotificationResult.Unauthorized, unconfigured.execute("", command("m1", "tok1")))
    }

    private fun command(messageId: String, token: String?, packageName: String = "app.saqz") =
        GooglePlayNotificationCommand(messageId, packageName, 4, token)

    private fun purchase(
        token: String,
        productId: String = "app.saqz.organizador",
        accountId: String? = owner.toString(),
        state: GooglePlayState = GooglePlayState.ACTIVE,
        acknowledged: Boolean = false,
        linked: String? = null,
        orderId: String = "GPA.1",
    ) = GooglePlayPurchase(
        purchaseToken = token,
        productId = productId,
        basePlanId = "mensal",
        state = state,
        expiresAt = now.plusSeconds(30L * 24 * 3600),
        autoRenew = true,
        canceledAt = null,
        obfuscatedAccountId = accountId,
        linkedPurchaseToken = linked,
        latestOrderId = orderId,
        acknowledged = acknowledged,
        testPurchase = true,
    )

    private class FakeGooglePlayApi : GooglePlayPurchasesApi {
        val purchases = mutableMapOf<String, GooglePlayPurchase>()
        val acknowledged = mutableListOf<String>()
        var failAcknowledge = false

        override fun subscription(purchaseToken: String) =
            purchases[purchaseToken]?.let { GooglePlayLookup.Found(it) } ?: GooglePlayLookup.NotFound

        override fun acknowledge(productId: String, purchaseToken: String) {
            if (failAcknowledge) throw GooglePlayUnavailableException("fora do ar")
            acknowledged += purchaseToken
        }
    }

    private class InMemoryGooglePlay(private val owners: Set<UUID>) :
        GooglePlaySubscriptionRepository, GooglePlayNotificationStore {
        val rows = linkedMapOf<String, GooglePlaySubscription>()
        val orders = linkedMapOf<String, GooglePlaySubscription>()
        private val messages = mutableSetOf<String>()

        override fun insertIfAbsent(subscription: GooglePlaySubscription) {
            rows.putIfAbsent(subscription.purchaseToken, subscription)
        }

        override fun findForUpdate(purchaseToken: String) = rows[purchaseToken]

        override fun save(subscription: GooglePlaySubscription) {
            rows[subscription.purchaseToken] = subscription
        }

        override fun findByOwner(ownerUserId: UUID) = rows.values.filter { it.ownerUserId == ownerUserId }

        override fun ownerExists(ownerUserId: UUID) = ownerUserId in owners

        override fun supersede(purchaseToken: String, at: Instant) {
            rows[purchaseToken]?.let { rows[purchaseToken] = it.copy(supersededAt = at) }
        }

        override fun markAcknowledged(purchaseToken: String) {
            rows[purchaseToken]?.let { rows[purchaseToken] = it.copy(acknowledged = true) }
        }

        override fun recordOrder(subscription: GooglePlaySubscription) {
            orders.putIfAbsent(checkNotNull(subscription.latestOrderId), subscription)
        }

        override fun listOrdersForOwner(ownerUserId: UUID, limit: Int) = orders.entries
            .filter { it.value.ownerUserId == ownerUserId }
            .map { GooglePlayOrderRecord(it.key, it.value.product.productId, Instant.EPOCH) }
            .take(limit)

        override fun recordIfNew(messageId: String, notificationType: Int?, purchaseToken: String?) = messages.add(messageId)
    }
}
