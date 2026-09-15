package br.com.saqz.composeapp.notifications

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationSessionBindingTest {
    @Test fun registersOnlyAuthenticatedDevicesAndClearsOnAccountSwitchAndLogout() = runTest {
        val session = MutableStateFlow<String?>(null)
        val native = Native()
        val gateway = Gateway()
        val binding = NotificationSessionBinding(session, native, gateway, backgroundScope)
        runCurrent()
        assertEquals(0, gateway.devices.size)
        session.value = "user-a"
        runCurrent()
        assertEquals(listOf("token-1"), gateway.devices.map { it.token })
        binding.refresh(); runCurrent()
        assertEquals(1, gateway.devices.size)
        session.value = "user-b"; runCurrent()
        assertEquals(2, native.clears)
        assertEquals(listOf("token-1", "token-2"), gateway.devices.map { it.token })
        assertEquals(emptyList(), gateway.removed)
        session.value = null; runCurrent()
        assertEquals(3, native.clears)
        assertEquals(0, gateway.removed.size)
    }
    @Test fun refreshRetriesRegistrationFailureAndTokenChangesRegisterAgain() = runTest {
        val native = Native()
        val gateway = Gateway().apply { fail = true }
        val binding = NotificationSessionBinding(MutableStateFlow("user"), native, gateway, backgroundScope)
        runCurrent()
        assertEquals(1, gateway.devices.size)
        gateway.fail = false
        binding.refresh(); runCurrent()
        assertEquals(2, gateway.devices.size)
        native.clears++
        native.changed?.invoke(); runCurrent()
        assertEquals("token-2", gateway.devices.last().token)
    }
    @Test fun failedRevocationBlocksReuseUntilRetryEvenAfterRestart() = runTest {
        val session = MutableStateFlow<String?>("user-a")
        val native = Native()
        val gateway = Gateway()
        val binding = NotificationSessionBinding(session, native, gateway, backgroundScope)
        runCurrent()
        native.clearSuccess = false
        session.value = "user-b"; runCurrent()
        assertEquals(1, gateway.devices.size)
        binding.refresh(); runCurrent()
        assertEquals(1, gateway.devices.size)
        native.clearSuccess = true
        binding.refresh(); runCurrent()
        assertEquals(listOf("token-1", "token-2"), gateway.devices.map { it.token })
        native.clearSuccess = false
        session.value = null; runCurrent()
        val restartedGateway = Gateway()
        val restarted = NotificationSessionBinding(MutableStateFlow("user-c"), native, restartedGateway, backgroundScope)
        runCurrent()
        assertEquals(0, restartedGateway.devices.size)
        native.clearSuccess = true
        restarted.refresh(); runCurrent()
        assertEquals(listOf("token-3"), restartedGateway.devices.map { it.token })
    }
    private class Native : NativeNotificationPort {
        var clears = 0
        var clearSuccess = true
        var changed: (() -> Unit)? = null
        override fun device(done: (NotificationDevice?) -> Unit) = done(NotificationDevice("installation", "token-$clears", "ANDROID"))
        override fun clear(done: (Boolean) -> Unit) { if (clearSuccess) clears++; done(clearSuccess) }
        override fun observe(changed: () -> Unit): NotificationSubscription {
            this.changed = changed
            return NotificationSubscription { this.changed = null }
        }
    }
    private class Gateway : NotificationDeviceGateway {
        var fail = false
        val devices = mutableListOf<NotificationDevice>()
        val removed = mutableListOf<String>()
        override suspend fun register(device: NotificationDevice): SaqzResult<Unit, CommunicationError> {
            devices += device
            return if (fail) SaqzResult.Failure(CommunicationError(DataError.Connectivity)) else SaqzResult.Success(Unit)
        }
        override suspend fun unregister(installationId: String): SaqzResult<Unit, CommunicationError> {
            removed += installationId
            return SaqzResult.Success(Unit)
        }
    }
}
