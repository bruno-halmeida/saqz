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
