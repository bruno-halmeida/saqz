package br.com.saqz.composeapp.analytics

import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.composeapp.navigation.SaqzShellDestination
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.analytics.analyticsName
import br.com.saqz.core.common.mvi.MviViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private sealed interface ProbeIntent {
    data object Tap : ProbeIntent
    data class NameChanged(val value: String) : ProbeIntent
}

private class ProbeViewModel : MviViewModel<Int, ProbeIntent, Unit>(0) {
    override fun handleIntent(intent: ProbeIntent) = update { it + 1 }
}

class SaqzAnalyticsTest {
    private val recorded = mutableListOf<Pair<String, Map<String, String>>>()

    @BeforeTest
    fun install() {
        SaqzAnalytics.track = { name, params -> recorded += name to params }
    }

    @AfterTest
    fun reset() = SaqzAnalytics.reset()

    @Test
    fun `analyticsName keeps only the class segments`() {
        assertEquals("AccessRoute.Login", analyticsName(AccessRoute.Login))
        assertEquals("SaqzShellDestination", analyticsName(SaqzShellDestination.Home))
        assertEquals("ProbeIntent.Tap", analyticsName(ProbeIntent.Tap))
        assertEquals("null", analyticsName(null))
    }

    @Test
    fun `onIntent reports ui_action and then handles the intent`() {
        val viewModel = ProbeViewModel()

        viewModel.onIntent(ProbeIntent.Tap)

        assertEquals(1, viewModel.state.value)
        assertEquals(listOf("ui_action" to mapOf("screen" to "Probe", "action" to "ProbeIntent.Tap")), recorded)
    }

    @Test
    fun `typing intents are handled but not reported`() {
        val viewModel = ProbeViewModel()

        viewModel.onIntent(ProbeIntent.NameChanged("b"))

        assertEquals(1, viewModel.state.value)
        assertEquals(emptyList(), recorded)
    }

    @Test
    fun `screen event carries the screen name`() {
        SaqzAnalytics.screen("Shell.inicio")

        assertEquals(listOf("screen_view" to mapOf("screen_name" to "Shell.inicio")), recorded)
    }

    @Test
    fun `log is forwarded to the installed sink and reset removes it`() {
        val lines = mutableListOf<String>()
        SaqzAnalytics.log = lines::add

        SaqzAnalytics.log("response GET probe status=503")
        SaqzAnalytics.reset()
        SaqzAnalytics.log("ignored")

        assertEquals(listOf("response GET probe status=503"), lines)
    }

    @Test
    fun `curated event and user property reach the sink`() {
        val properties = mutableListOf<Pair<String, String?>>()
        SaqzAnalytics.setProperty = { name, value -> properties += name to value }

        SaqzAnalytics.event("sign_up", "method" to "password")
        SaqzAnalytics.userProperty("plan_state", "active")

        assertEquals(listOf("sign_up" to mapOf("method" to "password")), recorded)
        assertEquals(listOf<Pair<String, String?>>("plan_state" to "active"), properties)
    }
}
