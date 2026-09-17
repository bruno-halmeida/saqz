package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WaitingRowTest {
    @Test
    fun nullOnClickHasNoClickActionAndTheTrailingButtonIsTheOnlyTarget() = runComposeUiTest {
        var reminders = 0
        setContent {
            SaqzTheme {
                WaitingRow(
                    icon = SaqzIcons.Megaphone,
                    title = "2 pessoas sem resposta",
                    meta = "Encerra hoje às 18h",
                    contentDescription = "2 pessoas sem resposta. Encerra hoje às 18h.",
                    onClick = null,
                    tag = "row",
                ) {
                    SaqzButton(
                        label = "Avisar",
                        onClick = { reminders += 1 },
                        variant = SaqzButtonVariant.Secondary,
                        size = SaqzButtonSize.Sm,
                        modifier = Modifier.testTag("trailing"),
                    )
                }
            }
        }

        onNodeWithTag("row").assertIsDisplayed().assertHasNoClickAction()
        onNodeWithTag("trailing").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, reminders)
    }

    @Test
    fun clickableRowForwardsTheClickAndCarriesTheDescription() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                WaitingRow(
                    icon = SaqzIcons.CreditCard,
                    title = "2 mensalidades a receber",
                    meta = "R$ 140,00 · agosto",
                    contentDescription = "2 mensalidades a receber em Vôlei do CERET",
                    onClick = { clicks += 1 },
                    tag = "row",
                ) {
                    Box(modifier = Modifier.size(22.dp).testTag("trailing"))
                }
            }
        }

        onNodeWithContentDescription("2 mensalidades a receber em Vôlei do CERET").assertHasClickAction()
        // A linha clicável funde os filhos: o trailing só existe na árvore não fundida.
        onNodeWithTag("trailing", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("row").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun leadingReplacesTheIconCircleAndNullMetaHidesTheSecondLine() = runComposeUiTest {
        setContent {
            SaqzTheme {
                WaitingRow(
                    icon = SaqzIcons.Users,
                    title = "26 pessoas",
                    meta = null,
                    contentDescription = "26 pessoas no grupo",
                    onClick = {},
                    tag = "row",
                    leading = { Box(modifier = Modifier.size(40.dp).testTag("leading")) },
                ) {
                    Box(modifier = Modifier.size(22.dp))
                }
            }
        }

        onNodeWithTag("leading", useUnmergedTree = true).assertIsDisplayed()
        // Sem meta, o título é o único texto que a linha (clicável, logo fundida) carrega.
        val texts = onNodeWithTag("row").fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.Text) { null }
        assertEquals(listOf("26 pessoas"), texts?.map { it.text })
    }
}
