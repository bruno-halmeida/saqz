package br.com.saqz.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzBottomSheetTest {

    private fun sheet(
        onCloseRequest: () -> Unit = {},
        dismissOnBackPress: Boolean = false,
        dismissOnClickOutside: Boolean = false,
        showCloseAction: Boolean = true,
        content: @Composable () -> Unit = { Text("Corpo") },
    ): @Composable () -> Unit = {
        SaqzTheme {
            SaqzBottomSheet(
                title = "Escolher opção",
                onCloseRequest = onCloseRequest,
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
                showCloseAction = showCloseAction,
                primaryAction = { SaqzButton("Confirmar", onClick = {}) },
                content = content,
            )
        }
    }

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
        setContent(sheet(onCloseRequest = { closes++ }, dismissOnClickOutside = false))
        onNodeWithTag(SaqzModalScrimTag).assertExists()
        onNodeWithTag(SaqzModalScrimTag).performTouchInput { click(topCenter) }
        waitForIdle()
        assertEquals(0, closes)
        onNodeWithTag(SaqzModalTitleTag).assertIsDisplayed()
    }

    @Test
    fun titleIsAnnouncedFirst() = runComposeUiTest {
        setContent(sheet())
        val titleTop = onNodeWithTag(SaqzModalTitleTag).getUnclippedBoundsInRoot().top
        val footerTop = onNodeWithTag(SaqzModalFooterTag).getUnclippedBoundsInRoot().top
        assertTrue(titleTop < footerTop, "title must precede footer in reading order")
    }

    @Test
    fun primaryActionIsAnnounced() = runComposeUiTest {
        setContent(sheet())
        onNodeWithText("Confirmar").assertExists()
    }

    @Test
    fun closeIsAccessible() = runComposeUiTest {
        setContent(sheet(showCloseAction = true))
        onNodeWithContentDescription("Fechar").assertExists().assertHasClickAction()
    }

    @Test
    fun outsideDismissesWhenEnabled() = runComposeUiTest {
        var closes = 0
        setContent(sheet(onCloseRequest = { closes++ }, dismissOnClickOutside = true))
        onNodeWithTag(SaqzModalScrimTag).performTouchInput { click(topCenter) }
        waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun outsideIgnoredWhenDisabled() = runComposeUiTest {
        var closes = 0
        setContent(sheet(onCloseRequest = { closes++ }, dismissOnClickOutside = false))
        onNodeWithTag(SaqzModalScrimTag).performTouchInput { click(topCenter) }
        waitForIdle()
        assertEquals(0, closes)
    }

    @Test
    fun explicitCloseAlwaysWorks() = runComposeUiTest {
        var closes = 0
        setContent(sheet(onCloseRequest = { closes++ }, showCloseAction = true))
        onNodeWithContentDescription("Fechar").performClick()
        waitForIdle()
        assertEquals(1, closes)
    }

    @Test
    fun longContentScrolls() = runComposeUiTest {
        setContent(
            sheet(content = {
                Column {
                    repeat(60) { i ->
                        Text("Linha $i", modifier = Modifier.padding(12.dp).testTag("row-$i"))
                    }
                }
            }),
        )
        onNodeWithTag("row-59").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noDragDismissSemantics() = runComposeUiTest {
        var closes = 0
        setContent(sheet(onCloseRequest = { closes++ }))
        // No drag handle, no drag-to-dismiss: swiping the sheet down leaves it open.
        onNodeWithTag(SaqzModalTitleTag).performTouchInput { swipeDown() }
        waitForIdle()
        assertEquals(0, closes)
        onNodeWithTag(SaqzModalTitleTag).assertIsDisplayed()
    }

    @Test
    fun bottomAnchoring() = runComposeUiTest {
        setContent(sheet())
        // Measure the footer against the modal container (scrim) in the same root: a
        // bottom-anchored sheet keeps its footer in the bottom quarter of the container;
        // a centred dialog with short content would place it near the middle.
        val scrim = onNodeWithTag(SaqzModalScrimTag).getUnclippedBoundsInRoot()
        val footerBottom = onNodeWithTag(SaqzModalFooterTag).getUnclippedBoundsInRoot().bottom
        assertTrue(
            scrim.bottom - footerBottom < scrim.height / 4,
            "sheet footer must be anchored near the bottom of the container",
        )
    }
}
