package br.com.saqz.subscriptions.presentation.appstore

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.appstore.AppStoreSubmissionError
import br.com.saqz.subscriptions.domain.port.AppStoreTransactionsResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription
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
class AppStoreTransactionSyncTest {
    private val port = FakeAppStorePort()
    private val gateway = FakeAppStoreGateway()

    private fun TestScope.sync() = AppStoreTransactionSync(
        port,
        gateway,
        kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    @Test
    fun `accepted transaction is finished and published`() = runTest {
        val sync = sync()
        val published = mutableListOf<MySubscription>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.deliveries.collect { published += it } }

        val delivery = sync.deliver(transaction("tx-1"))

        assertIs<AppStoreDelivery.Delivered>(delivery)
        assertEquals(listOf("jws-tx-1"), gateway.submitted)
        assertEquals(listOf("tx-1"), port.finished)
        assertEquals(1, published.size)
    }

    @Test
    fun `transaction of another account and invalid transaction are finished`() = runTest {
        gateway.submissions["jws-tx-1"] = SaqzResult.Failure(AppStoreSubmissionError.OwnedByAnotherAccount)
        gateway.submissions["jws-tx-2"] = SaqzResult.Failure(AppStoreSubmissionError.Invalid)
        val sync = sync()

        assertEquals(AppStoreDelivery.OwnedByAnotherAccount, sync.deliver(transaction("tx-1")))
        assertEquals(AppStoreDelivery.Rejected, sync.deliver(transaction("tx-2")))
        assertEquals(listOf("tx-1", "tx-2"), port.finished)
    }

    @Test
    fun `network and server failures never finish the transaction`() = runTest {
        gateway.submissions["jws-tx-1"] = SaqzResult.Failure(AppStoreSubmissionError.Data(DataError.Server))
        gateway.submissions["jws-tx-2"] = SaqzResult.Failure(AppStoreSubmissionError.Data(DataError.Connectivity))
        val sync = sync()

        assertEquals(AppStoreDelivery.Deferred(DataError.Server), sync.deliver(transaction("tx-1")))
        assertEquals(AppStoreDelivery.Deferred(DataError.Connectivity), sync.deliver(transaction("tx-2")))
        assertTrue(port.finished.isEmpty())
    }

    @Test
    fun `login drains unfinished transactions`() = runTest {
        port.unfinished = AppStoreTransactionsResult.Loaded(listOf(transaction("tx-1"), transaction("tx-2")))
        val sync = sync()

        sync.onAuthenticated()

        assertEquals(listOf("jws-tx-1", "jws-tx-2"), gateway.submitted)
        assertEquals(listOf("tx-1", "tx-2"), port.finished)
    }

    @Test
    fun `updates are delivered only while signed in`() = runTest {
        val sync = sync()
        sync.start()
        val listener = checkNotNull(port.listener)

        listener.onAppStoreTransactionUpdate(transaction("signed-out"))
        sync.onAuthenticated()
        listener.onAppStoreTransactionUpdate(transaction("signed-in"))
        sync.onSignedOut()
        listener.onAppStoreTransactionUpdate(transaction("after-logout"))

        assertEquals(listOf("jws-signed-in"), gateway.submitted)
        assertEquals(listOf("signed-in"), port.finished)
    }

    @Test
    fun `restore delivers every current entitlement`() = runTest {
        port.restored = AppStoreTransactionsResult.Loaded(listOf(transaction("tx-1"), transaction("tx-2")))
        val sync = sync()

        val outcome = assertIs<AppStoreRestoreOutcome.Restored>(sync.restore())

        assertEquals(2, outcome.deliveries.size)
        assertEquals(listOf("tx-1", "tx-2"), port.finished)
    }

    @Test
    fun `restore failure reaches the screen`() = runTest {
        port.restored = AppStoreTransactionsResult.Failed("sem rede")

        assertEquals(AppStoreRestoreOutcome.Failed, sync().restore())
    }
}
