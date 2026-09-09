package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupLeaveSheetTest {
    @Test fun confirmationAndCancelAreSeparateVisibleActions() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setContent { SaqzTheme { GroupLeaveSheet(GroupDetailsState(confirmingLeave = true), intents::add) } }
        val confirm = onNodeWithTag(GroupLeaveTags.Confirm).assertIsDisplayed()
        val cancel = onNodeWithTag(GroupLeaveTags.Cancel).assertIsDisplayed()
        assertTrue(confirm.getUnclippedBoundsInRoot().bottom <= cancel.getUnclippedBoundsInRoot().top)
        cancel.performClick()
        confirm.performClick()
        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.CancelLeave, GroupDetailsIntent.ConfirmLeave), intents)
    }

    @Test fun loadingPreventsCancelAndRepeatedConfirmation() = runComposeUiTest {
        setContent { SaqzTheme { GroupLeaveSheet(GroupDetailsState(confirmingLeave = true, leaving = true), {}) } }
        onNodeWithTag(GroupLeaveTags.Confirm).assertIsNotEnabled()
        onNodeWithTag(GroupLeaveTags.Cancel).assertIsNotEnabled()
    }

    @Test fun failedDepartureDisplaysRetryAndKeepsCancelAvailable() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setContent { SaqzTheme { GroupLeaveSheet(GroupDetailsState(confirmingLeave = true, leaveFailed = true), intents::add) } }
        onNodeWithTag(GroupLeaveTags.Error).assertIsDisplayed()
        onNodeWithTag(GroupLeaveTags.Confirm).performClick()
        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.ConfirmLeave), intents)
    }
}
