package br.com.saqz.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import br.com.saqz.designsystem.theme.SaqzAccessibilityPreferences
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzBottomNavTest {

    private fun items(
        onStart: () -> Unit = {},
        onComponents: () -> Unit = {},
        startSelected: Boolean = true,
    ) = listOf(
        SaqzBottomNavItem("Início", selected = startSelected, onClick = onStart, icon = { Text("•") }),
        SaqzBottomNavItem("Componentes", selected = !startSelected, onClick = onComponents, icon = { Text("•") }),
    )

    private fun assertNear(expected: Dp, actual: Dp) =
        assertTrue(
            kotlin.math.abs(expected.value - actual.value) < 0.5f,
            "expected ~$expected but was $actual",
        )

    @Test
    fun baseHeightIs56Dp() = runComposeUiTest {
        setContent { SaqzTheme { SaqzBottomNav(items = items()) } }
        assertNear(56.dp, onNodeWithTag(SaqzBottomNavBarTag).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun bottomInsetIsAdded() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzBottomNav(items = items(), contentWindowInsets = WindowInsets(bottom = 20.dp))
            }
        }
        // 56dp bar + 20dp bottom inset, added exactly once.
        assertNear(76.dp, onNodeWithTag(SaqzBottomNavBarTag).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun eachItemIs48Dp() = runComposeUiTest {
        setContent { SaqzTheme { SaqzBottomNav(items = items()) } }
        listOf(0, 1).forEach { index ->
            val bounds = onNodeWithTag(saqzBottomNavItemTag(index)).getUnclippedBoundsInRoot()
            assertTrue(bounds.width >= 48.dp, "item $index width ${bounds.width} must be >= 48dp")
            assertTrue(bounds.height >= 48.dp, "item $index height ${bounds.height} must be >= 48dp")
        }
    }

    @Test
    fun selectedStateIsAnnounced() = runComposeUiTest {
        setContent { SaqzTheme { SaqzBottomNav(items = items(startSelected = true)) } }
        onNodeWithTag(saqzBottomNavItemTag(0)).assertIsSelected()
        onNodeWithTag(saqzBottomNavItemTag(1)).assertIsNotSelected()
    }

    @Test
    fun selectionHasNonColorSignal() = runComposeUiTest {
        setContent { SaqzTheme { SaqzBottomNav(items = items(startSelected = true)) } }
        // The indicator bar is a shape present only for the selected item — not colour alone.
        // It lives under the item's merged clickable node, so read the unmerged tree.
        onNodeWithTag(saqzBottomNavIndicatorTag(0), useUnmergedTree = true).assertExists()
        onNodeWithTag(saqzBottomNavIndicatorTag(1), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun readingOrderMatchesList() = runComposeUiTest {
        setContent { SaqzTheme { SaqzBottomNav(items = items()) } }
        val first = onNodeWithTag(saqzBottomNavItemTag(0)).getUnclippedBoundsInRoot().left
        val second = onNodeWithTag(saqzBottomNavItemTag(1)).getUnclippedBoundsInRoot().left
        assertTrue(first < second, "items must render in list order")
    }

    @Test
    fun clickActivatesOnce() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme { SaqzBottomNav(items = items(onComponents = { clicks++ }, startSelected = true)) }
        }
        onNodeWithTag(saqzBottomNavItemTag(1)).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun reselectionActivatesOnce() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme { SaqzBottomNav(items = items(onStart = { clicks++ }, startSelected = true)) }
        }
        // Tapping the already-selected item fires once, never twice.
        onNodeWithTag(saqzBottomNavItemTag(0)).performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun hairlineSurvivesBothChromes() {
        // Translucent chrome.
        runComposeUiTest {
            setContent {
                SaqzTheme(preferences = SaqzAccessibilityPreferences(reduceTransparency = false)) {
                    SaqzBottomNav(items = items())
                }
            }
            assertNear(1.dp, onNodeWithTag(SaqzBottomNavHairlineTag).getUnclippedBoundsInRoot().height)
        }
        // Opaque chrome.
        runComposeUiTest {
            setContent {
                SaqzTheme(preferences = SaqzAccessibilityPreferences(reduceTransparency = true)) {
                    SaqzBottomNav(items = items())
                }
            }
            assertNear(1.dp, onNodeWithTag(SaqzBottomNavHairlineTag).getUnclippedBoundsInRoot().height)
        }
    }
}
