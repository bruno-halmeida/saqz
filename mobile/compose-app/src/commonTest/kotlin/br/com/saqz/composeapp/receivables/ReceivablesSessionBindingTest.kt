package br.com.saqz.composeapp.receivables

import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.ReceivablesAvailability
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import br.com.saqz.receivables.presentation.ReceivablesCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReceivablesSessionBindingTest {
    @Test
    fun loginAccountSwitchLogoutAndResumeDriveAvailability() = runTest {
        val session = MutableStateFlow<String?>(null)
        val gateway = FakeGateway()
        val coordinator = ReceivablesCoordinator(gateway, backgroundScope, ReceivablesSessionContext {
            session.value
        })
        val binding = ReceivablesSessionBinding(session, coordinator, backgroundScope)
        binding.onResume()
        runCurrent()
        assertEquals(0, gateway.calls)
        session.value = "1:user-a"
        runCurrent()
        assertEquals(1, gateway.calls)
        assertTrue(coordinator.state.value.discoveryAvailable)
        binding.onPause()
        assertFalse(coordinator.state.value.discoveryAvailable)
        binding.onResume()
        runCurrent()
        assertEquals(2, gateway.calls)
        session.value = "2:user-b"
        runCurrent()
        assertEquals(3, gateway.calls)
        session.value = null
        runCurrent()
        assertFalse(coordinator.state.value.discoveryAvailable)
        assertFalse(coordinator.state.value.maintenanceAvailable)
        binding.onResume()
        runCurrent()
        assertEquals(3, gateway.calls)
    }

    private class FakeGateway : ReceivablesAvailabilityGateway {
        var calls = 0
        override suspend fun get(): SaqzResult.Success<ReceivablesAvailability> {
            calls++
            return SaqzResult.Success(ReceivablesAvailability(true, true))
        }
    }
}
