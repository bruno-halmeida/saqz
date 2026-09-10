package br.com.saqz.subscriptions.presentation.changeplan

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.presentation.ui.changeplan.ChangePlanScreen
import br.com.saqz.subscriptions.presentation.ui.changeplan.ChangePlanTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChangePlanScreenTest {
    @Test
    fun `confirmation remains above catalog and dispatches the selected action`() = runComposeUiTest {
        val intents = mutableListOf<ChangePlanIntent>()
        val card = ChangePlanCardUi(Plan.Organizador, "Organizador", UiText.Raw("R$ 59,90"), emptyList(), false)
        setContent {
            SaqzTheme {
                ChangePlanScreen(
                    state = ChangePlanState(isLoading = false, plans = listOf(card), confirmTarget = card),
                    onBack = {}, onIntent = intents::add, onCopyPix = {}, onOpenInvoice = {},
                )
            }
        }
        onNodeWithTag(ChangePlanTags.Screen).assertExists()
        onNodeWithTag(ChangePlanTags.ConfirmSheet).assertExists()
        onNodeWithText("Trocar para Organizador?").assertExists()
        onNodeWithText("Trocar plano").performClick()
        assertEquals(listOf<ChangePlanIntent>(ChangePlanIntent.ConfirmChange), intents)
    }
}
