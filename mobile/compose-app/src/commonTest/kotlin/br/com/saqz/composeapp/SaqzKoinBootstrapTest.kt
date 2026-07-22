package br.com.saqz.composeapp

import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.composeapp.di.startSaqzKoin
import br.com.saqz.composeapp.di.stopSaqzKoin
import br.com.saqz.composeapp.di.loadSaqzPlatformDependencies
import br.com.saqz.composeapp.navigation.AccessOrchestrator
import br.com.saqz.composeapp.navigation.GroupsNavigationViewModel
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.network.AuthenticatedNetworkClient
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatformTools

class SaqzKoinBootstrapTest {
    @Test
    fun bootstrapRegistersThePlatformDependencyGraph() {
        stopSaqzKoin()
        val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            startSaqzKoin(testSaqzAppDependencies())

            val koin = KoinPlatformTools.defaultContext().get()
            assertNotNull(koin.get<AuthenticatedNetworkClient>())
            assertNotNull(koin.get<SessionAccessStateMachine>())
            assertNotNull(koin.get<GroupsNavigationViewModel>())
            val orchestrator = koin.get<AccessOrchestrator> { parametersOf(runtimeScope) }
            assertSame(koin.get<GroupProfileGateway>(), orchestrator.groupProfileGateway)
            assertSame(koin.get<GroupPhotoGateway>(), orchestrator.groupPhotoGateway)
        } finally {
            runtimeScope.cancel()
            stopSaqzKoin()
        }
    }

    @Test
    fun reloadingPlatformBindingsRecreatesTheNetworkSingleton() {
        stopSaqzKoin()
        try {
            startSaqzKoin(testSaqzAppDependencies())
            val koin = KoinPlatformTools.defaultContext().get()
            val first = koin.get<AuthenticatedNetworkClient>()

            loadSaqzPlatformDependencies(testSaqzAppDependencies())

            assertNotSame(first, koin.get<AuthenticatedNetworkClient>())
        } finally {
            stopSaqzKoin()
        }
    }
}
