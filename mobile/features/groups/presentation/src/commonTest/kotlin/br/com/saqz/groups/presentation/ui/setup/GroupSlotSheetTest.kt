package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSlotDraft
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupSlotSheetTest {
    private fun sheet(draft: GroupSlotDraft, index: Int? = null) = GroupSetupState(mode = GroupSetupMode.Create)
        .copy(sheet = GroupSetupSheet.Slot(index), slotDraft = draft)

    @Test
    fun twoDaysShowTheSummaryAndTheSaveManyButton() = runComposeUiTest {
        val draft = GroupSlotDraft(setOf(GroupWeekday.TUESDAY, GroupWeekday.THURSDAY), 19, 30)
        setContent { SaqzTheme { GroupSetupSheets(sheet(draft), {}) } }

        onNodeWithText("Toda terça e quinta, às 19h30.").assertExists()
        onNodeWithText("Salvar 2 horários").assertExists()
        onNodeWithText("Pode marcar mais de um.").assertExists()
        onNodeWithTag(GroupSlotSheetTags.weekday(GroupWeekday.THURSDAY)).assertIsOn()
    }

    @Test
    fun tappingDayAndMinuteSendsTheIntents() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent { SaqzTheme { GroupSetupSheets(sheet(GroupSlotDraft()), { intents += it }) } }

        onNodeWithTag(GroupSlotSheetTags.weekday(GroupWeekday.FRIDAY)).performClick()
        onNodeWithTag(GroupSlotSheetTags.minute(45)).performClick()
        assertEquals(GroupSetupIntent.ToggleSlotWeekday(GroupWeekday.FRIDAY), intents[0])
        assertEquals(GroupSetupIntent.PickSlotTime(19, 45), intents[1])
    }

    @Test
    fun editingAnExistingSlotIsSingleDay() = runComposeUiTest {
        val state = GroupSetupState(
            mode = GroupSetupMode.Create,
            form = PreviewCourtForm,
        ).copy(sheet = GroupSetupSheet.Slot(0), slotDraft = GroupSlotDraft(GroupWeekday.TUESDAY, 19, 30))
        val intents = mutableListOf<GroupSetupIntent>()
        setContent { SaqzTheme { GroupSetupSheets(state, { intents += it }) } }

        onNodeWithText("Pode marcar mais de um.").assertDoesNotExist()
        onNodeWithText("Salvar").assertExists()
        onNodeWithTag(GroupSlotSheetTags.weekday(GroupWeekday.FRIDAY)).performClick()
        assertEquals(GroupSetupIntent.PickSlotWeekday(GroupWeekday.FRIDAY), intents.last())
    }
}
