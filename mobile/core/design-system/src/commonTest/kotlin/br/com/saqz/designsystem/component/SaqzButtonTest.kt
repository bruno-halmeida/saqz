package br.com.saqz.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties as CoreSemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.width
import br.com.saqz.designsystem.theme.SaqzAccessibilityPreferences
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzButtonTest {
    private val tokens = SaqzColorTokens.Light

    @Test
    fun fourVariantsUseExpectedTokens() {
        val primary = tokens.buttonColors(SaqzButtonVariant.Primary)
        assertEquals(tokens.primary, primary.container)
        assertEquals(tokens.onPrimary, primary.content)

        val secondary = tokens.buttonColors(SaqzButtonVariant.Secondary)
        assertEquals(tokens.surface, secondary.container)
        assertEquals(tokens.primary, secondary.content)
        assertEquals(tokens.controlBorder, secondary.border)

        val ghost = tokens.buttonColors(SaqzButtonVariant.Ghost)
        assertEquals(Color.Transparent, ghost.container)
        assertEquals(tokens.primary, ghost.content)

        val destructive = tokens.buttonColors(SaqzButtonVariant.Destructive)
        assertEquals(tokens.errorForeground, destructive.container)
        assertEquals(tokens.onPrimary, destructive.content)

        // accent is never an action surface.
        SaqzButtonVariant.entries.forEach { variant ->
            val c = tokens.buttonColors(variant)
            assertTrue(c.container != tokens.accent, "$variant must not use accent")
            assertTrue(c.content != tokens.accent, "$variant must not use accent")
        }
    }

    @Test
    fun focusIsThreeToOne() {
        // The focus indicator paints in primary; it must reach 3:1 against the
        // adjacent surfaces it is drawn over.
        assertAtLeast(3.0, contrast(tokens.primary, tokens.background))
        assertAtLeast(3.0, contrast(tokens.primary, tokens.surface))
    }

    @Test
    fun pressStartsBeforeRelease() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzButton("Salvar", onClick = { clicks++ }, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").performTouchInput { down(center) }
        waitForIdle()
        val feedback = pressFeedback("btn")
        // Feedback is present while the finger is still down; no activation yet.
        assertTrue(feedback.scale < 1f || feedback.alpha < 1f, "press feedback before release")
        assertEquals(0, clicks)
        onNodeWithTag("btn").performTouchInput { up() }
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun pressScaleIs095() {
        assertEquals(0.95f, saqzPressScale(pressed = true, motion = SaqzMotionPolicy.Normal))
        assertEquals(1f, saqzPressScale(pressed = false, motion = SaqzMotionPolicy.Normal))
    }

    @Test
    fun pressDurationIs120ms() = runComposeUiTest {
        var duration = -1
        setContent { SaqzTheme { duration = SaqzTheme.motion.pressDurationMillis } }
        // The button drives its press tween from exactly this value.
        assertEquals(120, duration)
    }

    @Test
    fun releaseActivatesOnce() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme { SaqzButton("Salvar", onClick = { clicks++ }, modifier = Modifier.testTag("btn")) }
        }
        onNodeWithTag("btn").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledHasSemantics() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzButton("Salvar", onClick = {}, enabled = false, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").assertIsNotEnabled()
    }

    @Test
    fun disabledDoesNotActivate() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzButton("Salvar", onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").performClick()
        waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun loadingIsBusy() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzButton("Salvar", onClick = { clicks++ }, loading = true, modifier = Modifier.testTag("btn"))
            }
        }
        val node = onNodeWithTag("btn").fetchSemanticsNode()
        val state = node.config.getOrElseNullable(CoreSemanticsProperties.StateDescription) { null }
        assertEquals("Carregando", state)
        onNodeWithTag("btn").assertIsNotEnabled()
        onNodeWithTag("btn").performClick()
        waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun loadingKeepsName() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzButton("Salvar", onClick = {}, loading = true, modifier = Modifier.testTag("btn")) }
        }
        // The label stays in the tree (name preserved) even while the spinner shows.
        onNodeWithText("Salvar").assertExists()
    }

    @Test
    fun loadingKeepsWidth() = runComposeUiTest {
        setContent {
            SaqzTheme {
                Column {
                    SaqzButton("Salvar", onClick = {}, loading = false, modifier = Modifier.testTag("idle"))
                    SaqzButton("Salvar", onClick = {}, loading = true, modifier = Modifier.testTag("busy"))
                }
            }
        }
        val idle = onNodeWithTag("idle").getUnclippedBoundsInRoot().width
        val busy = onNodeWithTag("busy").getUnclippedBoundsInRoot().width
        assertEquals(idle, busy)
    }

    @Test
    fun reducedMotionKeepsFeedback() = runComposeUiTest {
        setContent {
            SaqzTheme(preferences = SaqzAccessibilityPreferences(reduceMotion = true)) {
                SaqzButton("Salvar", onClick = {}, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").performTouchInput { down(center) }
        waitForIdle()
        val feedback = pressFeedback("btn")
        // Reduced motion drops spatial scale but must keep opacity feedback.
        assertEquals(1f, feedback.scale)
        assertTrue(feedback.alpha < 1f, "opacity feedback kept under reduced motion")
        onNodeWithTag("btn").performTouchInput { up() }
    }

    private fun ComposeUiTest.pressFeedback(tag: String): SaqzPressFeedback {
        val value = onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrElseNullable(SaqzPressFeedbackKey) { null }
        assertNotNull(value, "press feedback semantics present")
        return value
    }

    private fun assertAtLeast(minimum: Double, actual: Double) =
        assertTrue(actual >= minimum, "expected >= $minimum but was $actual")

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
