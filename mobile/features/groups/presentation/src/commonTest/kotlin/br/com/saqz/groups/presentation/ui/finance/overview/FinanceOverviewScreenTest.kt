package br.com.saqz.groups.presentation.ui.finance.overview

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewIntent
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewPeriodSelection
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FinanceOverviewScreenTest {
    @Test
    fun `filled state shows balance structured group status and recent transaction`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                FinanceOverviewScreen(FinanceOverviewSamples.filled, onIntent = {})
            }
        }

        onNodeWithTag(FinanceOverviewTags.Balance).assertExists()
        onNodeWithTag(FinanceOverviewTags.BalanceValue).assertTextEquals("R$ 60.000,00")
        onNodeWithText("8 mensalidades em aberto", useUnmergedTree = true).assertExists()
        onNodeWithText("Tudo em dia", useUnmergedTree = true).assertExists()
        onNodeWithText("Sem cobrança ativa", useUnmergedTree = true).assertExists()
        onNodeWithText("+R$ 120,00", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `group row and period selector forward their intents`() = runComposeUiTest {
        val intents = mutableListOf<FinanceOverviewIntent>()
        setContent {
            SaqzTheme {
                FinanceOverviewScreen(FinanceOverviewSamples.filled, onIntent = intents::add)
            }
        }

        onNodeWithTag(FinanceOverviewTags.group("group-1"), useUnmergedTree = true).assertHasClickAction().performClick()
        onNodeWithTag(FinanceOverviewTags.period(FinanceOverviewPeriodSelection.PreviousMonth))
            .assertHasClickAction()
            .performClick()

        assertEquals(
            listOf(
                FinanceOverviewIntent.OpenGroup("group-1"),
                FinanceOverviewIntent.SelectPeriod(FinanceOverviewPeriodSelection.PreviousMonth),
            ),
            intents,
        )
    }

    @Test
    fun `empty and failure states expose their required actions`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                FinanceOverviewScreen(FinanceOverviewSamples.empty, onIntent = {})
            }
        }
        onNodeWithTag(FinanceOverviewTags.Empty).assertExists()
        onNodeWithTag(FinanceOverviewTags.Balance, useUnmergedTree = true).assertDoesNotExist()

        setContent {
            SaqzTheme {
                FinanceOverviewScreen(FinanceOverviewSamples.failed, onIntent = {})
            }
        }
        onNodeWithTag(FinanceOverviewTags.Failure).assertExists()
        onNodeWithTag(FinanceOverviewTags.Retry).assertExists().assertHasClickAction()
    }
}
