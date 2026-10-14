package br.com.saqz.groups.presentation.ui.finance.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import kotlin.test.assertTrue
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
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewTransactionUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FinanceOverviewScreenTest {
    @Test
    fun `summary with font scale 2 keeps every amount whole`() = runComposeUiTest {
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                SaqzTheme {
                    Box(Modifier.width(360.dp)) { FinanceOverviewScreen(FinanceOverviewSamples.filled, onIntent = {}) }
                }
            }
        }

        val filled = FinanceOverviewSamples.filled
        listOf(filled.entered, filled.left, filled.receivable).forEach { amount ->
            val bounds = onNode(hasText(amount) and hasAnyAncestor(hasTestTag(FinanceOverviewTags.Metrics)), useUnmergedTree = true).getBoundsInRoot()
            // Valor inteiro: "R$ 120.000,00" não cabe em menos que isso a 2x; "R$" sozinho caberia.
            assertTrue(bounds.width >= 80.dp, "valor cortado ($amount): ${bounds.width}")
            assertTrue(bounds.right <= 360.dp, "valor para fora da tela ($amount): $bounds")
        }
    }

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
    fun `transaction without direction shows a neutral title`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                FinanceOverviewScreen(
                    state = FinanceOverviewSamples.filled.copy(
                        recentTransactions = listOf(
                            FinanceOverviewTransactionUi(
                                id = "transaction-unknown",
                                title = null,
                                groupAndDate = "Grupo · 03/07",
                                amount = "R$ 10,00",
                                isIncoming = null,
                            ),
                        ),
                    ),
                    onIntent = {},
                )
            }
        }

        onNodeWithText("Lançamento", useUnmergedTree = true).assertExists()
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
