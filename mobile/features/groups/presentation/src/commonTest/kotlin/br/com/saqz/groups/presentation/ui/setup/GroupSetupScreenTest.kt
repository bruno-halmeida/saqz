package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupStep
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupSetupScreenTest {

    @Test
    fun savingDisablesCreateAndIgnoresNewClicks() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent {
            SaqzTheme {
                GroupReviewScreen(
                    state = GroupSetupState(
                        mode = GroupSetupMode.Create,
                        step = GroupSetupStep.Review,
                        form = PreviewCourtForm,
                        isSaving = true,
                    ),
                    onIntent = intents::add,
                )
            }
        }

        onNodeWithTag(GroupSetupTags.ReviewCreate).assertIsNotEnabled()
        onNodeWithTag(GroupSetupTags.ReviewEdit).assertIsNotEnabled()
        onNodeWithTag(GroupSetupTags.ReviewCreate).performClick()
        waitForIdle()
        assertTrue(intents.isEmpty())
    }

    @Test
    fun savingDisablesTheFormSubmit() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent {
            SaqzTheme {
                GroupSetupScreen(
                    state = GroupSetupState(
                        mode = GroupSetupMode.Edit(groupId = "grp-1"),
                        form = PreviewCourtForm,
                        isSaving = true,
                    ),
                    onIntent = intents::add,
                    onBack = {},
                )
            }
        }

        onNodeWithTag(GroupSetupTags.Submit).assertIsNotEnabled()
        onNodeWithTag(GroupSetupTags.Submit).performClick()
        waitForIdle()
        assertTrue(intents.isEmpty())
    }
}
