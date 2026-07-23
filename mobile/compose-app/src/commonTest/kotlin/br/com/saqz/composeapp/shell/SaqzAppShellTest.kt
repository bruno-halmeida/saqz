package br.com.saqz.composeapp.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import br.com.saqz.composeapp.SaqzAppEnvironment
import br.com.saqz.composeapp.catalog.saqzCatalogVariantTag
import br.com.saqz.composeapp.navigation.SaqzDestination
import br.com.saqz.composeapp.navigation.saqzLocalNavConfiguration
import br.com.saqz.designsystem.theme.SaqzMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzAppShellTest {

    private val navHeight = SaqzMetrics.Default.bottomNavHeight.value

    private fun bottom(test: androidx.compose.ui.test.ComposeUiTest, tag: String) =
        test.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom.value

    private fun top(test: androidx.compose.ui.test.ComposeUiTest, tag: String) =
        test.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value

    // The catalog demo also renders a BottomNav and menu fixtures with the same labels, so
    // nav interactions are scoped to the shell's own bottom nav subtree.
    private fun androidx.compose.ui.test.ComposeUiTest.shellTab(label: String) =
        onNode(hasAnyAncestor(hasTestTag(SaqzShellNavTag)) and hasText(label))

    @Test
    fun bottomInsetAppliedOnce() = runComposeUiTest {
        setContent { SaqzAppShell() }
        // The bottom nav is the single bottom-inset consumer: exactly one bar of height
        // 56dp + 1dp hairline (safe-area inset is 0 on the simulator, applied once).
        val bounds = onNodeWithTag(SaqzShellNavTag).getUnclippedBoundsInRoot()
        val height = (bounds.bottom - bounds.top).value
        assertTrue(height in (navHeight)..(navHeight + 3f), "nav height must be 56dp + hairline once, was $height")
    }

    @Test
    fun imeKeepsFocusedControlVisible() = runComposeUiTest {
        setContent { SaqzAppShell() }
        onNodeWithText("Componentes").performClick()
        waitForIdle()
        val input = onNodeWithTag(saqzCatalogVariantTag("Input-Text"))
        input.performScrollTo()
        input.performClick()
        waitForIdle()
        // The focused control stays inside the content slot, above the bottom chrome.
        input.assertIsDisplayed()
        assertTrue(
            input.getUnclippedBoundsInRoot().bottom.value <= bottom(this, SaqzShellSlotTag) + 1f,
            "focused control must stay above the bottom chrome",
        )
    }

    @Test
    fun landscapeKeepsActionsReachable() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(width = 720.dp, height = 360.dp)) { SaqzAppShell() }
        }
        // In a short landscape viewport both nav actions remain within bounds and reachable.
        onNodeWithText("Início").assertIsDisplayed()
        onNodeWithText("Componentes").assertIsDisplayed()
        onNodeWithText("Componentes").performClick()
        waitForIdle()
        onNodeWithTag(saqzCatalogVariantTag("Button-Primary")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fontScale2KeepsActionsReachable() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                SaqzAppShell()
            }
        }
        onNodeWithText("Componentes").performClick()
        waitForIdle()
        val action = onNodeWithTag(saqzCatalogVariantTag("Button-Primary"))
        action.performScrollTo().assertIsDisplayed()
        val rootRight = onNodeWithTag(SaqzShellContentTag).getUnclippedBoundsInRoot().right.value
        assertTrue(
            action.getUnclippedBoundsInRoot().right.value <= rootRight + 1f,
            "action must stay within the viewport at fontScale 2.0",
        )
    }

    @Test
    fun contentScrollsUnderChrome() = runComposeUiTest {
        setContent { SaqzAppShell() }
        // The content viewport spans the full height, extending under the bottom chrome band.
        assertTrue(
            bottom(this, SaqzShellContentTag) > top(this, SaqzShellNavTag) + 1f,
            "content viewport must extend under the bottom chrome",
        )
    }

    @Test
    fun finalPaddingClearsChrome() = runComposeUiTest {
        setContent { SaqzAppShell() }
        // The content slot reserves the nav height, so its actions end at/above the chrome top.
        assertTrue(
            bottom(this, SaqzShellSlotTag) <= top(this, SaqzShellNavTag) + 1f,
            "content slot must clear the bottom chrome",
        )
    }

    // Note: StateRestorationTester.emulateSaveAndRestore() throws NotImplemented on the Skiko
    // (iosSimulator) gate, so full process recreation is asserted on Android (T35:
    // rotationKeepsCatalog / rotationKeepsOverlayClosed). Here the shell's bottom-nav
    // save/restore round-trip is the iOS-sim-observable proxy for the same guarantees.

    @Test
    fun selectedDestinationRestores() = runComposeUiTest {
        lateinit var backStack: NavBackStack<NavKey>
        setContent {
            backStack = rememberNavBackStack(saqzLocalNavConfiguration, SaqzDestination.Home)
            SaqzAppShell(backStack = backStack)
        }
        waitForIdle()
        shellTab("Componentes").performClick()
        waitForIdle()
        shellTab("Início").performClick()
        waitForIdle()
        shellTab("Componentes").performClick()
        waitForIdle()
        // Re-selecting via the bottom nav restores the saved Catalog destination.
        assertEquals(
            SaqzDestination.Catalog,
            backStack.last(),
            "selected destination must be restored on reselection",
        )
    }

    @Test
    fun closedOverlayStaysClosed() = runComposeUiTest {
        setContent { SaqzAppShell() }
        waitForIdle()
        shellTab("Componentes").performClick()
        waitForIdle()
        // Open then close the catalog overlay.
        onNodeWithTag(saqzCatalogVariantTag("Dialog")).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("saqz-modal-title").assertIsDisplayed()
        onNodeWithText("ok").performClick()
        waitForIdle()
        // Leave to Home and return: the saved/restored Catalog must not reopen the overlay.
        shellTab("Início").performClick()
        waitForIdle()
        shellTab("Componentes").performClick()
        waitForIdle()
        onNodeWithTag("saqz-modal-title").assertDoesNotExist()
    }

    @Test
    fun readingOrderIsContentThenNav() = runComposeUiTest {
        setContent { SaqzAppShell() }
        // Content precedes the bottom nav top-to-bottom.
        assertTrue(
            top(this, SaqzShellContentTag) < top(this, SaqzShellNavTag),
            "content must be read before the bottom nav",
        )
    }

    @Test
    fun productionNavHasTwoItems() = runComposeUiTest {
        setContent { SaqzAppShell() }
        val tabs = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        assertEquals(2, tabs.fetchSemanticsNodes().size, "shell exposes exactly Home and Componentes")
        onNodeWithText("Início").assertExists()
        onNodeWithText("Componentes").assertExists()
    }
}
