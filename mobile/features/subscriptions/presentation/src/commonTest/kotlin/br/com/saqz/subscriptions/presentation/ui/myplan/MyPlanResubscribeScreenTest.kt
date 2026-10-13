package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.presentation.myplan.MyPlanCardUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanStatusTone
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Assinar de novo" no cartão da assinatura encerrada (a revisão da Apple não achava as compras). */
@OptIn(ExperimentalTestApi::class)
class MyPlanResubscribeScreenTest {
    @Test
    fun endedSubscriptionOffersToSubscribeAgain() = runComposeUiTest {
        val intents = mutableListOf<MyPlanIntent>()
        setContent { SaqzTheme { MyPlanScreen(ended(canResubscribe = true), {}, intents::add, purchasesAvailable = true) } }

        onNodeWithText("Assinar de novo").assertExists()
        onNodeWithTag(MyPlanTags.Resubscribe).performClick()

        assertEquals(listOf<MyPlanIntent>(MyPlanIntent.OpenSubscribe), intents)
    }

    @Test
    fun withoutAnyWayToBuyTheButtonStaysHidden() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(ended(canResubscribe = true), {}, {}, purchasesAvailable = false) } }
        onNodeWithTag(MyPlanTags.Resubscribe).assertDoesNotExist()
    }

    @Test
    fun aPlanThatStillGivesAccessHasNoResubscribeButton() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(ended(canResubscribe = false), {}, {}, purchasesAvailable = true) } }
        onNodeWithTag(MyPlanTags.Resubscribe).assertDoesNotExist()
    }

    private fun ended(canResubscribe: Boolean) = MyPlanState(
        isLoading = false,
        plan = MyPlanCardUi(
            name = "Organizador",
            statusLabel = UiText.Raw("Cancelado"),
            statusTone = MyPlanStatusTone.Canceled,
            nextChargeDate = null,
            accessUntilDate = "10/10/2026",
        ),
        canResubscribe = canResubscribe,
    )
}
