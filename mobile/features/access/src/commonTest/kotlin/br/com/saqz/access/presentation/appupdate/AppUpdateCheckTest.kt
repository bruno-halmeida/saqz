package br.com.saqz.access.presentation.appupdate

import br.com.saqz.access.domain.appupdate.AppUpdateError
import br.com.saqz.access.domain.appupdate.AppUpdateGateway
import br.com.saqz.access.domain.appupdate.AppUpdatePrompt
import br.com.saqz.access.domain.appupdate.AppUpdateVersions
import br.com.saqz.access.domain.port.AppUpdateMemoryPort
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateCheckTest {
    private class FakeMemory(var minimum: String? = null) : AppUpdateMemoryPort {
        val dismissed = mutableSetOf<String>()
        override fun readUpdateMinimum(done: (String?) -> Unit) = done(minimum)
        override fun writeUpdateMinimum(value: String?, done: (Boolean) -> Unit) {
            minimum = value
            done(true)
        }
        override fun isRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit) = done(version in dismissed)
        override fun markRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit) {
            dismissed += version
            done(true)
        }
    }

    /** Cada chamada fica pendurada até o teste responder. */
    private class FakeGateway : AppUpdateGateway {
        val pending = mutableListOf<CompletableDeferred<SaqzResult<AppUpdateVersions, AppUpdateError>>>()
        override suspend fun versions() = CompletableDeferred<SaqzResult<AppUpdateVersions, AppUpdateError>>()
            .also { pending += it }
            .await()

        fun answer(index: Int, minimum: String?, recommended: String?) =
            pending[index].complete(SaqzResult.Success(AppUpdateVersions(minimum, recommended)))

        fun fail(index: Int) = pending[index].complete(SaqzResult.Failure(AppUpdateError.Data(DataError.Connectivity)))
    }

    private val memory = FakeMemory()
    private val gateway = FakeGateway()

    private fun kotlinx.coroutines.test.TestScope.check() =
        AppUpdateCheck(gateway, memory, "1.0.15", CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test fun `the stored minimum decides before the network answers`() = runTest {
        memory.minimum = "1.0.16"
        val check = check()

        check.check()

        assertEquals(AppUpdatePrompt.Required, check.prompt.value)
    }

    @Test fun `the network raising the minimum blocks and is stored`() = runTest {
        val check = check()
        check.check()
        gateway.answer(0, "1.0.16", null)

        assertEquals(AppUpdatePrompt.Required, check.prompt.value)
        assertEquals("1.0.16", memory.minimum)
    }

    @Test fun `the network sending null erases the stored minimum and releases`() = runTest {
        memory.minimum = "1.0.16"
        val check = check()
        check.check()
        gateway.answer(0, null, null)

        assertEquals(AppUpdatePrompt.None, check.prompt.value)
        assertNull(memory.minimum)
    }

    @Test fun `a network failure keeps the stored decision`() = runTest {
        memory.minimum = "1.0.16"
        val check = check()
        check.check()
        gateway.fail(0)

        assertEquals(AppUpdatePrompt.Required, check.prompt.value)
        assertEquals("1.0.16", memory.minimum)
    }

    @Test fun `first opening without network and nothing stored shows nothing`() = runTest {
        val check = check()
        check.check()
        gateway.fail(0)

        assertEquals(AppUpdatePrompt.None, check.prompt.value)
    }

    @Test fun `the answer of an old check is discarded`() = runTest {
        val check = check()
        check.check()
        check.check()
        gateway.answer(1, null, null)
        gateway.answer(0, "1.0.16", null)

        assertEquals(AppUpdatePrompt.None, check.prompt.value)
        assertNull(memory.minimum)
    }

    @Test fun `a dismissed recommended stays closed and a higher one comes back`() = runTest {
        val check = check()
        check.check()
        gateway.answer(0, null, "1.0.16")
        assertEquals(AppUpdatePrompt.Recommended("1.0.16"), check.prompt.value)

        check.dismissRecommended()
        check.check()
        gateway.answer(1, null, "1.0.16")
        assertEquals(AppUpdatePrompt.None, check.prompt.value)

        check.check()
        gateway.answer(2, null, "1.0.17")
        assertEquals(AppUpdatePrompt.Recommended("1.0.17"), check.prompt.value)
    }
}
