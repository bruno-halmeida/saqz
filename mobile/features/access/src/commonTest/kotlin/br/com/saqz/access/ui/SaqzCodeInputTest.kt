package br.com.saqz.access.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O rótulo é o identificador da fileira nos testes: é ele que o leitor de tela usa, e
 * `hasSetTextAction()` some quando `enabled = false`.
 */
private const val LABEL = "Código de verificação"

@OptIn(ExperimentalTestApi::class)
class SaqzCodeInputTest {

    @Test
    fun digitsFillOneBoxAtATime() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent { SaqzTheme { SaqzCodeInput(code, { code = it }, label = LABEL) } }

        onNodeWithContentDescription(LABEL).performTextInput("1")
        onNodeWithContentDescription(LABEL).performTextInput("3")

        assertEquals("13", code)
        // Uma caixa por dígito: o valor inteiro é "13", mas cada caixa desenha um só.
        onNodeWithContentDescription(LABEL).assert(hasText("1"))
        onNodeWithContentDescription(LABEL).assert(hasText("3"))
    }

    @Test
    fun backspaceErasesThePreviousDigit() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent { SaqzTheme { SaqzCodeInput(code, { code = it }, label = LABEL) } }

        onNodeWithContentDescription(LABEL).performTextInput("13")
        onNodeWithContentDescription(LABEL).performKeyInput { pressKey(Key.Backspace) }

        assertEquals("1", code)
        // A caixa esvaziou de verdade: o dígito apagado não fica desenhado.
        assertEquals(0, onAllNodes(hasText("3")).fetchSemanticsNodes().size)
    }

    @Test
    fun pastingFourDigitsFillsEveryBoxAtOnce() = runComposeUiTest {
        var code by mutableStateOf("")
        var edits = 0
        setContent {
            SaqzTheme {
                SaqzCodeInput(
                    code,
                    {
                        code = it
                        edits++
                    },
                    label = LABEL,
                )
            }
        }

        onNodeWithContentDescription(LABEL).performTextInput("1359")

        assertEquals("1359", code)
        // Uma edição só: quem cola vindo do app de e-mail não digitou quatro vezes.
        assertEquals(1, edits)
        listOf("1", "3", "5", "9").forEach { onNodeWithContentDescription(LABEL).assert(hasText(it)) }
    }

    @Test
    fun nonDigitsAreDroppedInsteadOfTakingABox() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent { SaqzTheme { SaqzCodeInput(code, { code = it }, label = LABEL) } }

        onNodeWithContentDescription(LABEL).performTextInput("a")
        assertEquals("", code)

        // Código colado com separador: os dígitos passam, o resto some — e o "-" não
        // ocupa caixa nenhuma.
        onNodeWithContentDescription(LABEL).performTextInput("1-3")
        assertEquals("13", code)
        assertEquals(0, onAllNodes(hasText("-")).fetchSemanticsNodes().size)
    }

    @Test
    fun moreThanFourDigitsAreRefused() = runComposeUiTest {
        var code by mutableStateOf("")
        setContent { SaqzTheme { SaqzCodeInput(code, { code = it }, label = LABEL) } }

        onNodeWithContentDescription(LABEL).performTextInput("135987")

        assertEquals("1359", code)
    }

    @Test
    fun theRowIsASingleFieldForTheScreenReader() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCodeInput("13", {}, label = LABEL) } }

        // Quatro caixas, um campo só: quatro campos fariam o leitor anunciar quatro vezes.
        assertEquals(1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        onNodeWithContentDescription(LABEL).assertExists()
    }

    @Test
    fun errorIsTextAndNotOnlyColour() = runComposeUiTest {
        val message = "Código incorreto. Restam 2 tentativas."
        setContent { SaqzTheme { SaqzCodeInput("1359", {}, label = LABEL, errorText = message) } }

        // Mensagem visível e presa ao campo — cor sozinha não conta.
        onNodeWithText(message).assertExists()
        val announced = onNodeWithContentDescription(LABEL)
            .fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.Error) { null }
        assertEquals(message, announced)
        // O erro não limpa o campo: os dígitos errados continuam à vista.
        listOf("1", "3", "5", "9").forEach { onNodeWithContentDescription(LABEL).assert(hasText(it)) }
    }

    @Test
    fun disabledRefusesInput() = runComposeUiTest {
        setContent { SaqzTheme { SaqzCodeInput("13", {}, label = LABEL, enabled = false) } }

        // Travado durante o envio: não sobra ação de escrita para ninguém acionar, e o que
        // já foi digitado continua à vista.
        assertEquals(0, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        listOf("1", "3").forEach { onNodeWithContentDescription(LABEL).assert(hasText(it)) }
    }
}
