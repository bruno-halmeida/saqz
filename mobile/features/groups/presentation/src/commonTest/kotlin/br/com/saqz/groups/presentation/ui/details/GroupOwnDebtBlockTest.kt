package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PixKey = "ceret@volei.com.br"
private const val NoPixNote =
    "O grupo ainda não cadastrou uma chave Pix. Combine o pagamento com o admin — a baixa acontece no caixa do grupo."

@OptIn(ExperimentalTestApi::class)
class GroupOwnDebtBlockTest {
    @Test
    fun debtShowsTheTicketWithOneLinePerPendingChargeAndThePixKey() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.owesTwo)

        onNodeWithTag(GroupDetailsTags.OwnCharges).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnDebt).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesPending).assertExists()
        onNodeWithText("R$ 95,00").assertExists()
        onNodeWithText("2 cobranças em aberto").assertExists()
        // O mesmo formato que o e2e usa: status DESCENDENTE da tag da linha, árvore não mesclada.
        onNode(
            hasText("Em aberto") and hasAnyAncestor(hasTestTag(GroupDetailsTags.ownCharge("c-1"))),
            useUnmergedTree = true,
        ).assertExists()
        onNode(
            hasText("R$ 25,00") and hasAnyAncestor(hasTestTag(GroupDetailsTags.ownCharge("c-2"))),
            useUnmergedTree = true,
        ).assertExists()
    }

    // Gestor que deve: o mesmo bloco. Com o histórico aberto, cada tag contratual conta 1 —
    // o harness do e2e (`waitTag`) espera EXATAMENTE um nó por tag.
    @Test
    fun historyStaysOutOfTheTreeUntilTheToggleIsTappedAndEveryTagExistsOnce() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.adminOwes)

        onAllNodesWithTag(GroupDetailsTags.OwnChargesHistory).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.ownCharge("c-3")).assertCountEquals(0)
        onNodeWithText("Ver histórico (3)").assertExists()

        onNodeWithTag(GroupDetailsTags.OwnChargesHistoryToggle).performScrollTo().performClick()

        onNode(
            hasText("Paga") and hasAnyAncestor(hasTestTag(GroupDetailsTags.ownCharge("c-3"))),
            useUnmergedTree = true,
        ).assertExists()
        onNodeWithText("Isenta").assertExists()
        onNodeWithText("Cancelada").assertExists()
        onNodeWithText("Ocultar histórico").assertExists()
        val tags = listOf(
            GroupDetailsTags.OwnCharges,
            GroupDetailsTags.OwnDebt,
            GroupDetailsTags.OwnChargesPending,
            GroupDetailsTags.OwnChargesPix,
            GroupDetailsTags.OwnChargesPixCopy,
            GroupDetailsTags.OwnChargesHistoryToggle,
            GroupDetailsTags.OwnChargesHistory,
        ) + listOf("c-1", "c-2", "c-3", "c-4", "c-5").map(GroupDetailsTags::ownCharge)
        tags.forEach { onAllNodesWithTag(it).assertCountEquals(1) }
        onAllNodesWithTag(GroupDetailsTags.OwnChargesSettled).assertCountEquals(0)
        onAllNodesWithText("Minhas cobranças").assertCountEquals(1)

        onNodeWithTag(GroupDetailsTags.OwnChargesHistoryToggle).performScrollTo().performClick()

        onAllNodesWithTag(GroupDetailsTags.OwnChargesHistory).assertCountEquals(0)
    }

    // Contrato do e2e `monthly-history`: a chave é um Text próprio, achado por texto exato e único.
    @Test
    fun pixKeyIsItsOwnExactTextAndCopyAsksForIt() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupOwnDebtPreviewData.owesTwo) { intents += it }

        onNodeWithTag(GroupDetailsTags.OwnChargesPix).assertExists()
        onNodeWithText(PixKey).assertExists()
        onNode(hasText(PixKey) and hasAnyAncestor(hasTestTag(GroupDetailsTags.OwnChargesPix))).assertExists()
        onNode(hasText("Pix de Lucas Prado") and hasAnyAncestor(hasTestTag(GroupDetailsTags.OwnChargesPix))).assertExists()
        onNodeWithText("Copiar chave Pix").assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesPixCopy).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.CopyPix, intents.single())
    }

    @Test
    fun copiedKeySwapsTheButtonLabel() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.pixCopied)

        onNodeWithText("Chave copiada").assertExists()
        onAllNodesWithText("Copiar chave Pix").assertCountEquals(0)
    }

    @Test
    fun groupWithoutPixHidesTheButtonAndExplainsWhatToDo() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.noPix)

        onNodeWithTag(GroupDetailsTags.OwnDebt).assertExists()
        onNodeWithTag(GroupDetailsTags.ownCharge("c-1")).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPix).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPixCopy).assertCountEquals(0)
        onNodeWithText(NoPixNote).assertExists()
    }

    @Test
    fun singleChargeStillOnTimeHasNoCountAndKeepsItsLine() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.owesOne)

        onNodeWithTag(GroupDetailsTags.ownCharge("c-2")).assertExists()
        onAllNodesWithTag(GroupDetailsTags.ownCharge("c-1")).assertCountEquals(0)
        onAllNodesWithText("2 cobranças em aberto").assertCountEquals(0)
    }

    @Test
    fun settledShowsOnlyTheRowAtTheEndAndTheTapOpensTheHistory() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.settled)

        onNodeWithTag(GroupDetailsTags.OwnChargesSettled).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnCharges).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnDebt).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPix).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesHistory).assertCountEquals(0)
        onNode(
            hasText("Tudo em dia") and hasAnyAncestor(hasTestTag(GroupDetailsTags.OwnChargesSettled)),
            useUnmergedTree = true,
        ).assertExists()

        onNodeWithTag(GroupDetailsTags.OwnChargesSettled).performScrollTo().performClick()

        onNodeWithTag(GroupDetailsTags.OwnChargesHistory).assertExists()
        onNodeWithText("Paga").assertExists()
    }

    @Test
    fun loadingShowsOnlyTheSkeleton() = runComposeUiTest {
        setDetailsScreen(GroupOwnDebtPreviewData.loading)

        onNodeWithTag(GroupDetailsTags.OwnChargesSkeleton).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnDebt).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesSettled).assertCountEquals(0)
    }

    // A seção falha sozinha: o resto do detalhe continua na tela, com retry só dela.
    @Test
    fun failureKeepsTheScreenAndOffersRetry() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupOwnDebtPreviewData.failed) { intents += it }

        onNodeWithTag(GroupDetailsTags.Mural).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesFailure).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesSettled).assertCountEquals(0)
        onNodeWithTag(GroupDetailsTags.OwnChargesRetry).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.RetryOwnCharges, intents.single())
    }

    @Test
    fun memberWithoutChargesHasNoOwnChargesAnywhere() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member.copy(ownCharges = null))

        onAllNodesWithTag(GroupDetailsTags.OwnCharges).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesSettled).assertCountEquals(0)
        onAllNodesWithText("Minhas cobranças").assertCountEquals(0)
    }
}
