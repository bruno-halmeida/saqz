package br.com.saqz.groups.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import br.com.saqz.groups.data.*
import br.com.saqz.groups.presentation.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.network.SessionMembershipDto
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupOnboardingScreensTest {
    @Test fun `empty memberships offer group creation`() = runComposeUiTest {
        var intent: GroupOnboardingIntent? = null; groups(GroupSelectionState.NoGroup) { intent = it }
        onNodeWithText("Criar grupo").performClick()
        assertEquals(GroupOnboardingIntent.OpenCreateGroup, intent)
    }

    @Test fun `selector lists every group name`() = runComposeUiTest {
        groups(selector)
        onNodeWithText("First Group").assertExists(); onNodeWithText("Second Group").assertExists()
    }

    @Test fun `selector lists authoritative roles`() = runComposeUiTest {
        groups(selector)
        onNodeWithText("OWNER").assertExists(); onNodeWithText("ATHLETE").assertExists()
    }

    @Test fun `selector emits selected group id`() = runComposeUiTest {
        var intent: GroupOnboardingIntent? = null; groups(selector) { intent = it }
        onNodeWithText("Second Group").performClick()
        assertEquals(GroupOnboardingIntent.Select("group-2"), intent)
    }

    @Test fun `selector also offers group creation`() = runComposeUiTest {
        var intent: GroupOnboardingIntent? = null; groups(selector) { intent = it }
        onNodeWithTag(GroupOnboardingTags.Create).performClick()
        assertEquals(GroupOnboardingIntent.OpenCreateGroup, intent)
    }

    @Test fun `group switch loading never exposes previous content`() = runComposeUiTest {
        groups(GroupSelectionState.Loading("group-2"))
        onNodeWithText("First Group").assertDoesNotExist()
        onNodeWithTag(GroupOnboardingTags.GroupLoading).assertExists()
    }

    @Test fun `group load error retries`() = runComposeUiTest {
        var intent: GroupOnboardingIntent? = null; groups(GroupSelectionState.LoadError("group-2")) { intent = it }
        onNodeWithText("Tentar novamente").performClick()
        assertEquals(GroupOnboardingIntent.Retry, intent)
    }

    @Test fun `selected group exposes only current name`() = runComposeUiTest {
        groups(GroupSelectionState.Selected(versioned))
        onNodeWithText("Current Group").assertExists(); onNodeWithText("First Group").assertDoesNotExist()
    }

    @Test fun `create name emits controlled value`() = runComposeUiTest {
        var intent: CreateGroupIntent? = null; create(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Training Group")
        assertEquals(CreateGroupIntent.UpdateName("Training Group"), intent)
    }

    @Test fun `create timezone emits controlled value`() = runComposeUiTest {
        var intent: CreateGroupIntent? = null; create(onIntent = { intent = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("America/Sao_Paulo")
        assertEquals(CreateGroupIntent.UpdateTimeZone("America/Sao_Paulo"), intent)
    }

    @Test fun `invalid create validation state exposes field errors`() = runComposeUiTest {
        create(validationAttempted = true)
        onNodeWithText("Informe o nome do grupo").assertExists()
        onNodeWithText("Informe uma timezone IANA").assertExists()
    }

    @Test fun `valid create form submits once`() = runComposeUiTest {
        var intent: CreateGroupIntent? = null
        create(name = "Training Group", timeZone = "America/Sao_Paulo", onIntent = { intent = it })
        onNodeWithTag(GroupOnboardingTags.CreateSubmit).performClick()
        assertEquals(CreateGroupIntent.Submit, intent)
    }

    @Test fun `large text keeps loading create action stable`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    CreateGroupScreen(
                        CreateGroupUiState(
                            GroupAdministrationState(isLoading = true),
                            "Training Group",
                            "America/Sao_Paulo",
                        ),
                        {},
                    )
                }
            }
        }
        onNodeWithTag(GroupOnboardingTags.CreateSubmit).assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.groups(
        state: GroupSelectionState,
        onIntent: (GroupOnboardingIntent) -> Unit = {},
    ) = setContent { SaqzTheme { GroupOnboardingScreen(state, onIntent) } }

    private fun androidx.compose.ui.test.ComposeUiTest.create(
        state: GroupAdministrationState = GroupAdministrationState(), name: String = "", timeZone: String = "",
        validationAttempted: Boolean = false,
        onIntent: (CreateGroupIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            CreateGroupScreen(CreateGroupUiState(state, name, timeZone, validationAttempted), onIntent)
        }
    }

    private companion object {
        val selector = GroupSelectionState.Selector(
            listOf(SessionMembershipDto("group-1", "First Group", "OWNER"), SessionMembershipDto("group-2", "Second Group", "ATHLETE")),
        )
        val versioned = VersionedGroupDto(GroupDto("group-2", "Current Group", "America/Sao_Paulo", 1, GroupRoleDto.OWNER), "etag")
    }
}
