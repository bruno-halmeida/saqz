package br.com.saqz.groups.presentation.ui.finance.sheets

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.presentation.ui.finance.groupcash.DebtorUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FinanceSheetsTest {
    @Test
    fun `charge sheet shows individual WhatsApp billing and group Pix details`() = runComposeUiTest {
        var sent: Pair<DebtorUi, String>? = null
        val debtors = listOf(
            debtor("charge-camila", "Camila", 7_000L, "Mensalista · agosto"),
            debtor("charge-pedro", "Pedro", 3_000L, "Diarista · jogo de 28/07"),
        )

        setContent {
            SaqzTheme {
                ChargeSheet(
                    open = true,
                    debtors = debtors,
                    pixKey = "pix@saqz.com",
                    pixLabel = "Vôlei do CERET",
                    onClose = {},
                    onCopyPix = {},
                    onSend = { debtor, message -> sent = debtor to message },
                )
            }
        }

        onNodeWithText("Cobrar 2 cobranças").assertExists()
        onNodeWithText("R$\u00A0100,00 em aberto · Mensalista · agosto / Diarista · jogo de 28/07").assertExists()
        onNodeWithText("Cobrar no WhatsApp").assertExists()
        onNodeWithText("pix@saqz.com").assertExists()
        onNodeWithText("Vôlei do CERET").assertExists()
        onAllNodesWithText("Aviso no app").assertCountEquals(0)

        onNodeWithTag(FinanceSheetsTags.chargeRecipient("charge-pedro")).performClick()
        onNodeWithTag(FinanceSheetsTags.ChargeSend).performClick()

        val sentPair = sent ?: error("Expected a WhatsApp charge")
        assertEquals("charge-pedro", sentPair.first.chargeId)
        assertTrue(sentPair.second.contains("Pedro"))
        assertTrue(sentPair.second.contains("pix@saqz.com"))
    }

    @Test
    fun `charge sheet uses singular title and debtor reference in summary`() = runComposeUiTest {
        val debtor = debtor("charge-game", "Ana", 7_000L, "Diarista · jogo de 28/07")

        setContent {
            SaqzTheme {
                ChargeSheet(
                    open = true,
                    debtors = listOf(debtor),
                    pixKey = "pix@saqz.com",
                    pixLabel = "Vôlei do CERET",
                    onClose = {},
                    onCopyPix = {},
                    onSend = { _, _ -> },
                )
            }
        }

        onNodeWithText("Cobrar 1 cobrança").assertExists()
        onNodeWithText("R$\u00A070,00 em aberto", substring = true).assertExists()
        onNodeWithText("Diarista · jogo de 28/07", substring = true).assertExists()
    }

    @Test
    fun `receipt sheet keeps amount fixed and confirms the selected paid method`() = runComposeUiTest {
        var method: PaidMethod? = null
        val debtor = debtor("charge-ana", "Ana", 7_000L, "Diarista · jogo de 28/07")

        setContent {
            SaqzTheme {
                ReceiptSheet(
                    open = true,
                    debtor = debtor,
                    onClose = {},
                    onConfirm = { method = it },
                )
            }
        }

        onNodeWithText("Ana").assertExists()
        onAllNodesWithText("Diarista · jogo de 28/07").assertCountEquals(3)
        onNodeWithText("R$\u00A07.000,00").assertExists()
        onNodeWithText("Referente a").assertExists()
        onNodeWithText("Como recebeu").assertExists()
        onNodeWithText("Pix").assertExists()
        onNodeWithText("Dinheiro").performClick()
        onNodeWithTag(FinanceSheetsTags.ReceiptConfirm).performClick()

        assertEquals(PaidMethod.Cash, method)
        onAllNodesWithText("Pagamento parcial").assertCountEquals(0)
        onAllNodesWithText("Avisar que o pagamento entrou").assertCountEquals(0)
    }

    @Test
    fun `WhatsApp URL percent-encodes the prefilled message`() {
        val url = whatsappChargeUrl("Olá Ana! R$ 70,00")

        assertEquals("https://wa.me/?text=Ol%C3%A1%20Ana%21%20R%24%2070%2C00", url)
    }

    private fun debtor(id: String, name: String, cents: Long, reference: String) = DebtorUi(
        chargeId = id,
        memberId = "member-$id",
        name = name,
        dueLabel = "Vence em 28/07",
        amountLabel = if (cents == 7_000L) "R$\u00A07.000,00" else "R$\u00A03.000,00",
        amountCents = cents,
        chargeVersion = 1L,
        month = "2026-08",
        referenceLabel = reference,
    )
}
