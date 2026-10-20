package br.com.saqz.groups.presentation.ui.schedule

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.schedule.GroupScheduleIntent
import br.com.saqz.groups.presentation.schedule.GroupScheduleState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupScheduleScreenTest {
    private fun loaded() = GroupScheduleState(isLoading = false)

    @Test
    fun `the counter shows the duration and sends the neighbour on the grid`() = runComposeUiTest {
        val intents = mutableListOf<GroupScheduleIntent>()
        setContent { SaqzTheme { GroupScheduleScreen(loaded().copy(durationMinutes = 150), { intents += it }, {}) } }

        onNodeWithText("2h30").performScrollTo().assertExists()
        onNodeWithContentDescription("Aumentar a duração").performScrollTo().performClick()
        assertEquals(GroupScheduleIntent.SelectDuration(180), intents.last())
        onNodeWithContentDescription("Diminuir a duração").performScrollTo().performClick()
        assertEquals(GroupScheduleIntent.SelectDuration(120), intents.last())
    }

    @Test
    fun `duration error colours the hint of the counter`() = runComposeUiTest {
        val state = loaded().copy(durationMinutes = 45, durationError = true)
        setContent { SaqzTheme { GroupScheduleScreen(state, {}, {}) } }

        onNodeWithText("De 1h a 8h. Até 3h, de 30 em 30 minutos.").performScrollTo().assertExists()
    }

    @Test
    fun `saved duration off the grid shows the value and moves to the neighbour`() = runComposeUiTest {
        val intents = mutableListOf<GroupScheduleIntent>()
        val state = loaded().copy(durationMinutes = 195)
        setContent { SaqzTheme { GroupScheduleScreen(state, { intents += it }, {}) } }

        onNodeWithText("3h15").performScrollTo().assertExists()
        onNodeWithContentDescription("Aumentar a duração").performScrollTo().performClick()
        assertEquals(GroupScheduleIntent.SelectDuration(240), intents.last())
    }
}
