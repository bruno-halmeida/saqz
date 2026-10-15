package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.presentation.myplan.MyPlanCardUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanStatusTone
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Reativar" na assinatura cancelada que ainda dá acesso até o fim do período pago. */
@OptIn(ExperimentalTestApi::class)
class MyPlanReactivateScreenTest {
    @Test
    fun canceledWithAccessOffersReactivateAndDispatchesTheIntent() = runComposeUiTest {
        val intents = mutableListOf<MyPlanIntent>()
        setContent { SaqzTheme { MyPlanScreen(canceled(), {}, intents::add, purchasesAvailable = true) } }

        onNodeWithTag(MyPlanTags.Reactivate).performClick()

        assertEquals(listOf<MyPlanIntent>(MyPlanIntent.Reactivate), intents)
        onNodeWithTag(MyPlanTags.Resubscribe).assertDoesNotExist()
    }

    @Test
    fun withoutCancellationThereIsNoReactivate() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(canceled().copy(canReactivate = false), {}, {}, purchasesAvailable = true) } }
        onNodeWithTag(MyPlanTags.Reactivate).assertDoesNotExist()
    }

    @Test
    fun storePlanOffersReactivateOnlyOnTheDeviceOfThatStore() = runComposeUiTest {
        val store = canceled().copy(managingStore = SubscriptionProvider.AppStore)
        setContent { SaqzTheme { MyPlanScreen(store, {}, {}, deviceStore = SubscriptionProvider.AppStore, purchasesAvailable = false) } }
        onNodeWithTag(MyPlanTags.Reactivate).assertExists()
    }

    @Test
    fun storePlanSeenOnAnotherStoreDeviceHasNoReactivate() = runComposeUiTest {
        val store = canceled().copy(managingStore = SubscriptionProvider.AppStore)
        setContent { SaqzTheme { MyPlanScreen(store, {}, {}, deviceStore = SubscriptionProvider.GooglePlay, purchasesAvailable = true) } }
        onNodeWithTag(MyPlanTags.Reactivate).assertDoesNotExist()
    }

    @Test
    fun aFailedReactivationShowsTheError() = runComposeUiTest {
        setContent {
            SaqzTheme {
                MyPlanScreen(canceled().copy(reactivateError = UiText.Raw("Não deu certo")), {}, {}, purchasesAvailable = true)
            }
        }
        onNodeWithTag(MyPlanTags.ReactivateError).assertExists()
    }

    private fun canceled() = MyPlanState(
        isLoading = false,
        plan = MyPlanCardUi(
            name = "Organizador",
            statusLabel = UiText.Raw("Cancelado"),
            statusTone = MyPlanStatusTone.Canceled,
            nextChargeDate = null,
            accessUntilDate = "12/11/2026",
        ),
        canReactivate = true,
    )
}
