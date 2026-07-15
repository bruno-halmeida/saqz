package br.com.saqz.designsystem.component

import androidx.compose.ui.Modifier
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
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
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
