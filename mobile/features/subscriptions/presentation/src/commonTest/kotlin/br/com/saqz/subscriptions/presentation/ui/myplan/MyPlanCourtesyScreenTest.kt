package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test

/** Cortesia sem assinatura (DEB-097): cartão e prazo, sem gestão de plano pago nem cancelamento. */
@OptIn(ExperimentalTestApi::class)
class MyPlanCourtesyScreenTest {
    @Test
    fun courtesyOnlyShowsTheCardAndNoManagementOrCancel() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(MyPlanPreviewData.courtesyOnly, {}, {}, purchasesAvailable = true) } }

        onNodeWithTag(MyPlanTags.PlanCard).assertExists()
        onNodeWithTag(MyPlanTags.CourtesyNote).assertExists()
        onNodeWithTag(MyPlanTags.Receipts).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.ChangePlan).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.CancelButton).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.Resubscribe).assertDoesNotExist()
        onNodeWithTag(MyPlanTags.UsageCard).assertDoesNotExist()
    }

    @Test
    fun courtesyOverPaidKeepsManagementForTheSubscription() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(MyPlanPreviewData.courtesyOverPaid, {}, {}, purchasesAvailable = true) } }

        onNodeWithTag(MyPlanTags.CourtesyNote).assertExists()
        onNodeWithTag(MyPlanTags.SubscriptionNote).assertExists()
        onNodeWithTag(MyPlanTags.Receipts).assertExists()
        onNodeWithTag(MyPlanTags.UsageCard).assertDoesNotExist()
    }
}
