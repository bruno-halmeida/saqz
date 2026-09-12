package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanTrialUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MyPlanTrialScreenTest {
    @Test
    fun expiredTrialExplainsReadOnlyAndOffersSubscriptionWithoutPaidControls() = runComposeUiTest {
        val intents = mutableListOf<MyPlanIntent>()
        setContent {
            SaqzTheme { MyPlanScreen(trialState(TrialStatus.Expired), {}, intents::add) }
        }
        onNodeWithText("Seu histórico está preservado. Assine para criar jogos, receber novas respostas de presença e voltar a alterar o financeiro.").assertExists()
        onNodeWithTag(MyPlanTags.CancelButton).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.Receipts).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.ChangePlan).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.Subscribe).performClick()
        assertEquals(listOf<MyPlanIntent>(MyPlanIntent.OpenSubscribe), intents)
    }

    @Test
    fun availableTrialHasNotStartedYet() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(trialState(TrialStatus.Available), {}, {}) } }
        onNodeWithText("Seus 14 dias grátis começam ao criar o primeiro grupo.").assertExists()
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

    private fun trialState(status: TrialStatus) = MyPlanState(
        isLoading = false,
        trial = MyPlanTrialUi(status, "2026-09-12T12:30:00Z", true, true),
    )
}
