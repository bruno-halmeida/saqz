package br.com.saqz.groups.presentation.ui.schedule

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
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
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.schedule.GroupScheduleIntent
import br.com.saqz.groups.presentation.setup.GroupSlotDraft
import br.com.saqz.groups.presentation.ui.setup.GroupSlotSheetTags
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

    @Test
    fun `the priority is a row of four equal options with the support text`() = runComposeUiTest {
        val intents = mutableListOf<GroupScheduleIntent>()
        setContent { SaqzTheme { GroupScheduleScreen(loaded().copy(confirmationLeadMinutes = 360), { intents += it }, {}) } }

        onNodeWithText("Prioridade dos mensalistas").performScrollTo().assertExists()
        onNodeWithText("Horas antes do jogo. Depois, a vaga é de todos.").performScrollTo().assertExists()
        listOf("3h", "6h", "12h", "24h").forEach { onNodeWithText(it).performScrollTo().assertExists() }
        onNodeWithTag("${GroupScheduleTags.ConfirmationLead}-360").performScrollTo().assertIsSelected()
        onNodeWithTag("${GroupScheduleTags.ConfirmationLead}-1440").performScrollTo().performClick()
        assertEquals(GroupScheduleIntent.SelectConfirmationLead(1_440), intents.last())
    }

    @Test
    fun `a saved priority off the four options marks none`() = runComposeUiTest {
        setContent { SaqzTheme { GroupScheduleScreen(loaded().copy(confirmationLeadMinutes = 90), {}, {}) } }

        listOf(180, 360, 720, 1_440).forEach {
            onNodeWithTag("${GroupScheduleTags.ConfirmationLead}-$it").performScrollTo().assertIsNotSelected()
        }
    }

    private fun withSheet(draft: GroupSlotDraft, index: Int = GroupScheduleState.NEW_SLOT) = loaded().copy(
        slots = listOf(GroupRegularSlotForm(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120)),
        slotSheet = index,
        slotDraft = draft,
    )

    @Test
    fun `the new slot sheet takes several days and offers to save them all`() = runComposeUiTest {
        val intents = mutableListOf<GroupScheduleIntent>()
        val draft = GroupSlotDraft(setOf(GroupWeekday.TUESDAY, GroupWeekday.THURSDAY), 19, 30)
        setContent { SaqzTheme { GroupScheduleScreen(withSheet(draft), { intents += it }, {}) } }

        onNodeWithText("Dias e horário").assertExists()
        onNodeWithText("Pode marcar mais de um.").assertExists()
        onNodeWithText("Toda terça e quinta, às 19h30.").assertExists()
        onNodeWithTag(GroupSlotSheetTags.weekday(GroupWeekday.FRIDAY)).performClick()
        onNodeWithTag(GroupSlotSheetTags.minute(45)).performClick()
        onNodeWithTag(GroupScheduleTags.SlotSheetSave).assertTextEquals("Salvar 2 horários")
        onNodeWithTag(GroupScheduleTags.SlotSheetSave).performClick()
        assertEquals(
            listOf(
                GroupScheduleIntent.ToggleDraftDay(GroupWeekday.FRIDAY),
                GroupScheduleIntent.PickDraftTime(19, 45),
                GroupScheduleIntent.ConfirmSlot,
            ),
            intents,
        )
    }

    @Test
    fun `editing a slot is a single day and saves one`() = runComposeUiTest {
        val intents = mutableListOf<GroupScheduleIntent>()
        setContent {
            SaqzTheme {
                GroupScheduleScreen(withSheet(GroupSlotDraft(GroupWeekday.TUESDAY, 19, 30), index = 0), { intents += it }, {})
            }
        }

        onNodeWithText("Pode marcar mais de um.").assertDoesNotExist()
        onNodeWithTag(GroupScheduleTags.SlotSheetSave).assertTextEquals("Salvar")
        onNodeWithTag(GroupSlotSheetTags.weekday(GroupWeekday.FRIDAY)).performClick()
        assertEquals(GroupScheduleIntent.PickDraftDay(GroupWeekday.FRIDAY), intents.last())
    }

    @Test
    fun `a saved minute off the grid shows in the summary and marks none`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupScheduleScreen(withSheet(GroupSlotDraft(GroupWeekday.TUESDAY, 19, 20), index = 0), {}, {})
            }
        }

        onNodeWithText("Toda terça, às 19h20.").assertExists()
        listOf(0, 15, 30, 45).forEach { onNodeWithTag(GroupSlotSheetTags.minute(it)).assertIsNotSelected() }
    }

    @Test
    fun `save is disabled with no day marked`() = runComposeUiTest {
        setContent {
            SaqzTheme { GroupScheduleScreen(withSheet(GroupSlotDraft(emptySet(), 19, 30)), {}, {}) }
        }

        onNodeWithTag(GroupScheduleTags.SlotSheetSave).assertIsNotEnabled()
    }
}
