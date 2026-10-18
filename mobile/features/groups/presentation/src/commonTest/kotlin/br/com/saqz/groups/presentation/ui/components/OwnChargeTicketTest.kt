package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OwnChargeTicketTest {
    @Test
    fun withoutOnCopyNeitherTheReceiverNorTheButtonIsComposed() = runComposeUiTest {
        setTicket(onCopy = null)

        onNodeWithTag("card").assertIsDisplayed()
        onAllNodesWithTag("copy").assertCountEquals(0)
        onAllNodesWithText("Copiar chave Pix").assertCountEquals(0)
        onAllNodesWithText("Pix de Lucas Prado").assertCountEquals(0)
    }

    @Test
    fun copyButtonForwardsTheClick() = runComposeUiTest {
        var copies = 0
        setTicket(onCopy = { copies += 1 })

        onNodeWithText("Pix de Lucas Prado").assertIsDisplayed()
        onNodeWithText("Copiar chave Pix").assertIsDisplayed()
        onNodeWithTag("copy").performClick()

        assertEquals(1, copies)
    }

    @Test
    fun copiedSwapsTheButtonLabel() = runComposeUiTest {
        setTicket(copied = true)

        onNodeWithText("Chave copiada").assertIsDisplayed()
        onAllNodesWithText("Copiar chave Pix").assertCountEquals(0)
    }

    @Test
    fun detailsSlotAndFootnoteAreComposed() = runComposeUiTest {
        setTicket(footnote = "Pagamento é manual: depois de pagar, o admin dá baixa no caixa do grupo.") {
            Text(text = "Mensalidade · Agosto", modifier = Modifier.testTag("details"))
        }

        onNodeWithTag("details").assertIsDisplayed()
        onNodeWithText("Pagamento é manual: depois de pagar, o admin dá baixa no caixa do grupo.").assertIsDisplayed()
    }

    @Test
    fun dueChipRendersOnceWithOrWithoutTheHeaderChip() = runComposeUiTest {
        setTicket(headerChip = null)

        onAllNodesWithText("Venceu em 10/08").assertCountEquals(1)
        onAllNodesWithText("Vôlei do CERET").assertCountEquals(0)
    }

    @Test
    fun nullOnClickLeavesTheCardWithoutClickAction() = runComposeUiTest {
        setTicket(onClick = null)

        onNodeWithTag("card").assertHasNoClickAction()
    }

    // iPhone estreito: o valor grande quebrava no meio do número ("R$ 150,0" / "0") ao lado do chip.
    @Test
    fun amountNeverBreaksAndTheChipDropsBelowWhenItDoesNotFit() = runComposeUiTest {
        setContent {
            SaqzTheme {
                Box(Modifier.width(200.dp)) { Ticket(amount = "R$ 1.150,00", headerChip = "Vôlei do CERET") }
            }
        }
        val amount = onNodeWithText("R$ 1.150,00", useUnmergedTree = true)
        val chip = onNodeWithText("Venceu em 10/08", useUnmergedTree = true)
        val results = mutableListOf<TextLayoutResult>()
        amount.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertEquals(1, results.single().lineCount, "o valor não pode quebrar")
        assertTrue(chip.getBoundsInRoot().top >= amount.getBoundsInRoot().bottom, "o chip desce para a linha de baixo")
    }

    @Test
    fun amountAndChipShareTheLineWhenTheyFit() = runComposeUiTest {
        setContent { SaqzTheme { Box(Modifier.width(375.dp)) { Ticket(amount = "R$ 95,00", headerChip = "Vôlei do CERET") } } }
        val amount = onNodeWithText("R$ 95,00", useUnmergedTree = true).getBoundsInRoot()
        val chip = onNodeWithText("Venceu em 10/08", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(chip.top < amount.bottom, "cabendo, o chip fica ao lado do valor")
        assertTrue(chip.left >= amount.right, "o chip fica à direita do valor")
    }

    @Composable
    private fun Ticket(amount: String, headerChip: String?) = OwnChargeTicket(
        eyebrow = "Em aberto",
        amountLabel = amount,
        dueChipLabel = "Venceu em 10/08",
        dueChipOverdue = true,
        headerChip = headerChip,
        countLabel = null,
        receiverLabel = null,
        copied = false,
        copyLabel = "Copiar chave Pix",
        copiedLabel = "Chave copiada",
        onCopy = null,
        onClick = null,
        contentDescription = null,
        tags = OwnChargeTicketTags(card = "card", copy = "copy"),
    )

    private fun ComposeUiTest.setTicket(
        headerChip: String? = null,
        copied: Boolean = false,
        onCopy: (() -> Unit)? = {},
        onClick: (() -> Unit)? = null,
        contentDescription: String? = null,
        footnote: String? = null,
        details: (@Composable ColumnScope.() -> Unit)? = null,
    ) = setContent {
        SaqzTheme {
            OwnChargeTicket(
                eyebrow = "Em aberto",
                amountLabel = "R$ 95,00",
                dueChipLabel = "Venceu em 10/08",
                dueChipOverdue = true,
                headerChip = headerChip,
                countLabel = null,
                receiverLabel = "Pix de Lucas Prado",
                copied = copied,
                copyLabel = "Copiar chave Pix",
                copiedLabel = "Chave copiada",
                onCopy = onCopy,
                onClick = onClick,
                contentDescription = contentDescription,
                tags = OwnChargeTicketTags(card = "card", copy = "copy"),
                footnote = footnote,
                details = details,
            )
        }
    }
}
