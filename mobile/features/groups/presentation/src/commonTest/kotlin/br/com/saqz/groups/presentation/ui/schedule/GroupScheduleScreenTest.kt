package br.com.saqz.groups.presentation.ui.schedule

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.schedule.GroupScheduleIntent
import br.com.saqz.groups.presentation.schedule.GroupScheduleState
import br.com.saqz.groups.presentation.ui.components.DurationChoiceTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupScheduleScreenTest {
    private fun loaded() = GroupScheduleState(isLoading = false)

    @Test
    fun `tapping other sends the intent and typing sends the hours`() = runComposeUiTest {
        val intents = mutableListOf<GroupScheduleIntent>()
        setContent { SaqzTheme { GroupScheduleScreen(loaded(), { intents += it }, {}) } }

        onNodeWithTag(DurationChoiceTags.Other).performScrollTo().performClick()
        assertEquals(GroupScheduleIntent.SelectOtherDuration, intents.last())
    }

    @Test
    fun `other with whole hours shows the field and the equivalent`() = runComposeUiTest {
        val intents = mutableListOf<GroupScheduleIntent>()
        val state = loaded().copy(durationMinutes = 180, durationText = "3")
        setContent { SaqzTheme { GroupScheduleScreen(state, { intents += it }, {}) } }

        onNodeWithTag(DurationChoiceTags.Other).performScrollTo().assertIsSelected()
        onNodeWithText("3 h = 180 min").performScrollTo().assertExists()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(DurationChoiceTags.Field)), useUnmergedTree = true)
            .performScrollTo().performTextReplacement("4")
        assertEquals(GroupScheduleIntent.UpdateDurationText("4"), intents.last())
    }

    @Test
    fun `duration error shows in the field`() = runComposeUiTest {
        val state = loaded().copy(durationMinutes = 0, durationText = "9", durationError = true)
        setContent { SaqzTheme { GroupScheduleScreen(state, {}, {}) } }

        onNodeWithText("Digite um número inteiro de horas, de 3 a 8.").performScrollTo().assertExists()
    }

    @Test
    fun `saved duration off the hour opens as other with the value`() = runComposeUiTest {
        val state = loaded().copy(durationMinutes = 195)
        setContent { SaqzTheme { GroupScheduleScreen(state, {}, {}) } }

        onNodeWithTag(DurationChoiceTags.Other).performScrollTo().assertIsSelected()
        onNodeWithText("Duração: 3h15").performScrollTo().assertExists()
    }
}
