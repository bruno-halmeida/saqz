package br.com.saqz.subscriptions.presentation.appstore

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.coupon.CouponPlatform
import br.com.saqz.subscriptions.domain.coupon.CouponRejection
import br.com.saqz.subscriptions.domain.coupon.CouponValidation
import br.com.saqz.subscriptions.domain.port.GooglePlayBasePlan
import br.com.saqz.subscriptions.domain.port.GooglePlayOffer
import br.com.saqz.subscriptions.domain.port.GooglePlayProduct
import br.com.saqz.subscriptions.domain.port.GooglePlayProductsResult
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.googleplay.FakeGooglePlayGateway
import br.com.saqz.subscriptions.presentation.googleplay.FakeGooglePlayPort
import br.com.saqz.subscriptions.presentation.googleplay.GooglePlayPaywallStore
import br.com.saqz.subscriptions.presentation.googleplay.GooglePlayPurchaseSync
import br.com.saqz.subscriptions.presentation.googleplay.PLAY_ACCOUNT
import br.com.saqz.subscriptions.presentation.googleplay.PLAY_ORGANIZADOR
import br.com.saqz.subscriptions.presentation.googleplay.playCatalogItem
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Tenho um cupom" na tela de planos, com o Play e com a App Store atrás, contra o contrato fixo do backend. */
@OptIn(ExperimentalCoroutinesApi::class)
class PaywallCouponTest {
    private val coupons = FakeCouponGateway()
    private val events = mutableListOf<Pair<String, Map<String, String>>>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SaqzAnalytics.track = { name, params -> events += name to params }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        SaqzAnalytics.reset()
    }

    // region Google Play
    private val playPort = FakeGooglePlayPort().apply { productsResult = playProducts(withCouponOffer = true) }
    private val playGateway = FakeGooglePlayGateway()

    private fun TestScope.playViewModel(): AppStorePaywallViewModel {
        val catalog = FakeCatalogGateway().apply { plansResult = SaqzResult.Success(listOf(playCatalogItem())) }
        val sync = GooglePlayPurchaseSync(playPort, playGateway, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        return AppStorePaywallViewModel(GooglePlayPaywallStore(playPort, sync, playGateway), catalog, coupons)
    }

    private fun AppStorePaywallViewModel.apply(code: String) {
        onIntent(AppStorePaywallIntent.OpenCoupon)
        onIntent(AppStorePaywallIntent.CouponCodeChanged(code))
        onIntent(AppStorePaywallIntent.ApplyCoupon)
    }

    @Test
    fun `applied coupon shows the play offer price and buys with the offer token`() = runTest {
        val viewModel = playViewModel()

        viewModel.apply("verão-30")

        assertEquals(listOf(CouponRequest("VERO30", Plan.Organizador, SubscriptionCycle.Monthly, CouponPlatform.Android)), coupons.requests)
        val state = viewModel.state.value
        assertNull(state.couponSheet)
        val coupon = checkNotNull(state.couponFor(Plan.Organizador))
        assertEquals("R$ 41,93", coupon.discountedPrice)
        assertEquals(30, coupon.discountPercent)
        assertEquals(3, coupon.durationCycles)

        viewModel.onIntent(AppStorePaywallIntent.PurchaseWithCoupon)

        assertEquals(Triple(PLAY_ORGANIZADOR, "token-cupom-mensal", PLAY_ACCOUNT), playPort.purchases.single())
        assertTrue(viewModel.state.value.isSubscribed)
    }

    @Test
    fun `applied coupon is tracked with platform plan cycle and discount`() = runTest {
        playViewModel().apply("VERAO30")

        assertEquals(
            "coupon_applied" to mapOf("platform" to "android", "plan" to "ORGANIZADOR", "cycle" to "MONTHLY", "discount_percent" to "30"),
            events.single { it.first == "coupon_applied" },
        )
    }

    @Test
    fun `offer missing from the play details says it unlocks in minutes and never buys without the discount`() = runTest {
        playPort.productsResult = playProducts(withCouponOffer = false)
        val viewModel = playViewModel()

        viewModel.apply("VERAO30")

        val state = viewModel.state.value
        assertNull(state.coupon)
        assertEquals(PaywallCouponMessage.Pending, state.couponSheet?.message)
        assertFalse(state.couponSheet!!.isValidating)
        // Releu o produto antes de desistir: a oferta pode ter acabado de sair no Play.
        assertEquals(listOf(listOf(PLAY_ORGANIZADOR), listOf(PLAY_ORGANIZADOR)), playPort.requestedProducts)
        viewModel.onIntent(AppStorePaywallIntent.PurchaseWithCoupon)
        assertTrue(playPort.purchases.isEmpty())
        assertTrue(events.none { it.first == "coupon_applied" })
    }

    @Test
    fun `offer that appears on the second read is used`() = runTest {
        playPort.productsResult = playProducts(withCouponOffer = false)
        val viewModel = playViewModel()
        playPort.productsResult = playProducts(withCouponOffer = true)
        viewModel.apply("VERAO30")
        assertEquals("R$ 41,93", viewModel.state.value.coupon?.discountedPrice)
    }

    @Test
    fun `changing cycle validates the coupon again for the new cycle`() = runTest {
        val viewModel = playViewModel()
        viewModel.apply("VERAO30")
        coupons.result = { SaqzResult.Success(CouponValidation.Rejected(CouponRejection.InvalidCycle)) }

        viewModel.onIntent(AppStorePaywallIntent.SelectCycle(SubscriptionCycle.Annual))

        assertEquals(SubscriptionCycle.Annual, coupons.requests.last().cycle)
        val state = viewModel.state.value
        assertNull(state.coupon)
        assertEquals(PaywallCouponMessage.InvalidCycle, state.couponSheet?.message)
        assertEquals("VERAO30", state.couponSheet?.code)
    }

    @Test
    fun `every rejection becomes a short message on the sheet`() = runTest {
        val expected = mapOf(
            CouponRejection.NotFound to PaywallCouponMessage.NotFound,
            CouponRejection.Expired to PaywallCouponMessage.Expired,
            CouponRejection.InvalidPlan to PaywallCouponMessage.InvalidPlan,
            CouponRejection.InvalidCycle to PaywallCouponMessage.InvalidCycle,
            CouponRejection.NotEligible to PaywallCouponMessage.NotEligible,
            CouponRejection.Exhausted to PaywallCouponMessage.Exhausted,
            CouponRejection.NotAvailableOnPlatform to PaywallCouponMessage.OnlyOnWeb,
            CouponRejection.PendingOnPlatform to PaywallCouponMessage.Pending,
        )
        val viewModel = playViewModel()
        expected.forEach { (rejection, message) ->
            coupons.result = { SaqzResult.Success(CouponValidation.Rejected(rejection)) }
            viewModel.apply("X1")
            assertEquals(message, viewModel.state.value.couponSheet?.message, rejection.name)
            assertNull(viewModel.state.value.coupon)
        }
        coupons.result = { SaqzResult.Failure(DataError.Connectivity) }
        viewModel.apply("X1")
        assertEquals(PaywallCouponMessage.Failed, viewModel.state.value.couponSheet?.message)
    }

    @Test
    fun `typed code keeps only uppercase letters and digits`() {
        assertEquals("VERAO30", normalizeCouponCode(" verao 30!"))
        assertEquals("ABC", normalizeCouponCode("a-b_c"))
    }
    // endregion

    // region App Store
    private val port = FakeAppStorePort()
    private val appStore = FakeAppStoreGateway()
    private val catalog = FakeCatalogGateway()

    private fun TestScope.appStoreViewModel() = AppStorePaywallViewModel(
        port = port,
        sync = AppStoreTransactionSync(port, appStore, CoroutineScope(UnconfinedTestDispatcher(testScheduler))),
        appStore = appStore,
        subscriptions = catalog,
        coupons = coupons,
    )

    @Test
    fun `iphone coupon opens the app store redeem page and reloads the plan on return`() = runTest {
        val viewModel = appStoreViewModel()
        viewModel.apply("VERAO30")

        assertEquals(CouponPlatform.Ios, coupons.requests.single().platform)
        assertEquals(REDEEM_URL, viewModel.state.value.coupon?.redeemUrl)
        assertNull(viewModel.state.value.coupon?.discountedPrice)

        viewModel.onIntent(AppStorePaywallIntent.PurchaseWithCoupon)

        assertEquals(AppStorePaywallEffect.OpenUrl(REDEEM_URL), viewModel.effects.first())
        assertEquals(AppStorePaywallNotice.CouponRedeemOpened, viewModel.state.value.notice)
        assertTrue(viewModel.state.value.awaitingRedeem)
        assertTrue(port.purchases.isEmpty())

        catalog.subscriptionResult = SaqzResult.Success(appStoreSubscription())
        viewModel.onIntent(AppStorePaywallIntent.Resumed)

        assertTrue(viewModel.state.value.isSubscribed)
    }

    @Test
    fun `resume without a redeem in progress does nothing`() = runTest {
        val viewModel = appStoreViewModel()
        catalog.subscriptionResult = SaqzResult.Success(appStoreSubscription())
        viewModel.onIntent(AppStorePaywallIntent.Resumed)
        assertFalse(viewModel.state.value.isSubscribed)
    }

    @Test
    fun `iphone coupon without a redeem url says it unlocks in minutes`() = runTest {
        coupons.result = { SaqzResult.Success(CouponValidation.Applied(coupon(it).copy(appStoreRedeemUrl = null))) }
        val viewModel = appStoreViewModel()
        viewModel.apply("VERAO30")
        assertEquals(PaywallCouponMessage.Pending, viewModel.state.value.couponSheet?.message)
    }
    // endregion
}

private fun playProducts(withCouponOffer: Boolean) = GooglePlayProductsResult.Loaded(
    listOf(
        GooglePlayProduct(
            productId = PLAY_ORGANIZADOR,
            name = "Organizador",
            basePlans = listOf(
                GooglePlayBasePlan("mensal", "offer-mensal", "R$ 59,90"),
                GooglePlayBasePlan("anual", "offer-anual", "R$ 539,90"),
            ),
            offers = if (withCouponOffer) {
                listOf(
                    GooglePlayOffer("mensal", "cupom-verao30", "token-cupom-mensal", "R$ 41,93"),
                    GooglePlayOffer("anual", "cupom-verao30", "token-cupom-anual", "R$ 377,93"),
                )
            } else {
                emptyList()
            },
        ),
    ),
)
