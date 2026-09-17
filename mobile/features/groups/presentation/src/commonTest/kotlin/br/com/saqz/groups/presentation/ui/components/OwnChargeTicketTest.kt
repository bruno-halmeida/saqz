package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

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
