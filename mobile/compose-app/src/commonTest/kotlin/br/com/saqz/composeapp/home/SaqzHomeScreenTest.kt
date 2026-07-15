package br.com.saqz.composeapp.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SaqzHomeScreenTest {

    @Test
    fun rendersWordmark() = runComposeUiTest {
        setContent { SaqzTheme { SaqzHomeScreen(onExploreComponents = {}) } }
        onNodeWithTag(SaqzHomeWordmarkTag).assertIsDisplayed()
    }

    @Test
    fun rendersHeading() = runComposeUiTest {
        setContent { SaqzTheme { SaqzHomeScreen(onExploreComponents = {}) } }
        onNodeWithText("Saqz").assertIsDisplayed()
    }

    @Test
    fun rendersExploreAction() = runComposeUiTest {
        setContent { SaqzTheme { SaqzHomeScreen(onExploreComponents = {}) } }
        onNodeWithText("Explorar componentes").assertIsDisplayed()
        onNodeWithText("Explorar componentes").assertHasClickAction()
    }

    @Test
    fun accessibilityOrderIsStable() = runComposeUiTest {
        setContent { SaqzTheme { SaqzHomeScreen(onExploreComponents = {}) } }
        // Reading order follows vertical position: wordmark, then heading, then action.
        val wordmarkTop = onNodeWithTag(SaqzHomeWordmarkTag).getUnclippedBoundsInRoot().top
        val headingTop = onNodeWithTag(SaqzHomeHeadingTag).getUnclippedBoundsInRoot().top
        val actionTop = onNodeWithText("Explorar componentes").getUnclippedBoundsInRoot().top
        kotlin.test.assertTrue(wordmarkTop < headingTop, "wordmark above heading")
        kotlin.test.assertTrue(headingTop < actionTop, "heading above action")
    }

    @Test
    fun actionNameMatchesVisibleLabel() = runComposeUiTest {
        setContent { SaqzTheme { SaqzHomeScreen(onExploreComponents = {}) } }
        // The accessible action label is exactly the visible label from the pt-BR catalog.
        val node = onNodeWithText("Explorar componentes").fetchSemanticsNode()
        val clickLabel = node.config[SemanticsActions.OnClick].label
        assertEquals("Explorar componentes", clickLabel)
    }

    @Test
    fun exploreActivatesOnce() = runComposeUiTest {
        var clicks = 0
        setContent { SaqzTheme { SaqzHomeScreen(onExploreComponents = { clicks++ }) } }
        onNodeWithText("Explorar componentes").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }
}
