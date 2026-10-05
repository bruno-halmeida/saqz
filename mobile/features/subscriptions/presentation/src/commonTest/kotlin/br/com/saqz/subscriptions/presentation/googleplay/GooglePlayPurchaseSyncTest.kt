package br.com.saqz.subscriptions.presentation.googleplay

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.googleplay.GooglePlaySubmissionError
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.presentation.store.StoreDelivery
import br.com.saqz.subscriptions.presentation.store.StoreRestoreOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GooglePlayPurchaseSyncTest {
    private val port = FakeGooglePlayPort()
    private val gateway = FakeGooglePlayGateway()

    private fun TestScope.sync() = GooglePlayPurchaseSync(port, gateway, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `accepted purchase is published with product and token`() = runTest {
        val sync = sync()
        val published = mutableListOf<MySubscription>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.deliveries.collect { published += it } }

        val delivery = sync.deliver(playPurchase("token-1"))

        assertIs<StoreDelivery.Delivered>(delivery)
        assertEquals(listOf(PLAY_ORGANIZADOR to "token-1"), gateway.submitted)
        assertEquals(1, published.size)
    }

    @Test
    fun `backend refusals and outages map to their delivery`() = runTest {
        gateway.submissions["owned"] = SaqzResult.Failure(GooglePlaySubmissionError.OwnedByAnotherAccount)
        gateway.submissions["invalid"] = SaqzResult.Failure(GooglePlaySubmissionError.Invalid)
        gateway.submissions["down"] = SaqzResult.Failure(GooglePlaySubmissionError.Data(DataError.Server))
        val sync = sync()

        assertEquals(StoreDelivery.OwnedByAnotherAccount, sync.deliver(playPurchase("owned")))
        assertEquals(StoreDelivery.Rejected, sync.deliver(playPurchase("invalid")))
        assertEquals(StoreDelivery.Deferred(DataError.Server), sync.deliver(playPurchase("down")))
    }

    @Test
    fun `login resubmits only completed purchases the backend has not acknowledged`() = runTest {
        port.purchasesResult = GooglePlayPurchasesResult.Loaded(
            listOf(
                playPurchase("acknowledged", acknowledged = true),
                playPurchase("pending", pending = true),
                playPurchase("waiting"),
            ),
        )
        val sync = sync()

        sync.onAuthenticated()

        assertEquals(listOf(PLAY_ORGANIZADOR to "waiting"), gateway.submitted)
    }

    @Test
    fun `restore resubmits every completed purchase of the google account`() = runTest {
        port.purchasesResult = GooglePlayPurchasesResult.Loaded(
            listOf(playPurchase("acknowledged", acknowledged = true), playPurchase("pending", pending = true)),
        )

        val outcome = sync().restore()

        assertEquals(1, assertIs<StoreRestoreOutcome.Restored>(outcome).deliveries.size)
        assertEquals(listOf(PLAY_ORGANIZADOR to "acknowledged"), gateway.submitted)
    }

    @Test
    fun `purchases updated in the background are delivered only when signed in and paid`() = runTest {
        val sync = sync()
        sync.start()
        val listener = checkNotNull(port.listener)

        listener.onGooglePlayPurchaseUpdate(playPurchase("before-login"))
        sync.onAuthenticated()
        listener.onGooglePlayPurchaseUpdate(playPurchase("still-pending", pending = true))
        listener.onGooglePlayPurchaseUpdate(playPurchase("paid"))

        assertEquals(listOf(PLAY_ORGANIZADOR to "paid"), gateway.submitted)
        assertTrue(port.listener != null)
    }
}
