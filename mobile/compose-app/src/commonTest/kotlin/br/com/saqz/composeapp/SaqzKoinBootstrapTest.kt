package br.com.saqz.composeapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.BeforeTest
import kotlin.test.AfterTest

import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.composeapp.di.startSaqzKoin
import br.com.saqz.composeapp.di.stopSaqzKoin
import br.com.saqz.composeapp.di.loadSaqzPlatformDependencies
import br.com.saqz.composeapp.navigation.AccessRuntimeContract
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateViewModel
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import org.koin.mp.KoinPlatformTools

class SaqzKoinBootstrapTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun bootstrapRegistersThePlatformDependencyGraph() {
        stopSaqzKoin()
        try {
            val dependencies = testSaqzPlatformDependencies()
            startSaqzKoin(dependencies)

            val koin = KoinPlatformTools.defaultContext().get()
            assertNotNull(koin.get<AuthenticatedNetworkClient>())
            assertNotNull(koin.get<SessionAccessStateMachine>())
            // C1: the entry point's whole graph — the session gate over the orchestrator.
            assertNotNull(koin.get<AccessRuntimeContract>())
            assertNotNull(koin.get<AccessViewModel>())
            assertNotNull(koin.get<SubscriptionGateViewModel>())
            assertNotNull(koin.get<ProfileGateway>())
            assertNotNull(koin.get<ProfilePhotoSelectionPort>())
            kotlin.test.assertSame(dependencies.financialDocuments, koin.get<br.com.saqz.receivables.domain.port.ReceiptDocumentPicker>())
            assertNotNull(koin.get<OwnProfileViewModel>())
            assertNotNull(koin.get<br.com.saqz.receivables.presentation.ReceiptFinanceHomeViewModel>())
            assertNotNull(koin.get<br.com.saqz.receivables.presentation.ReceiptWalletViewModel> {
                org.koin.core.parameter.parametersOf(androidx.lifecycle.SavedStateHandle())
            })
            assertNotNull(koin.get<br.com.saqz.receivables.presentation.FinancialManagementViewModel> {
                org.koin.core.parameter.parametersOf(androidx.lifecycle.SavedStateHandle())
            })
            assertNotNull(koin.get<br.com.saqz.receivables.presentation.MemberPaymentViewModel> {
                org.koin.core.parameter.parametersOf("order", androidx.lifecycle.SavedStateHandle())
            })
            assertNotNull(koin.get<br.com.saqz.receivables.presentation.RecurrenceViewModel> {
                org.koin.core.parameter.parametersOf("account", "group", androidx.lifecycle.SavedStateHandle())
            })
        } finally {
            stopSaqzKoin()
        }
    }

    @Test
    fun appStorePortRegistersThePaywallAndTheTransactionSync() {
        stopSaqzKoin()
        try {
            val port = TestAppStorePort()
            startSaqzKoin(testSaqzPlatformDependencies(appStorePurchases = port))
            val koin = KoinPlatformTools.defaultContext().get()

            val checkout = koin.get<br.com.saqz.composeapp.subscriptiongate.StoreCheckout>()
            kotlin.test.assertTrue(checkout.purchasesAvailable)
            kotlin.test.assertSame(checkout.sync, koin.get<br.com.saqz.subscriptions.presentation.appstore.AppStoreTransactionSync>())
            assertNotNull(koin.get<br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallViewModel>())
            // O listener de `Transaction.updates` é registrado já na carga da plataforma.
            kotlin.test.assertTrue(port.listening)
        } finally {
            stopSaqzKoin()
        }
    }

    @Test
    fun googlePlayPortRegistersThePaywallAndThePurchaseSync() {
        stopSaqzKoin()
        try {
            val port = TestGooglePlayPort()
            startSaqzKoin(testSaqzPlatformDependencies(googlePlayPurchases = port))
            val koin = KoinPlatformTools.defaultContext().get()

            val checkout = koin.get<br.com.saqz.composeapp.subscriptiongate.StoreCheckout>()
            kotlin.test.assertTrue(checkout.purchasesAvailable)
            kotlin.test.assertEquals(br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider.GooglePlay, checkout.deviceStore)
            kotlin.test.assertSame(checkout.sync, koin.get<br.com.saqz.subscriptions.presentation.googleplay.GooglePlayPurchaseSync>())
            assertNotNull(koin.get<br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallViewModel>())
            // O listener do Play é registrado já na carga da plataforma.
            kotlin.test.assertTrue(port.listening)
        } finally {
            stopSaqzKoin()
        }
    }

    @Test
    fun withoutAnAppStorePortNothingIsSoldInTheApp() {
        stopSaqzKoin()
        try {
            startSaqzKoin(testSaqzPlatformDependencies())
            val checkout = KoinPlatformTools.defaultContext().get().get<br.com.saqz.composeapp.subscriptiongate.StoreCheckout>()

            kotlin.test.assertFalse(checkout.purchasesAvailable)
            kotlin.test.assertFalse(checkout.canManageSubscriptions)
            kotlin.test.assertNull(checkout.sync)
        } finally {
            stopSaqzKoin()
        }
    }

    @Test
    fun reloadingPlatformBindingsRecreatesTheNetworkSingleton() {
        stopSaqzKoin()
        try {
            startSaqzKoin(testSaqzPlatformDependencies())
            val koin = KoinPlatformTools.defaultContext().get()
            val first = koin.get<AuthenticatedNetworkClient>()

            loadSaqzPlatformDependencies(testSaqzPlatformDependencies())

            assertNotSame(first, koin.get<AuthenticatedNetworkClient>())
        } finally {
            stopSaqzKoin()
        }
    }
}

