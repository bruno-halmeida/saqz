package br.com.saqz.composeapp

import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.composeapp.di.startSaqzKoin
import br.com.saqz.composeapp.di.stopSaqzKoin
import br.com.saqz.composeapp.di.loadSaqzPlatformDependencies
import br.com.saqz.composeapp.navigation.AccessRuntimeContract
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import org.koin.mp.KoinPlatformTools

class SaqzKoinBootstrapTest {
    @Test
    fun bootstrapRegistersThePlatformDependencyGraph() {
        stopSaqzKoin()
        try {
            startSaqzKoin(testSaqzPlatformDependencies())

            val koin = KoinPlatformTools.defaultContext().get()
            assertNotNull(koin.get<AuthenticatedNetworkClient>())
            assertNotNull(koin.get<SessionAccessStateMachine>())
            // C1: the entry point's whole graph — the session gate over the orchestrator.
            assertNotNull(koin.get<AccessRuntimeContract>())
            assertNotNull(koin.get<AccessViewModel>())
            assertNotNull(koin.get<ProfileGateway>())
            assertNotNull(koin.get<ProfilePhotoSelectionPort>())
            assertNotNull(koin.get<OwnProfileViewModel>())
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
