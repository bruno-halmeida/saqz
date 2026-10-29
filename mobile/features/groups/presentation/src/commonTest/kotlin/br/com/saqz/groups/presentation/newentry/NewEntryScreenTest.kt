package br.com.saqz.groups.presentation.newentry

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NewEntryScreenTest {
    @Test
    fun `expense has the amount shortcuts four equal categories and the date that opens the calendar`() =
        runComposeUiTest {
            val intents = mutableListOf<NewEntryIntent>()
            setContent {
                SaqzTheme {
                    NewEntryScreen(
                        state = NewEntryState(
                            date = "2026-10-27",
                            direction = NewEntryDirection.Out,
                            category = NewEntryCategory.Court,
                        ),
                        onBack = {},
                        onIntent = intents::add,
                    )
                }
            }

            onNodeWithText("Nova saída").assertExists()
            onNodeWithTag(NewEntryTags.Shortcuts).assertExists()
            onNodeWithText("Árbitro").assertExists()
            onNodeWithText("Hoje · terça, 27/10").assertExists()
            onNodeWithText("R$\u00A0120").performClick()
            onNodeWithTag(NewEntryTags.Date).performClick()

            assertEquals(listOf(NewEntryIntent.SelectAmountShortcut(12_000L), NewEntryIntent.OpenDatePicker), intents)
        }

    @Test
    fun `income has sponsorship sale and others and no shortcuts`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                NewEntryScreen(state = NewEntryState(date = "2026-10-26"), onBack = {}, onIntent = {})
            }
        }

        onNodeWithText("Nova entrada").assertExists()
        onNodeWithText("Patrocínio").assertExists()
        onNodeWithText("Venda").assertExists()
        onNodeWithTag(NewEntryTags.Shortcuts).assertDoesNotExist()
        onNodeWithText("Salvar entrada").assertExists()
    }

    @Test
    fun `correction shows only value and description and saves with the correction label`() = runComposeUiTest {
        val intents = mutableListOf<NewEntryIntent>()
        setContent {
            SaqzTheme {
                NewEntryScreen(
                    state = NewEntryState(
                        date = "2026-08-04",
                        editing = true,
                        amountText = "80,00",
                        description = "Aluguel da quadra",
                    ),
                    onBack = {},
                    onIntent = intents::add,
                )
            }
        }

        onNodeWithText("Corrigir lançamento").assertExists()
        onNodeWithText("Valor").assertExists()
        onNodeWithText("Descrição").assertExists()
        onNodeWithText("Categoria").assertDoesNotExist()
        onNodeWithText("Data").assertDoesNotExist()
        onNodeWithText("R$ 80").assertDoesNotExist()
        onNodeWithTag(NewEntryTags.Save).performClick()

        assertEquals(listOf<NewEntryIntent>(NewEntryIntent.Save), intents)
        onNodeWithText("Salvar correção").assertExists()
    }

    @Test
    fun `correction conflict asks to reopen the expense`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                NewEntryScreen(
                    state = NewEntryState(date = "2026-08-04", editing = true, error = GroupUiError.Conflict),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithText("Este lançamento mudou. Volte ao extrato e abra de novo.").assertExists()
    }

    @Test
    fun `correction load failure offers retry`() = runComposeUiTest {
        val intents = mutableListOf<NewEntryIntent>()
        setContent {
            SaqzTheme {
                NewEntryScreen(
                    state = NewEntryState(
                        date = "2026-08-04",
                        editing = true,
                        entryLoadFailed = true,
                        error = GroupUiError.Network,
                    ),
                    onBack = {},
                    onIntent = intents::add,
                )
            }
        }

        onNodeWithText("Tentar novamente").performClick()

        assertEquals(listOf<NewEntryIntent>(NewEntryIntent.RetryLoadEdit), intents)
    }
}
