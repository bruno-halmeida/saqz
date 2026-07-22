package br.com.saqz.composeapp

import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.composeapp.di.startSaqzKoin
import br.com.saqz.composeapp.di.stopSaqzKoin
import br.com.saqz.composeapp.navigation.GroupsNavigationViewModel
import br.com.saqz.network.AuthenticatedNetworkClient
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.mp.KoinPlatformTools

class SaqzKoinBootstrapTest {
    @Test
    fun bootstrapRegistersThePlatformDependencyGraph() {
        stopSaqzKoin()
        try {
            startSaqzKoin(testSaqzAppDependencies())

            val koin = KoinPlatformTools.defaultContext().get()
            assertNotNull(koin.get<AuthenticatedNetworkClient>())
            assertNotNull(koin.get<SessionAccessStateMachine>())
            assertNotNull(koin.get<GroupsNavigationViewModel>())
        } finally {
            stopSaqzKoin()
        }
    }
}
