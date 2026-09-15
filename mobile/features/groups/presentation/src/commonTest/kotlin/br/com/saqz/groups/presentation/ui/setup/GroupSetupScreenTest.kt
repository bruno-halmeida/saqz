package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun newGroupKeepsPhotoAndBasicsButDefersAdvancedSettings() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Create), {}, {}) } }
        onNodeWithTag(GroupSetupTags.Photo).assertExists()
        onNodeWithTag(GroupSetupTags.Name).assertExists()
        onNodeWithTag(GroupSetupTags.Modality).assertExists()
        onNodeWithTag(GroupSetupTags.Composition).assertExists()
        onNodeWithTag(GroupSetupTags.Level).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        onNodeWithTag(GroupSetupTags.Level).assertExists()
        onNodeWithTag(GroupSetupTags.Capacity).assertExists()
    }

    @Test
    fun editingAndValidationKeepAdvancedFieldsAccessible() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(PreviewErrorState, {}, {}) } }
        onNodeWithTag(GroupSetupTags.CustomLevel).assertExists()
        onNodeWithTag(GroupSetupTags.VenueAddress).assertExists()
    }

    @Test
    fun editingWithoutErrorsKeepsAdvancedFieldsExpanded() = runComposeUiTest {
        setContent {
            SaqzTheme { GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Edit("existing")), {}, {}) }
        }
        onNodeWithTag(GroupSetupTags.Advanced).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Level).assertExists()
        onNodeWithTag(GroupSetupTags.Capacity).assertExists()
    }

    @Test
    fun firstTrialGroupExplainsStartWithoutPayment() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Create), {}, {}, showTrialOffer = true)
            }
        }
        onNodeWithTag(GroupSetupTags.TrialOffer).assertExists()
        onNodeWithText("Teste o Organizador com até 3 grupos e atletas ilimitados. O prazo começa no primeiro grupo e é o mesmo para todos. Depois, escolha um plano pago para continuar, sem cobrança automática.").assertExists()
    }

    @Test
    fun editingDoesNotPromiseANewTrial() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Edit("existing")), {}, {}, showTrialOffer = true)
            }
        }
        onNodeWithTag(GroupSetupTags.TrialOffer).assertDoesNotExist()
    }
    @Test
    fun paidGroupDoesNotPromiseANewTrial() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(GroupSetupState(mode = GroupSetupMode.Create), {}, {}, showTrialOffer = false)
            }
        }
        onNodeWithTag(GroupSetupTags.TrialOffer).assertDoesNotExist()
    }

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

    @Test
    fun editSaveFailureUsesSaveCopy() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupSetupScreen(
                    state = GroupSetupState(
                        mode = GroupSetupMode.Edit(groupId = "grp-1"),
                        form = PreviewCourtForm,
                        saveFailed = true,
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }
        waitForIdle()

        onNodeWithText("Não foi possível salvar o grupo").assertExists()
        onNodeWithText("Não foi possível criar o grupo").assertDoesNotExist()
    }
}
