package br.com.saqz.subscriptions.presentation.ui.countdown

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.trial.TrialUsage
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TrialCountdownSheetTest {
    private val usage = TrialUsage(games = 3, confirmedAthletes = 12, cashCents = 45_000)

    @Test
    fun showsTheCountdownTheUsageAndTheCallToSubscribe() = runComposeUiTest {
        setContent { SaqzTheme { TrialCountdownSheet(3, usage, {}, {}) } }

        onNodeWithText("Faltam 3 dias do seu teste").assertExists()
        onNodeWithText("Assine para continuar marcando jogos.").assertExists()
        onNodeWithText("3 jogos").assertExists()
        onNodeWithText("12 atletas confirmados").assertExists()
        onNodeWithText("R$ 450,00 no caixa").assertExists()
    }

    @Test
    fun singularAndLastDayTitles() = runComposeUiTest {
        var days = androidx.compose.runtime.mutableIntStateOf(1)
        setContent { SaqzTheme { TrialCountdownSheet(days.intValue, null, {}, {}) } }
        onNodeWithText("Falta 1 dia do seu teste").assertExists()
        days.intValue = 0
        waitForIdle()
        onNodeWithText("Hoje é o último dia do seu teste").assertExists()
    }

    @Test
    fun withoutAnyUsageOnlyTheCountdownShows() = runComposeUiTest {
        setContent { SaqzTheme { TrialCountdownSheet(2, TrialUsage(0, 0, 0), {}, {}) } }
        onNodeWithTag(TrialCountdownTags.Usage).assertDoesNotExist()
    }

    @Test
    fun viewPlansClosesAndOpensThePlans() = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent { SaqzTheme { TrialCountdownSheet(2, usage, { events += "dismiss" }, { events += "plans" }) } }

        onNodeWithTag(TrialCountdownTags.ViewPlans).performClick()

        assertEquals(listOf("dismiss", "plans"), events)
    }

    @Test
    fun notNowOnlyCloses() = runComposeUiTest {
        val events = mutableListOf<String>()
        setContent { SaqzTheme { TrialCountdownSheet(2, usage, { events += "dismiss" }, { events += "plans" }) } }

        onNodeWithTag(TrialCountdownTags.NotNow).performClick()

        assertEquals(listOf("dismiss"), events)
    }
}
