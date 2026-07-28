package br.com.saqz.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzInputTest {

    @Test
    fun valueIsControlled() = runComposeUiTest {
        var callbackFired = false
        setContent {
            SaqzTheme {
                // Value is pinned and the callback is ignored: a controlled field with
                // no internal copy must keep showing the pinned value after typing.
                SaqzInput(
                    value = TextFieldValue("fixo"),
                    onValueChange = { callbackFired = true },
                    label = "Nome",
                    modifier = Modifier.testTag("input"),
                )
            }
        }
        onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput("x")
        waitForIdle()
        assertTrue(callbackFired, "onValueChange is wired")
        onNode(hasSetTextAction(), useUnmergedTree = true).assertTextEquals("fixo")
    }

    @Test
    fun labelIsAssociated() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInput(TextFieldValue(""), {}, label = "E-mail", modifier = Modifier.testTag("input"))
            }
        }
        // Label and field share one merged accessible node.
        onNodeWithTag("input").assert(hasText("E-mail"))
    }

    @Test
    fun helperIsAssociated() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInput(
                    TextFieldValue(""), {}, label = "E-mail",
                    helperText = "Use seu e-mail principal", modifier = Modifier.testTag("input"),
                )
            }
        }
        onNodeWithTag("input").assert(hasText("Use seu e-mail principal"))
    }

    @Test
    fun errorReplacesHelper() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInput(
                    TextFieldValue(""), {}, label = "E-mail",
                    helperText = "Use seu e-mail principal", errorText = "E-mail inválido",
                    modifier = Modifier.testTag("input"),
                )
            }
        }
        onNodeWithTag("input").assert(hasText("E-mail inválido"))
        onNodeWithText("Use seu e-mail principal").assertDoesNotExist()
    }

    @Test
    fun errorIsAnnounced() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInput(
                    TextFieldValue(""), {}, label = "E-mail", errorText = "E-mail inválido",
                    modifier = Modifier.testTag("input"),
                )
            }
        }
        val error = onNode(hasSetTextAction(), useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrElseNullable(SemanticsProperties.Error) { null }
        assertEquals("E-mail inválido", error)
    }

    @Test
    fun emailUsesEmailKeyboard() {
        assertEquals(KeyboardType.Email, keyboardTypeFor(SaqzInputKind.Email))
        assertEquals(KeyboardType.Text, keyboardTypeFor(SaqzInputKind.Text))
        assertEquals(KeyboardType.Password, keyboardTypeFor(SaqzInputKind.Password))
    }

    @Test
    fun passwordStartsObscured() {
        val obscured = visualTransformationFor(SaqzInputKind.Password, revealed = false)
        assertTrue(obscured is PasswordVisualTransformation)
        val masked = obscured.filter(AnnotatedString("segredo")).text.text
        assertEquals("•••••••", masked)
        // Text/email kinds are never masked; revealed password is plain.
        assertEquals(VisualTransformation.None, visualTransformationFor(SaqzInputKind.Text, revealed = false))
        assertEquals(VisualTransformation.None, visualTransformationFor(SaqzInputKind.Password, revealed = true))
    }

    @Test
    fun togglePreservesValue() = runComposeUiTest {
        val changes = mutableListOf<TextFieldValue>()
        setContent {
            SaqzTheme {
                SaqzInput(
                    value = TextFieldValue("segredo", selection = TextRange(2, 5)),
                    onValueChange = { changes += it },
                    label = "Senha", kind = SaqzInputKind.Password, modifier = Modifier.testTag("input"),
                )
            }
        }
        onNodeWithContentDescription("Mostrar senha").performClick()
        waitForIdle()
        // Toggling reveal must not emit any value change.
        assertTrue(changes.isEmpty(), "toggle preserves value without emitting change")
    }

    @Test
    fun togglePreservesSelection() = runComposeUiTest {
        val changes = mutableListOf<TextFieldValue>()
        setContent {
            SaqzTheme {
                SaqzInput(
                    value = TextFieldValue("segredo", selection = TextRange(2, 5)),
                    onValueChange = { changes += it },
                    label = "Senha", kind = SaqzInputKind.Password, modifier = Modifier.testTag("input"),
                )
            }
        }
        onNodeWithContentDescription("Mostrar senha").performClick()
        waitForIdle()
        // No emitted change means the caller's selection TextRange(2,5) is untouched.
        assertTrue(changes.none { it.selection != TextRange(2, 5) })
    }

    @Test
    fun togglePreservesFocus() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInput(
                    value = TextFieldValue("segredo"),
                    onValueChange = {},
                    label = "Senha", kind = SaqzInputKind.Password,
                    modifier = Modifier.testTag("input"),
                )
            }
        }
        // Focus the field, then flip the reveal; the field must keep focus.
        onNode(hasSetTextAction(), useUnmergedTree = true).performClick()
        waitForIdle()
        onNode(hasSetTextAction(), useUnmergedTree = true).assertIsFocused()
        onNodeWithContentDescription("Mostrar senha").performClick()
        waitForIdle()
        onNode(hasSetTextAction(), useUnmergedTree = true).assertIsFocused()
    }

    // O defeito do VUL-63: só o placeholder recuava quando `enabled = false`, e campo
    // travado quase sempre TEM valor — então o estado normal do desabilitado era idêntico
    // ao editável.
    @Test
    fun disabledFadesLabelAndValue() {
        val tokens = SaqzColorTokens.Light
        assertEquals(tokens.textPrimary, tokens.inputContent(enabled = true))
        assertEquals(tokens.disabledForeground, tokens.inputContent(enabled = false))
    }

    @Test
    fun disabledContentIsVisiblyApartFromEnabled() {
        val tokens = SaqzColorTokens.Light
        val disabled = contrast(tokens.inputContent(enabled = false), tokens.surface)
        val enabled = contrast(tokens.inputContent(enabled = true), tokens.surface)
        // Piso de 3:1 sobre o fundo: a isenção 1.4.3 cobre componente inativo, mas o texto
        // travado ainda precisa ser lido. Teto de 4.5:1 porque o recuo é o próprio sinal —
        // se o desabilitado empatar com o habilitado, o bug volta.
        assertTrue(disabled >= 3.0, "texto desabilitado ficou em ${disabled.format()}:1 sobre o fundo")
        assertTrue(disabled < 4.5, "texto desabilitado em ${disabled.format()}:1 não recua o bastante")
        assertTrue(
            enabled - disabled > 5.0,
            "habilitado (${enabled.format()}:1) e desabilitado (${disabled.format()}:1) estão perto demais",
        )
    }

    // O P2 do review do #50: a linha de 1px tinha guarda de `enabled`, o halo de 3dp não,
    // então travado + erro mantinha o glow vermelho em volta. A guarda mora na origem —
    // linha e halo saem os dois do acento.
    @Test
    fun disabledWearsNoAccent() {
        val tokens = SaqzColorTokens.Light
        assertNull(tokens.inputAccent(enabled = false, wrong = true, focused = false))
        assertNull(tokens.inputAccent(enabled = false, wrong = false, focused = true))
        assertNull(tokens.inputAccent(enabled = false, wrong = true, focused = true))
    }

    @Test
    fun enabledStillWearsErrorAndFocus() {
        val tokens = SaqzColorTokens.Light
        assertEquals(
            tokens.errorForeground,
            tokens.inputAccent(enabled = true, wrong = true, focused = true),
            "erro tem precedência sobre foco no campo ativo",
        )
        assertEquals(tokens.primary, tokens.inputAccent(enabled = true, wrong = false, focused = true))
        assertNull(tokens.inputAccent(enabled = true, wrong = false, focused = false))
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: Color) =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun Double.format() = (this * 100).toInt() / 100.0

    @Test
    fun toggleTargetIs48Dp() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInput(
                    TextFieldValue(""), {}, label = "Senha", kind = SaqzInputKind.Password,
                    modifier = Modifier.testTag("input"),
                )
            }
        }
        onNodeWithContentDescription("Mostrar senha").assertHeightIsAtLeast(48.dp)
        onNodeWithContentDescription("Mostrar senha").assertWidthIsAtLeast(48.dp)
    }
}