private class TestAppStorePort : br.com.saqz.subscriptions.domain.port.AppStorePurchasesPort {
    var listening = false

    override fun canMakeAppStorePayments() = true

    override fun loadAppStoreProducts(
        productIds: List<String>,
        done: br.com.saqz.subscriptions.domain.port.AppStoreProductsCallback,
    ) = done.onAppStoreProducts(br.com.saqz.subscriptions.domain.port.AppStoreProductsResult.Loaded(emptyList()))

    override fun purchaseAppStoreProduct(
        productId: String,
        appAccountToken: String,
        done: br.com.saqz.subscriptions.domain.port.AppStorePurchaseCallback,
    ) = done.onAppStorePurchase(br.com.saqz.subscriptions.domain.port.AppStorePurchaseResult.Cancelled)

    override fun finishAppStoreTransaction(transactionId: String) = Unit

    override fun readUnfinishedAppStoreTransactions(done: br.com.saqz.subscriptions.domain.port.AppStoreTransactionsCallback) =
        done.onAppStoreTransactions(br.com.saqz.subscriptions.domain.port.AppStoreTransactionsResult.Loaded(emptyList()))

    override fun restoreAppStorePurchases(done: br.com.saqz.subscriptions.domain.port.AppStoreTransactionsCallback) =
        done.onAppStoreTransactions(br.com.saqz.subscriptions.domain.port.AppStoreTransactionsResult.Loaded(emptyList()))

    override fun listenForAppStoreTransactions(listener: br.com.saqz.subscriptions.domain.port.AppStoreTransactionListener) {
        listening = true
    }

    override fun showAppStoreSubscriptionManagement(done: br.com.saqz.subscriptions.domain.port.AppStoreManagementCallback) =
        done.onAppStoreManagementClosed()
}

private class TestGooglePlayPort : br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesPort {
    var listening = false

    override fun canMakeGooglePlayPayments() = true

    override fun loadGooglePlayProducts(
        productIds: List<String>,
        done: br.com.saqz.subscriptions.domain.port.GooglePlayProductsCallback,
    ) = done.onGooglePlayProducts(br.com.saqz.subscriptions.domain.port.GooglePlayProductsResult.Loaded(emptyList()))

    override fun purchaseGooglePlayProduct(
        productId: String,
        offerToken: String,
        obfuscatedAccountId: String,
        done: br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseCallback,
    ) = done.onGooglePlayPurchase(br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseResult.Cancelled)

    override fun readGooglePlaySubscriptionPurchases(done: br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesCallback) =
        done.onGooglePlayPurchases(br.com.saqz.subscriptions.domain.port.GooglePlayPurchasesResult.Loaded(emptyList()))

    override fun listenForGooglePlayPurchases(listener: br.com.saqz.subscriptions.domain.port.GooglePlayPurchaseListener) {
        listening = true
    }

    override fun showGooglePlaySubscriptionManagement(productId: String?) = Unit
}
