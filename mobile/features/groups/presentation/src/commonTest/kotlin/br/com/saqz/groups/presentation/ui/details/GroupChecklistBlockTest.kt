package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupChecklistItem
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupChecklistBlockTest {
    @Test
    fun `rows route to their action and snooze asks to postpone`() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setContent { SaqzTheme { GroupChecklistBlock(GroupChecklistPreview, intents::add) } }

        onNodeWithText("Deixe o grupo redondo").assertExists()
        onNodeWithText("Agora").assertExists()
        onAllNodesWithText("Feito").assertCountEquals(1)
        onNodeWithTag(GroupDetailsTags.checklistItem(GroupChecklistItem.WhatsApp)).performClick()
        onNodeWithTag(GroupDetailsTags.checklistItem(GroupChecklistItem.Pix)).performClick()
        onNodeWithTag(GroupDetailsTags.ChecklistSnooze).performClick()

        assertEquals(
            listOf(
                GroupDetailsIntent.ChecklistAction(GroupChecklistItem.WhatsApp),
                GroupDetailsIntent.ChecklistAction(GroupChecklistItem.Pix),
                GroupDetailsIntent.SnoozeChecklist,
            ),
            intents,
        )
    }

    @Test
    fun `checklist only appears in the onboarding group after the three steps`() = runComposeUiTest {
        val state = GroupHeroPreviewData.adminInviteGuide.copy(memberCount = 2, checklist = GroupChecklistPreview)

        setDetailsScreen(state, onboardingGroup = true)
        onNodeWithTag(GroupDetailsTags.Checklist).assertExists()
    }

    @Test
    fun `checklist stays hidden while the steps are open or outside the onboarding group`() = runComposeUiTest {
        val state = GroupHeroPreviewData.adminInviteGuide.copy(memberCount = 1, checklist = GroupChecklistPreview)

        setDetailsScreen(state, onboardingGroup = true)
        onAllNodesWithText("Deixe o grupo redondo").assertCountEquals(0)
    }

    @Test
    fun `second group never shows the checklist`() = runComposeUiTest {
        val state = GroupHeroPreviewData.adminInviteGuide.copy(memberCount = 2, checklist = GroupChecklistPreview)

        setDetailsScreen(state, onboardingGroup = false)
        onAllNodesWithText("Deixe o grupo redondo").assertCountEquals(0)
    }

    @Test
    fun `pix key notice shows for the admin and opens the setup without repeating the pix item`() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        val checklist = GroupChecklistPreview.copy(rows = GroupChecklistPreview.rows.filter { it.item != GroupChecklistItem.Pix })
        val state = GroupHeroPreviewData.adminInviteGuide.copy(memberCount = 2, checklist = checklist, pixKeyNotice = true)

        setDetailsScreen(state, onboardingGroup = true, onIntent = intents::add)

        onNodeWithTag(GroupDetailsTags.PixKeyNotice).assertExists()
        onNodeWithTag(GroupDetailsTags.checklistItem(GroupChecklistItem.Pix)).assertDoesNotExist()
        onAllNodesWithText("Cadastre a chave Pix para seus atletas pagarem.").assertCountEquals(1)
        onNodeWithTag(br.com.saqz.groups.presentation.ui.finance.PixKeyNoticeTags.Action).performClick()
        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.OpenPixSetup), intents)
    }

    @Test
    fun `athlete never sees the pix key notice`() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member.copy(pixKeyNotice = true))

        onNodeWithTag(GroupDetailsTags.PixKeyNotice).assertDoesNotExist()
    }

    @Test
    fun `without the notice the checklist keeps asking for the pix key`() = runComposeUiTest {
        val state = GroupHeroPreviewData.adminInviteGuide.copy(memberCount = 2, checklist = GroupChecklistPreview)

        setDetailsScreen(state, onboardingGroup = true)

        onNodeWithTag(GroupDetailsTags.PixKeyNotice).assertDoesNotExist()
        onNodeWithTag(GroupDetailsTags.checklistItem(GroupChecklistItem.Pix)).assertExists()
    }
}
