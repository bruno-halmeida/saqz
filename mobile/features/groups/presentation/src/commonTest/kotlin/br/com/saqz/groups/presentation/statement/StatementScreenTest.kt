package br.com.saqz.groups.presentation.statement

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.FinanceDirection
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StatementScreenTest {
    private val charge = StatementItemUi("charge-1", FinanceDirection.In, "Mensalidade · Bia", "Pix · 04/08/2026", "+R$ 80,00")
    private val manual = StatementItemUi(
        "expense-1", FinanceDirection.Out, "Aluguel da quadra", "Quadra · 03/08/2026", "−R$ 320,00", manual = true,
    )

    @Test
    fun `manual entries show correct and void while charges show neither`() = runComposeUiTest {
        val intents = mutableListOf<StatementIntent>()
        setScreen(StatementState(isLoading = false, items = listOf(charge, manual)), intents::add)

        onNodeWithTag(StatementTags.edit("charge-1")).assertDoesNotExist()
        onNodeWithTag(StatementTags.void("charge-1")).assertDoesNotExist()
        onNodeWithTag(StatementTags.edit("expense-1")).performClick()
        onNodeWithTag(StatementTags.void("expense-1")).performClick()

        assertEquals(
            listOf(StatementIntent.Edit("expense-1"), StatementIntent.AskVoid("expense-1")),
            intents,
        )
    }

    @Test
    fun `void sheet says the effect and confirms or dismisses`() = runComposeUiTest {
        val intents = mutableListOf<StatementIntent>()
        setScreen(
            StatementState(isLoading = false, items = listOf(manual), voidTargetId = "expense-1"),
            intents::add,
        )

        onNodeWithText("Anular lançamento").assertExists()
        onNodeWithText("O valor sai do saldo e do extrato. Não dá para desfazer.").assertExists()
        onNodeWithTag(StatementTags.VoidConfirm).performClick()
        onNodeWithText("Agora não").performClick()

        assertEquals(listOf(StatementIntent.ConfirmVoid, StatementIntent.DismissVoid), intents)
    }

    @Test
    fun `while voiding the confirm and the row actions are disabled`() = runComposeUiTest {
        setScreen(
            StatementState(isLoading = false, items = listOf(manual), voidTargetId = "expense-1", isVoiding = true),
            {},
        )

        onNodeWithTag(StatementTags.VoidConfirm).assertIsNotEnabled()
        onNodeWithTag(StatementTags.edit("expense-1")).assertIsNotEnabled()
        onNodeWithTag(StatementTags.void("expense-1")).assertIsNotEnabled()
    }

    @Test
    fun `no void target keeps the sheet closed`() = runComposeUiTest {
        setScreen(StatementState(isLoading = false, items = listOf(manual)), {})

        onNodeWithText("Anular lançamento").assertDoesNotExist()
    }

    @Test
    fun `action errors are explained on the statement`() = runComposeUiTest {
        setScreen(
            StatementState(isLoading = false, items = listOf(manual), actionError = StatementActionError.Conflict),
            {},
        )

        onNodeWithText("Este lançamento mudou. Atualizamos o extrato.").assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: StatementState,
        onIntent: (StatementIntent) -> Unit,
    ) = setContent {
        SaqzTheme { StatementScreen(state = state, onBack = {}, onIntent = onIntent) }
    }
}
