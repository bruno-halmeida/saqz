package br.com.saqz.designsystem.component

import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzCardTest {

    @Test
    fun staticHasNoClickAction() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzCard(modifier = Modifier.testTag("card")) { Text("Estático") }
            }
        }
        val config = onNodeWithTag("card").fetchSemanticsNode().config
        assertNull(config.getOrElseNullable(SemanticsActions.OnClick) { null })
        assertNull(config.getOrElseNullable(SemanticsProperties.Role) { null })
    }

    @Test
    fun staticHasNoPressIndication() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzCard(modifier = Modifier.testTag("card")) { Text("Estático") }
            }
        }
        // A static card never publishes press feedback.
        val feedback = onNodeWithTag("card").fetchSemanticsNode().config
            .getOrElseNullable(SaqzPressFeedbackKey) { null }
        assertNull(feedback)
    }

    @Test
    fun interactiveHasClickRole() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzCard(onClick = {}, modifier = Modifier.testTag("card")) { Text("Ação") }
            }
        }
        val config = onNodeWithTag("card").fetchSemanticsNode().config
        assertNotNull(config.getOrElseNullable(SemanticsActions.OnClick) { null })
        assertEquals(Role.Button, config.getOrElseNullable(SemanticsProperties.Role) { null })
    }

    @Test
    fun interactiveTargetIs48Dp() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzCard(onClick = {}, modifier = Modifier.testTag("card")) { Text("Ação") }
            }
        }
        onNodeWithTag("card").assertHeightIsAtLeast(48.dp)
        onNodeWithTag("card").assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun feedbackStartsOnPress() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzCard(onClick = { clicks++ }, modifier = Modifier.testTag("card")) { Text("Ação") }
            }
        }
        onNodeWithTag("card").performTouchInput { down(center) }
        waitForIdle()
        val feedback = pressFeedback("card")
        assertTrue(feedback.scale < 1f || feedback.alpha < 1f, "press feedback before release")
        assertEquals(0, clicks)
        onNodeWithTag("card").performTouchInput { up() }
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun releaseActivatesOnce() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzCard(onClick = { clicks++ }, modifier = Modifier.testTag("card")) { Text("Ação") }
            }
        }
        onNodeWithTag("card").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    private fun ComposeUiTest.pressFeedback(tag: String): SaqzPressFeedback {
        val value = onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrElseNullable(SaqzPressFeedbackKey) { null }
        assertNotNull(value, "press feedback semantics present")
        return value
    }
}
