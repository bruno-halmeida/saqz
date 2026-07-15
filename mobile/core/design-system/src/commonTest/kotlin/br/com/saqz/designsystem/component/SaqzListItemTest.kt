package br.com.saqz.designsystem.component

import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzListItemTest {

    @Test
    fun staticHasNoClickAction() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzListItem(headline = "Título", modifier = Modifier.testTag("row"))
            }
        }
        val config = onNodeWithTag("row").fetchSemanticsNode().config
        assertNull(config.getOrElseNullable(SemanticsActions.OnClick) { null })
    }

    @Test
    fun interactiveRowIsClickable() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzListItem(headline = "Título", onClick = {}, modifier = Modifier.testTag("row"))
            }
        }
        val config = onNodeWithTag("row").fetchSemanticsNode().config
        assertNotNull(config.getOrElseNullable(SemanticsActions.OnClick) { null })
    }

    @Test
    fun rowTargetIs48Dp() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzListItem(headline = "Título", onClick = {}, modifier = Modifier.testTag("row"))
            }
        }
        onNodeWithTag("row").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun readingOrderIsStable() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzListItem(
                    headline = "H",
                    onClick = {},
                    supportingContent = { Text("S") },
                    leadingContent = { Text("L") },
                    trailingContent = { Text("T") },
                    modifier = Modifier.testTag("row"),
                )
            }
        }
        val order = texts(onNodeWithTag("row", useUnmergedTree = true).fetchSemanticsNode())
        assertEquals(listOf("L", "H", "S", "T"), order)
    }

    @Test
    fun optionalSlotsDoNotChangeOrder() = runComposeUiTest {
        setContent {
            SaqzTheme {
                // Drop leading + supporting; headline must still precede trailing.
                SaqzListItem(
                    headline = "H",
                    onClick = {},
                    trailingContent = { Text("T") },
                    modifier = Modifier.testTag("row"),
                )
            }
        }
        val order = texts(onNodeWithTag("row", useUnmergedTree = true).fetchSemanticsNode())
        assertEquals(listOf("H", "T"), order)
    }

    @Test
    fun pressFeedbackPrecedesRelease() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzListItem(headline = "Título", onClick = { clicks++ }, modifier = Modifier.testTag("row"))
            }
        }
        onNodeWithTag("row").performTouchInput { down(center) }
        waitForIdle()
        val feedback = onNodeWithTag("row").fetchSemanticsNode().config
            .getOrElseNullable(SaqzPressFeedbackKey) { null }
        assertNotNull(feedback)
        assertTrue(feedback.scale < 1f || feedback.alpha < 1f, "press feedback before release")
        assertEquals(0, clicks)
        onNodeWithTag("row").performTouchInput { up() }
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun nestedContentActivatesOnce() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzListItem(
                    headline = "Título",
                    onClick = { clicks++ },
                    trailingContent = { Text("T", modifier = Modifier.testTag("nested")) },
                    modifier = Modifier.testTag("row"),
                )
            }
        }
        // Tapping a nested slot must fire the row callback exactly once (no double click).
        onNodeWithTag("nested", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    private fun texts(node: SemanticsNode): List<String> {
        val own = node.config.getOrElseNullable(SemanticsProperties.Text) { null }
            ?.map { it.text } ?: emptyList()
        return own + node.children.flatMap { texts(it) }
    }
}
