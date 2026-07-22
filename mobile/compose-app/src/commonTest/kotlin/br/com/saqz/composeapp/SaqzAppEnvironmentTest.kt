package br.com.saqz.composeapp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.core.common.state.SaqzUiState
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzAppEnvironmentTest {

    @Test
    fun defaultShowsContent() {
        val dependencies = startTestSaqzKoin()
        try {
            runComposeUiTest {
                setContent { SaqzApp(dependencies) }
                // Default startup is Content(Unit): the Home content slot renders, not a state view.
                onNodeWithText("Organize seu grupo.", substring = true).assertExists()
                onNodeWithContentDescription("Carregando").assertDoesNotExist()
            }
        } finally {
            stopTestSaqzKoin()
        }
    }

    @Test
    fun loadingUsesStateHost() = runComposeUiTest {
        setContent { SaqzApp(SaqzAppEnvironment(startupState = SaqzUiState.Loading)) }
        onNodeWithContentDescription("Carregando").assertExists()
        onNodeWithText("Organize seu grupo.", substring = true).assertDoesNotExist()
    }

    @Test
    fun emptyUsesStateHost() = runComposeUiTest {
        setContent { SaqzApp(SaqzAppEnvironment(startupState = SaqzUiState.Empty)) }
        onNodeWithText("Nada por aqui").assertExists()
        onNodeWithText("Organize seu grupo.", substring = true).assertDoesNotExist()
    }

    @Test
    fun errorUsesStateHost() = runComposeUiTest {
        setContent { SaqzApp(SaqzAppEnvironment(startupState = SaqzUiState.Error)) }
        onNodeWithText("Não foi possível carregar").assertExists()
        onNodeWithText("Organize seu grupo.", substring = true).assertDoesNotExist()
    }

    @Test
    fun reduceMotionReachesTheme() = runComposeUiTest {
        // The environment's reduceMotion is mapped through the exact toPreferences() the app
        // uses, and SaqzTheme selects the reduced policy from it.
        lateinit var reduced: SaqzMotionPolicy
        lateinit var normal: SaqzMotionPolicy
        setContent {
            SaqzTheme(SaqzAppEnvironment(reduceMotion = true).toPreferences()) {
                reduced = SaqzTheme.motion
            }
            SaqzTheme(SaqzAppEnvironment(reduceMotion = false).toPreferences()) {
                normal = SaqzTheme.motion
            }
        }
        assertEquals(SaqzMotionPolicy.Reduced, reduced)
        assertEquals(SaqzMotionPolicy.Normal, normal)
    }

    @Test
    fun reduceTransparencyReachesTheme() = runComposeUiTest {
        // reduceTransparency travels through the same mapping into SaqzTheme's preferences,
        // independent of reduceMotion (chrome opacity is design-system internal, so the
        // observable seam is the mapping the theme consumes).
        val prefs = SaqzAppEnvironment(reduceTransparency = true).toPreferences()
        assertTrue(prefs.reduceTransparency)
        assertFalse(prefs.reduceMotion)
    }

    @Test
    fun nativeBoundaryHasOnlyTwoBooleans() {
        // Native supplies just the two booleans; startup stays Content(Unit), so no core
        // SaqzUiState type crosses the boundary.
        val env = SaqzAppEnvironment(reduceMotion = true, reduceTransparency = true)
        assertEquals(SaqzUiState.Content(Unit), env.startupState)
        assertTrue(env.reduceMotion && env.reduceTransparency)
        val defaults = SaqzAppEnvironment()
        assertEquals(SaqzUiState.Content(Unit), defaults.startupState)
        assertFalse(defaults.reduceMotion || defaults.reduceTransparency)
    }
}
