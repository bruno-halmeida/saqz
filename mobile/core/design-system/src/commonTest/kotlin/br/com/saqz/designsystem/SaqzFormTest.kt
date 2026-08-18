package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A forma que quebra é a do formulário de cartão: validade e CVV dividem um `Row`, cada um
 * com `weight(1f)`, entre um campo em cima e outro embaixo. É por causa dela que o
 * [SaqzFormScope] usa `FocusDirection.Next` e não `Down` — e é ela que os testes montam.
 */
@OptIn(ExperimentalTestApi::class)
class SaqzFormTest {

    /**
     * O "Próximo" da validade tem de cair no CVV, o vizinho de linha. `FocusDirection.Down`
     * é busca geométrica: acha o campo do titular, que está literalmente abaixo, e pula o
     * CVV — o usuário digita o mês e o teclado o joga para o fim do formulário.
     */
    @Test
    fun nextGoesSidewaysInsideTheRow() = runComposeUiTest {
        setContent { SaqzTheme { CardShapedForm() } }

        field("validade").performClick()
        waitForIdle()
        field("validade").assertIsFocused()

        field("validade").performImeAction()
        waitForIdle()

        field("cvv").assertIsFocused()
        field("titular").assertIsNotFocused()
    }

    @Test
    fun doneClearsTheFocus() = runComposeUiTest {
        setContent { SaqzTheme { CardShapedForm() } }

        field("titular").performClick()
        waitForIdle()
        field("titular").assertIsFocused()

        field("titular").performImeAction()
        waitForIdle()

        field("titular").assertIsNotFocused()
    }

    @Test
    fun doneOnTheLastFieldSubmitsTheForm() = runComposeUiTest {
        var submits = 0
        setContent { SaqzTheme { CardShapedForm(onSubmit = { submits += 1 }) } }

        field("titular").performClick()
        waitForIdle()
        field("titular").performImeAction()
        waitForIdle()

        assertEquals(1, submits)
        field("titular").assertIsNotFocused()
    }

    /**
     * O par `imeAction` + `keyboardActions` viaja junto num [SaqzFormIme]; se o [SaqzInput]
     * ligasse só as ações e deixasse o `imeAction` cair no `Default`, o teclado mostraria a
     * tecla de quebra de linha e nenhuma das ações acima seria disparada por ninguém.
     */
    @Test
    fun theDeclaredImeReachesTheField() = runComposeUiTest {
        setContent { SaqzTheme { CardShapedForm() } }

        assertEquals(ImeAction.Next, field("numero").imeAction())
        assertEquals(ImeAction.Next, field("validade").imeAction())
        assertEquals(ImeAction.Next, field("cvv").imeAction())
        assertEquals(ImeAction.Done, field("titular").imeAction())
    }

    // Cada campo carrega um valor distinto porque a moldura do SaqzInput funde rótulo e campo
    // num nó só: sem isso não há como apontar para o `BasicTextField` de dentro.
    private fun ComposeUiTest.field(value: String) =
        onNode(hasSetTextAction() and hasText(value), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.imeAction() =
        fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.ImeAction) { null }
}

@Composable
private fun CardShapedForm(onSubmit: (() -> Unit)? = null) = SaqzForm(onSubmit = onSubmit) {
    SaqzInput("numero", {}, label = "Número do cartão", ime = imeNext())
    Row {
        SaqzInput("validade", {}, label = "Validade", modifier = Modifier.weight(1f), ime = imeNext())
        SaqzInput("cvv", {}, label = "CVV", modifier = Modifier.weight(1f), ime = imeNext())
    }
    SaqzInput("titular", {}, label = "Nome do titular", ime = imeDone())
}
