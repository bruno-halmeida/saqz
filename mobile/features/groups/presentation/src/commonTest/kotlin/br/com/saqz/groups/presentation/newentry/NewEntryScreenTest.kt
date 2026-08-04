package br.com.saqz.groups.presentation.newentry

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NewEntryScreenTest {
    @Test
    fun `other category shows custom field validation`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                NewEntryScreen(
                    state = NewEntryState(
                        date = "2026-08-04",
                        category = NewEntryCategory.Other,
                        error = GroupUiError.Validation,
                    ),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithText("O que é?").assertExists()
        onNodeWithText("Informe o que é (2 a 40 caracteres).").assertExists()
    }
}
