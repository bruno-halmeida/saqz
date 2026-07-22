package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.composeapp.di.stopSaqzKoin
import br.com.saqz.composeapp.di.startSaqzKoin
import br.com.saqz.composeapp.testSaqzAppDependencies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatformTools

class AccessOrchestratorTest {
    @Test
    fun `start observes auth and reconciles signed out state into login`() {
        stopSaqzKoin()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            startSaqzKoin(testSaqzAppDependencies())
            val orchestrator = KoinPlatformTools.defaultContext().get()
                .get<AccessOrchestrator> { parametersOf(scope) }

            assertFalse(orchestrator.state.value.authObserved)

            orchestrator.onIntent(AccessRuntimeIntent.Start)

            assertTrue(orchestrator.state.value.authObserved)
            assertEquals(AuthScreen.LOGIN, orchestrator.state.value.authentication.screen)
        } finally {
            scope.cancel()
            stopSaqzKoin()
        }
    }
}
