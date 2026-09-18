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
}
