package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import br.com.saqz.subscriptions.domain.trial.TrialUsage
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanTrialUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MyPlanTrialScreenTest {
    @Test
    fun expiredTrialShowsAccessStatusWithoutPurchaseControls() = runComposeUiTest {
        val intents = mutableListOf<MyPlanIntent>()
        setContent {
            SaqzTheme { MyPlanScreen(trialState(TrialStatus.Expired), {}, intents::add) }
        }
        onNodeWithText("Sem plano, você não marca jogos e a galera não confirma presença. Grupos e histórico estão guardados.").assertExists()
        onNodeWithTag(MyPlanTags.CancelButton).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.Receipts).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.ChangePlan).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.Subscribe).assertDoesNotExist()
        assertEquals(emptyList(), intents)
    }

    @Test
    fun expiredTrialDropsTheNoChargeNoteAndTheRawTimeZone() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(trialState(TrialStatus.Expired), {}, {}) } }
        onNodeWithText("Sem cobrança automática. Quando o teste acabar, você não marca jogos e a galera não confirma presença. Grupos e histórico ficam guardados.").assertDoesNotExist()
        onAllNodes(hasText("America/", substring = true)).assertCountEquals(0)
    }

    @Test
    fun activeTrialShowsTheCountdownAndTheUsageSummary() = runComposeUiTest {
        val trial = MyPlanTrialUi(
            TrialStatus.Active, "2026-09-26T12:30:00Z", true, true,
            daysLeft = 3, usage = TrialUsage(games = 3, confirmedAthletes = 12, cashCents = 45_000),
        )
        setContent { SaqzTheme { MyPlanScreen(MyPlanState(isLoading = false, trial = trial), {}, {}) } }
        onNodeWithText("Faltam 3 dias").assertExists()
        onNodeWithText("3 jogos").assertExists()
        onNodeWithText("12 atletas confirmados").assertExists()
        onNodeWithText("R$\u00A0450,00 no caixa").assertExists()
        onNodeWithText("Espaço para o seu grupo crescer").assertExists()
        onNodeWithText("Sem cobrança automática. Quando o teste acabar, você não marca jogos e a galera não confirma presença. Grupos e histórico ficam guardados.").assertExists()
    }

    @Test
    fun lastDayAndSingularDaysReadNaturally() = runComposeUiTest {
        fun trial(days: Int) = MyPlanState(isLoading = false, trial = MyPlanTrialUi(TrialStatus.Active, "2026-09-26T12:30:00Z", true, true, daysLeft = days))
        val days = androidx.compose.runtime.mutableIntStateOf(1)
        setContent { SaqzTheme { MyPlanScreen(trial(days.intValue), {}, {}) } }
        onNodeWithText("Falta 1 dia").assertExists()
        days.intValue = 0
        waitForIdle()
        onNodeWithText("Último dia do teste").assertExists()
    }

    @Test
    fun availableTrialHasNotStartedYet() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(trialState(TrialStatus.Available), {}, {}) } }
        onNodeWithText("Teste o Organizador por 14 dias. O prazo começa ao criar o primeiro grupo.").assertExists()
    }

    @Test
    fun ineligibleAccountIsNotDescribedAsActiveTrial() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(trialState(TrialStatus.Ineligible), {}, {}) } }
        onNodeWithText("Esta conta não tem um teste gratuito disponível.").assertExists()
    }

    @Test
    fun subscribedHistoryDoesNotShowTrialCard() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(trialState(TrialStatus.Subscribed), {}, {}) } }
        onNodeWithTag(MyPlanTags.TrialCard).assertDoesNotExist()
    }

    @Test
    fun customTrialOfferDisplaysConfiguredDays() = runComposeUiTest {
        val state = MyPlanState(isLoading = false, trial = MyPlanTrialUi(TrialStatus.Available, null, true, true, 45))
        setContent { SaqzTheme { MyPlanScreen(state, {}, {}) } }
        onNodeWithText("Teste o Organizador por 45 dias. O prazo começa ao criar o primeiro grupo.").assertExists()
    }

    private fun trialState(status: TrialStatus) = MyPlanState(
        isLoading = false,
        trial = MyPlanTrialUi(status, "2026-09-12T12:30:00Z", true, true),
    )
}
