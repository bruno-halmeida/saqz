package br.com.saqz.groups.presentation.ui.finance.review

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.ReviewDecision
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UnchargedReviewScreenTest {
    @Test
    fun `rows show the state pill the paid line and the summary`() = runComposeUiTest {
        val intents = mutableListOf<UnchargedReviewIntent>()
        setContent { SaqzTheme { UnchargedReviewScreen(state, onBack = {}, onIntent = intents::add) } }

        onNodeWithText("Terça, 20/10 · CERET").assertExists()
        onNodeWithText("Entra no caixa em 20/10 ·").assertExists()
        onNodeWithText("Convite · paga Bia").assertExists()
        onNodeWithText("Saiu do grupo").assertExists()
        onNodeWithText("2 cobranças · 1 já pagou · 2 sem cobrança").assertExists()
        onNodeWithTag(UnchargedReviewTags.method("b/0")).performClick()
        onNodeWithTag(UnchargedReviewTags.pill("a/0")).performClick()
        onNodeWithTag(UnchargedReviewTags.Confirm).performClick()

        assertEquals(
            listOf(
                UnchargedReviewIntent.CycleMethod("b/0"),
                UnchargedReviewIntent.OpenMenu("a/0"),
                UnchargedReviewIntent.Confirm,
            ),
            intents,
        )
    }

    @Test
    fun `menu explains each state and chooses`() = runComposeUiTest {
        val intents = mutableListOf<UnchargedReviewIntent>()
        setContent { SaqzTheme { UnchargedReviewScreen(state.copy(menuKey = "b/0"), onBack = {}, onIntent = intents::add) } }

        onNodeWithText("Diária pendente de R$ 20").assertExists()
        onNodeWithText("Recebida, com a data do jogo").assertExists()
        onNodeWithText("Fica marcado, dá para reabrir").assertExists()
        onNodeWithTag(UnchargedReviewTags.option(ReviewDecision.Skip)).performClick()

        assertEquals(listOf<UnchargedReviewIntent>(UnchargedReviewIntent.Choose("b/0", ReviewDecision.Skip)), intents)
    }

    @Test
    fun `confirm is locked while sending`() = runComposeUiTest {
        setContent { SaqzTheme { UnchargedReviewScreen(state.copy(saving = true), onBack = {}, onIntent = {}) } }

        onNodeWithTag(UnchargedReviewTags.Confirm).assertIsNotEnabled()
    }

    private val state = UnchargedReviewState(
        isLoading = false,
        gameDay = "Terça, 20/10",
        venueName = "CERET",
        gameDate = "20/10",
        amountInput = "20,00",
        suggested = true,
        rows = listOf(
            ReviewRowUi("a/0", "a", 0, "Caio Lima", null, false, false, ReviewDecision.Charge),
            ReviewRowUi("b/0", "b", 0, "Duda Alves", null, false, false, ReviewDecision.Paid),
            ReviewRowUi("c/1", "c", 1, "Júlia Rocha", "Bia", false, false, ReviewDecision.Charge),
            ReviewRowUi("d/0", "d", 0, "Rafa Nunes", null, false, true, ReviewDecision.Skip),
            ReviewRowUi("e/0", "e", 0, "Marcos Lima", null, true, false, ReviewDecision.Skip),
        ),
    )
}
