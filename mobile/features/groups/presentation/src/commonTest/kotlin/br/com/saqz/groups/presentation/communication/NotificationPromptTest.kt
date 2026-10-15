package br.com.saqz.groups.presentation.communication

import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.communication.NotificationDevice
import br.com.saqz.groups.domain.communication.NotificationPermission
import br.com.saqz.groups.domain.communication.NotificationPromptMemory
import br.com.saqz.groups.domain.communication.NotificationSubscription
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val Day = 24L * 60 * 60 * 1000

private class FakeNotificationPort(
    var state: NotificationPermission = NotificationPermission.NotAsked,
    var memory: NotificationPromptMemory = NotificationPromptMemory(),
    private val answer: NotificationPermission = NotificationPermission.Granted,
) : NativeNotificationPort {
    var systemRequests = 0
    var settingsOpened = 0
    override fun device(done: (NotificationDevice?) -> Unit) = done(null)
    override fun clear(done: (Boolean) -> Unit) = done(true)
    override fun dismissAll() = Unit
    override fun observe(changed: () -> Unit) = NotificationSubscription { }
    override fun permission(done: (NotificationPermission) -> Unit) = done(state)
    override fun requestPermission(done: (NotificationPermission) -> Unit) {
        systemRequests++
        state = answer
        done(answer)
    }
    override fun openSettings() { settingsOpened++ }
    override fun promptMemory(done: (NotificationPromptMemory) -> Unit) = done(memory)
    override fun savePromptMemory(memory: NotificationPromptMemory) { this.memory = memory }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPromptTest {
    private var clock = 10 * Day
    private var granted = 0

    private fun TestScope.prompt(port: FakeNotificationPort) =
        NotificationPrompt(port, this, { clock }, { granted++ })

    @Test
    fun `the sheet opens for the audience when the permission was never asked`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakeNotificationPort()
        val prompt = prompt(port)
        prompt.offer(NotificationPromptAudience.Organizer)
        assertEquals(NotificationPromptAudience.Organizer, prompt.audience.value)
        assertEquals(0, port.systemRequests)
    }

    @Test
    fun `whoever already allowed never sees the sheet`() = runTest(UnconfinedTestDispatcher()) {
        val prompt = prompt(FakeNotificationPort(state = NotificationPermission.Granted))
        prompt.offer(NotificationPromptAudience.Athlete)
        assertNull(prompt.audience.value)
    }

    @Test
    fun `whoever refused in the system or has no push never sees the sheet`() = runTest(UnconfinedTestDispatcher()) {
        for (state in listOf(NotificationPermission.Denied, NotificationPermission.Unsupported)) {
            val prompt = prompt(FakeNotificationPort(state = state))
            prompt.offer(NotificationPromptAudience.Athlete)
            assertNull(prompt.audience.value)
            assertEquals(state, prompt.permission.value)
        }
    }

    @Test
    fun `enable closes the sheet then asks the system once and wires the device when granted`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakeNotificationPort()
        val prompt = prompt(port)
        prompt.offer(NotificationPromptAudience.Athlete)
        prompt.enable()
        assertNull(prompt.audience.value)
        assertEquals(1, port.systemRequests)
        assertEquals(NotificationPermission.Granted, prompt.permission.value)
        assertEquals(1, granted)
        assertEquals(NotificationPromptMemory(), port.memory)
    }

    @Test
    fun `a system refusal leaves the settings path and does not wire the device`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakeNotificationPort(answer = NotificationPermission.Denied)
        val prompt = prompt(port)
        prompt.offer(NotificationPromptAudience.Athlete)
        prompt.enable()
        assertEquals(NotificationPermission.Denied, prompt.permission.value)
        assertEquals(0, granted)
        prompt.openSettings()
        assertEquals(1, port.settingsOpened)
    }

    @Test
    fun `not now stores the choice and the system is not asked`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakeNotificationPort()
        val prompt = prompt(port)
        prompt.offer(NotificationPromptAudience.Athlete)
        prompt.dismiss()
        assertNull(prompt.audience.value)
        assertEquals(NotificationPromptMemory(1, clock), port.memory)
        assertEquals(0, port.systemRequests)
    }

    @Test
    fun `after not now the sheet waits three days`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakeNotificationPort(memory = NotificationPromptMemory(1, clock))
        val prompt = prompt(port)
        clock += 3 * Day - 1
        prompt.offer(NotificationPromptAudience.Athlete)
        assertNull(prompt.audience.value)
        clock += 1
        prompt.offer(NotificationPromptAudience.Athlete)
        assertEquals(NotificationPromptAudience.Athlete, prompt.audience.value)
    }

    @Test
    fun `the sheet returns at most two more times`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakeNotificationPort()
        val prompt = prompt(port)
        var shown = 0
        repeat(6) {
            clock += 3 * Day
            prompt.offer(NotificationPromptAudience.Athlete)
            if (prompt.audience.value != null) { shown++; prompt.dismiss() }
        }
        assertEquals(3, shown)
        assertEquals(3, port.memory.declined)
    }

    @Test
    fun `refreshing finds a permission turned on in the settings and wires the device`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakeNotificationPort(state = NotificationPermission.Denied)
        val prompt = prompt(port)
        prompt.refreshPermission()
        assertEquals(NotificationPermission.Denied, prompt.permission.value)
        port.state = NotificationPermission.Granted
        prompt.refreshPermission()
        assertEquals(NotificationPermission.Granted, prompt.permission.value)
        assertEquals(1, granted)
    }
}
