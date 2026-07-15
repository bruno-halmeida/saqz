package br.com.saqz.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzDialogTest {

    // Back-press is owned by the platform via DialogProperties; the two dismiss channels
    // stay independent and are never collapsed into one flag.
    @Test
    fun backDismissesWhenEnabled() {
        assertTrue(saqzDialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false).dismissOnBackPress)
    }

    @Test
    fun backIgnoredWhenDisabled() {
        assertFalse(saqzDialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false).dismissOnBackPress)
    }

    @Test
    fun backgroundIsBlocked() = runComposeUiTest {
        var closes = 0
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = { closes++ },
                    dismissOnClickOutside = false,
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) { Text("Corpo") }
            }
        }
        // The scrim covers the window and absorbs the tap: nothing leaks to the background
        // and the dialog stays open. Tap near the top edge, clear of the centred card.
        onNodeWithTag(SaqzModalScrimTag).assertExists()
        onNodeWithTag(SaqzModalScrimTag).performTouchInput { click(topCenter) }
        waitForIdle()
        assertEquals(0, closes)
        onNodeWithTag(SaqzModalTitleTag).assertIsDisplayed()
    }

    @Test
    fun titleIsAnnouncedFirst() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = {},
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) { Text("Corpo") }
            }
        }
        val titleTop = onNodeWithTag(SaqzModalTitleTag).getUnclippedBoundsInRoot().top
        val footerTop = onNodeWithTag(SaqzModalFooterTag).getUnclippedBoundsInRoot().top
        // Reading order top-to-bottom: title precedes the footer/primary action.
        assertTrue(titleTop < footerTop, "title must precede footer in reading order")
    }

    @Test
    fun primaryActionIsAnnounced() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = {},
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) { Text("Corpo") }
            }
        }
        onNodeWithText("Confirmar").assertExists()
    }

    @Test
    fun closeIsAccessible() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = {},
                    showCloseAction = true,
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) { Text("Corpo") }
            }
        }
        onNodeWithContentDescription("Fechar").assertExists().assertHasClickAction()
    }

    @Test
    fun outsideDismissesWhenEnabled() = runComposeUiTest {
        var closes = 0
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = { closes++ },
                    dismissOnClickOutside = true,
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) { Text("Corpo") }
            }
        }
        onNodeWithTag(SaqzModalScrimTag).performTouchInput { click(topCenter) }
        waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun outsideIgnoredWhenDisabled() = runComposeUiTest {
        var closes = 0
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = { closes++ },
                    dismissOnClickOutside = false,
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) { Text("Corpo") }
            }
        }
        onNodeWithTag(SaqzModalScrimTag).performTouchInput { click(topCenter) }
        waitForIdle()
        assertEquals(0, closes)
    }

    @Test
    fun explicitCloseAlwaysWorks() = runComposeUiTest {
        var closes = 0
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = { closes++ },
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    showCloseAction = true,
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) { Text("Corpo") }
            }
        }
        // Close stays reachable even with both dismiss channels off.
        onNodeWithContentDescription("Fechar").performClick()
        waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun longContentScrolls() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = {},
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) {
                    Column {
                        repeat(60) { i ->
                            Text("Linha $i", modifier = Modifier.padding(12.dp).testTag("row-$i"))
                        }
                    }
                }
            }
        }
        // The last row is off-screen until the body scrolls to it.
        onNodeWithTag("row-59").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun actionsRemainVisible() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzDialog(
                    title = "Confirmar ação",
                    onCloseRequest = {},
                    primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                ) {
                    Column {
                        repeat(60) { i ->
                            Text("Linha $i", modifier = Modifier.padding(12.dp).testTag("row-$i"))
                        }
                    }
                }
            }
        }
        // Footer is fixed: scrolling the body keeps the primary action on screen.
        onNodeWithTag("row-59").performScrollTo()
        onNodeWithTag(SaqzModalFooterTag).assertIsDisplayed()
    }
}
