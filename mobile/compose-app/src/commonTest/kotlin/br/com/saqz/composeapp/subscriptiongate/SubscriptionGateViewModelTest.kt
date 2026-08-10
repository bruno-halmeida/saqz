package br.com.saqz.composeapp.subscriptiongate

import androidx.lifecycle.viewModelScope
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.subscriptions.domain.subscription.CustomerInfo
import br.com.saqz.subscriptions.domain.subscription.CustomerInfoProvider
import br.com.saqz.subscriptions.domain.purchase.PurchaseInformationError
import br.com.saqz.subscriptions.domain.purchase.PurchaseInformationGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionGateViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening gate verifies immediately and leaves it awaiting authorization`() = runTest {
        val entitlement = FakeEntitlement(false)
        withViewModel(entitlement = entitlement) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()

            assertEquals(SubscriptionGateStatus.NotAuthorized, viewModel.state.value.status)
            assertEquals(1, entitlement.calls)
        }
    }

    @Test
    fun `authorization emits only after the shared entitlement says creation is allowed`() = runTest {
        val entitlement = FakeEntitlement(true)
        withViewModel(entitlement = entitlement) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()

            assertEquals(SubscriptionGateStatus.Authorized, viewModel.state.value.status)
            assertEquals(SubscriptionGateEffect.AuthorizationGranted, viewModel.effects.firstValue())
        }
    }

    @Test
    fun `polling is local 20 seconds and never overlaps an authorization request`() = runTest {
        val entitlement = FakeEntitlement(false).apply { pending = CompletableDeferred() }
        withViewModel(entitlement = entitlement) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()
            advanceTimeBy(SubscriptionGateViewModel.POLL_INTERVAL_MILLIS)
            runCurrent()

            assertEquals(SubscriptionGateStatus.Verifying, viewModel.state.value.status)
            assertEquals(1, entitlement.calls)
            assertEquals(1, entitlement.activeCalls)

            checkNotNull(entitlement.pending).complete(false)
            runCurrent()
            advanceTimeBy(SubscriptionGateViewModel.POLL_INTERVAL_MILLIS)
            runCurrent()

            assertEquals(2, entitlement.calls)
            assertEquals(1, entitlement.maxActiveCalls)
        }
    }

    @Test
    fun `closing or backgrounding the gate stops polling`() = runTest {
        val entitlement = FakeEntitlement(false)
        withViewModel(entitlement = entitlement) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()
            viewModel.onIntent(SubscriptionGateIntent.Closed)
            viewModel.onIntent(SubscriptionGateIntent.ForegroundChanged(false))
            advanceTimeBy(SubscriptionGateViewModel.POLL_INTERVAL_MILLIS * 2)
            runCurrent()

            assertEquals(1, entitlement.calls)
        }
    }

    @Test
    fun `returning to foreground refreshes visible gate immediately`() = runTest {
        val entitlement = FakeEntitlement(false)
        withViewModel(entitlement = entitlement) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()
            viewModel.onIntent(SubscriptionGateIntent.ForegroundChanged(false))
            entitlement.allowed = true
            viewModel.onIntent(SubscriptionGateIntent.ForegroundChanged(true))
            runCurrent()

            assertEquals(SubscriptionGateStatus.Authorized, viewModel.state.value.status)
            assertEquals(2, entitlement.calls)
        }
    }

    @Test
    fun `manual refresh is immediate and does not overlap the automatic check`() = runTest {
        val entitlement = FakeEntitlement(false)
        withViewModel(entitlement = entitlement) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()
            entitlement.allowed = true
            viewModel.onIntent(SubscriptionGateIntent.RefreshAuthorization)
            runCurrent()

            assertEquals(2, entitlement.calls)
            assertEquals(SubscriptionGateStatus.Authorized, viewModel.state.value.status)
        }
    }

    @Test
    fun `purchase confirmation is shown only on real success and masks authoritative email`() = runTest {
        val purchase = FakePurchaseInformationGateway(SaqzResult.Success(Unit))
        withViewModel(
            entitlement = FakeEntitlement(false),
            purchase = purchase,
            customer = FakeCustomerInfoProvider(CustomerInfo("Ana", "ana.silva@example.com")),
        ) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()
            viewModel.onIntent(SubscriptionGateIntent.RequestPurchaseInformation)
            runCurrent()

            assertEquals(SubscriptionGateStatus.Sent, viewModel.state.value.status)
            assertEquals("a***a@example.com", viewModel.state.value.maskedEmail)
            assertEquals(1, purchase.calls)
            assertNotEquals("ana.silva@example.com", viewModel.state.value.maskedEmail)
        }
    }

    @Test
    fun `purchase request exposes sending state until the server confirms delivery`() = runTest {
        val purchase = FakePurchaseInformationGateway(SaqzResult.Success(Unit)).apply {
            pending = CompletableDeferred()
        }
        withViewModel(entitlement = FakeEntitlement(false), purchase = purchase) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()
            viewModel.onIntent(SubscriptionGateIntent.RequestPurchaseInformation)
            runCurrent()

            assertEquals(SubscriptionGateStatus.Sending, viewModel.state.value.status)
            checkNotNull(purchase.pending).complete(SaqzResult.Success(Unit))
            runCurrent()
            assertEquals(SubscriptionGateStatus.Sent, viewModel.state.value.status)
        }
    }

    @Test
    fun `purchase failure is recoverable and never reports sent`() = runTest {
        val purchase = FakePurchaseInformationGateway(
            SaqzResult.Failure(PurchaseInformationError.Data(br.com.saqz.domain.DataError.Connectivity)),
        )
        withViewModel(entitlement = FakeEntitlement(false), purchase = purchase) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()
            viewModel.onIntent(SubscriptionGateIntent.RequestPurchaseInformation)
            runCurrent()

            assertEquals(SubscriptionGateStatus.Failed, viewModel.state.value.status)
            assertTrue(viewModel.state.value.failure is SubscriptionGateFailure.PurchaseInformation)
            assertFalse(viewModel.state.value.status == SubscriptionGateStatus.Sent)
        }
    }

    @Test
    fun `purchase exception is recoverable and never reports sent`() = runTest {
        val purchase = FakePurchaseInformationGateway(SaqzResult.Success(Unit)).apply {
            exception = IllegalStateException("temporary transport failure")
        }
        withViewModel(entitlement = FakeEntitlement(false), purchase = purchase) { viewModel ->
            viewModel.onIntent(SubscriptionGateIntent.Opened)
            runCurrent()
            viewModel.onIntent(SubscriptionGateIntent.RequestPurchaseInformation)
            runCurrent()

            assertEquals(SubscriptionGateStatus.Failed, viewModel.state.value.status)
            assertEquals(SubscriptionGateFailure.PurchaseInformation, viewModel.state.value.failure)
            assertFalse(viewModel.state.value.status == SubscriptionGateStatus.Sent)
        }
    }

    private fun viewModel(
        entitlement: FakeEntitlement,
        purchase: PurchaseInformationGateway = FakePurchaseInformationGateway(SaqzResult.Success(Unit)),
        customer: CustomerInfoProvider = FakeCustomerInfoProvider(null),
    ) = SubscriptionGateViewModel(entitlement, purchase, customer)

    private suspend fun TestScope.withViewModel(
        entitlement: FakeEntitlement,
        purchase: PurchaseInformationGateway = FakePurchaseInformationGateway(SaqzResult.Success(Unit)),
        customer: CustomerInfoProvider = FakeCustomerInfoProvider(null),
        block: suspend (SubscriptionGateViewModel) -> Unit,
    ) {
        val viewModel = viewModel(entitlement, purchase, customer)
        try {
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private class FakeEntitlement(var allowed: Boolean) : GroupCreationEntitlement {
        var calls = 0
        var activeCalls = 0
        var maxActiveCalls = 0
        var pending: CompletableDeferred<Boolean>? = null

        override suspend fun canCreateGroup(): Boolean {
            calls++
            activeCalls++
            maxActiveCalls = maxOf(maxActiveCalls, activeCalls)
            return try {
                pending?.await() ?: allowed
            } finally {
                activeCalls--
            }
        }
    }

    private class FakePurchaseInformationGateway(
        private val result: SaqzResult<Unit, PurchaseInformationError>,
    ) : PurchaseInformationGateway {
        var calls = 0
        var pending: CompletableDeferred<SaqzResult<Unit, PurchaseInformationError>>? = null
        var exception: Exception? = null

        override suspend fun request(): SaqzResult<Unit, PurchaseInformationError> {
            calls++
            exception?.let { throw it }
            return pending?.await() ?: result
        }
    }

    private class FakeCustomerInfoProvider(
        private val info: CustomerInfo?,
    ) : CustomerInfoProvider {
        override suspend fun current(): CustomerInfo? = info
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()
}
