package br.com.saqz.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SaqzSegmentedVariantsTest {
    @Test
    fun verticalSegmentedShowsTheFullLabelAndReportsTheIndex() = runComposeUiTest {
        var selected by mutableStateOf(-1)
        setContent {
            SaqzTheme {
                SaqzSegmented(
                    options = listOf("Vôlei de quadra", "Vôlei de areia", "Futevôlei"),
                    selected = selected,
                    onSelect = { selected = it },
                    vertical = true,
                )
            }
        }
        onNodeWithText("Vôlei de quadra").assertIsNotSelected()
        onNodeWithText("Vôlei de areia").performClick()
        waitForIdle()
        assertEquals(1, selected)
        onNodeWithText("Vôlei de areia").assertIsSelected()
    }

    @Test
    fun multiSegmentedTogglesEachOptionIndependently() = runComposeUiTest {
        var selected by mutableStateOf(setOf(2))
        setContent {
            SaqzTheme {
                SaqzMultiSegmented(
                    options = listOf("Dom", "Seg", "Ter", "Qua"),
                    selected = selected,
                    onToggle = { selected = if (it in selected) selected - it else selected + it },
                )
            }
        }
        onNodeWithText("Ter").assertIsOn()
        onNodeWithText("Qua").assertIsOff()
        onNodeWithText("Qua").performClick()
        waitForIdle()
        assertEquals(setOf(2, 3), selected)
        onNodeWithText("Ter").performClick()
        waitForIdle()
        assertEquals(setOf(3), selected)
    }

    @Test
    fun stepperFormatsTheValueAndFollowsTheCustomNeighbours() = runComposeUiTest {
        var value by mutableStateOf(120)
        val grid = listOf(60, 90, 120, 150)
        setContent {
            SaqzTheme {
                SaqzStepper(
                    value = value,
                    onValueChange = { value = it },
                    min = 60,
                    max = 150,
                    format = { "${it / 60}h" },
                    previous = { v -> grid.last { it < v } },
                    next = { v -> grid.first { it > v } },
                    decreaseDescription = "Diminuir 30 minutos",
                    increaseDescription = "Aumentar 30 minutos",
                )
            }
        }
        onNodeWithText("2h").assertExists()
        onNodeWithContentDescription("Aumentar 30 minutos").performClick()
        waitForIdle()
        assertEquals(150, value)
        onNodeWithContentDescription("Diminuir 30 minutos").performClick()
        waitForIdle()
        assertEquals(120, value)
    }
}
