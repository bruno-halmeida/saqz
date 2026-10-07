package br.com.saqz.subscriptions.presentation.googleplay

import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseResult
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallEffect
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallIntent
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallNotice
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallPhase
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallViewModel
import br.com.saqz.subscriptions.presentation.appstore.FakeCatalogGateway
import br.com.saqz.subscriptions.presentation.appstore.appStoreSubscription
import br.com.saqz.subscriptions.presentation.store.PaywallStoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A mesma tela de compra, com o Play atrás: preço do Play, compra presa à conta, backend decide. */
@OptIn(ExperimentalCoroutinesApi::class)
class GooglePlayPaywallTest {
    private val port = FakeGooglePlayPort()
    private val gateway = FakeGooglePlayGateway()
    private val catalog = FakeCatalogGateway().apply { plansResult = SaqzResult.Success(listOf(playCatalogItem())) }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): AppStorePaywallViewModel {
        val sync = GooglePlayPurchaseSync(port, gateway, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        return AppStorePaywallViewModel(GooglePlayPaywallStore(port, sync, gateway), catalog)
    }

    @Test
    fun `ready shows the play price of each base plan`() = runTest {
        val state = viewModel().state.value

        assertEquals(PaywallStoreKind.GooglePlay, state.store)
        assertEquals(AppStorePaywallPhase.Ready, state.phase)
        val plan = state.plans.single()
        assertEquals("R$ 59,90", plan.offer(SubscriptionCycle.Monthly)?.displayPrice)
        assertEquals("R$ 539,90", plan.offer(SubscriptionCycle.Annual)?.displayPrice)
        assertEquals(listOf(listOf(PLAY_ORGANIZADOR)), port.requestedProducts)
    }

    @Test
    fun `purchase uses the base plan offer and the account id and confirms with the backend`() = runTest {
        val viewModel = viewModel()
        val offer = checkNotNull(viewModel.state.value.plans.single().offer(SubscriptionCycle.Annual))

        viewModel.onIntent(AppStorePaywallIntent.Purchase(offer.productId))

        assertEquals(listOf(Triple(PLAY_ORGANIZADOR, "offer-anual", PLAY_ACCOUNT)), port.purchases)
        assertEquals(listOf(PLAY_ORGANIZADOR to "token-1"), gateway.submitted)
        assertTrue(viewModel.state.value.isSubscribed)
        assertEquals(AppStorePaywallEffect.Subscribed, viewModel.effects.first())
    }

    @Test
    fun `a pending payment waits without going to the backend`() = runTest {
        port.purchaseResult = GooglePlayPurchaseResult.Purchased(playPurchase("token-1", pending = true))
        val viewModel = viewModel()
        val offer = checkNotNull(viewModel.state.value.plans.single().offer(SubscriptionCycle.Monthly))

        viewModel.onIntent(AppStorePaywallIntent.Purchase(offer.productId))

        assertEquals(AppStorePaywallNotice.Pending, viewModel.state.value.notice)
        assertTrue(gateway.submitted.isEmpty())
    }

    @Test
    fun `a play subscription of another saqz account says so instead of a generic failure`() = runTest {
        port.purchaseResult = GooglePlayPurchaseResult.OwnedByAnotherAccount
        val viewModel = viewModel()
        val offer = checkNotNull(viewModel.state.value.plans.single().offer(SubscriptionCycle.Monthly))

        viewModel.onIntent(AppStorePaywallIntent.Purchase(offer.productId))

        assertEquals(AppStorePaywallNotice.OwnedByAnotherAccount, viewModel.state.value.notice)
        assertTrue(gateway.submitted.isEmpty())
    }

    @Test
    fun `an app store subscriber is not offered a second subscription on android`() = runTest {
        catalog.subscriptionResult = SaqzResult.Success(appStoreSubscription())

        assertEquals(AppStorePaywallPhase.Unavailable, viewModel().state.value.phase)
    }

    @Test
    fun `current play plan is marked`() = runTest {
        catalog.subscriptionResult = SaqzResult.Success(playSubscription())

        val plan = viewModel().state.value.plans.single()

        assertTrue(checkNotNull(plan.offer(SubscriptionCycle.Monthly)).isCurrent)
        assertEquals(SubscriptionProvider.GooglePlay, playSubscription().provider)
    }
}
