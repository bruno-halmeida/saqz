package br.com.saqz.access.ui

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
import br.com.saqz.access.data.GroupDto
import br.com.saqz.access.data.GroupRoleDto
import br.com.saqz.access.data.VersionedGroupDto
import br.com.saqz.access.presentation.GroupAdministrationState
import br.com.saqz.access.presentation.GroupSelectionState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.network.SessionMembershipDto
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupOnboardingScreensTest {
    @Test fun `bootstrap renders progress without login`() = runComposeUiTest {
        bootstrap(SessionAccessState.Bootstrapping)
        onNodeWithTag(GroupOnboardingTags.BootstrapLoading).assertExists()
        onNodeWithText("Entrar").assertDoesNotExist()
    }

    @Test fun `bootstrap error retries without returning to login`() = runComposeUiTest {
        var calls = 0; bootstrap(SessionAccessState.BootstrapError, { calls++ })
        onNodeWithText("Tentar novamente").performClick()
        assertEquals(1, calls)
        onNodeWithText("Entrar").assertDoesNotExist()
    }

    @Test fun `empty memberships offer group creation`() = runComposeUiTest {
        var calls = 0; groups(GroupSelectionState.NoGroup, onCreate = { calls++ })
        onNodeWithText("Criar grupo").performClick()
        assertEquals(1, calls)
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
        var selected = ""; groups(selector, onSelect = { selected = it })
        onNodeWithText("Second Group").performClick()
        assertEquals("group-2", selected)
    }

    @Test fun `selector also offers group creation`() = runComposeUiTest {
        var calls = 0; groups(selector, onCreate = { calls++ })
        onNodeWithTag(GroupOnboardingTags.Create).performClick(); assertEquals(1, calls)
    }

    @Test fun `group switch loading never exposes previous content`() = runComposeUiTest {
        groups(GroupSelectionState.Loading("group-2"))
        onNodeWithText("First Group").assertDoesNotExist()
        onNodeWithTag(GroupOnboardingTags.GroupLoading).assertExists()
    }

    @Test fun `group load error retries`() = runComposeUiTest {
        var calls = 0; groups(GroupSelectionState.LoadError("group-2"), onRetry = { calls++ })
        onNodeWithText("Tentar novamente").performClick(); assertEquals(1, calls)
    }

    @Test fun `selected group exposes only current name`() = runComposeUiTest {
        groups(GroupSelectionState.Selected(versioned))
        onNodeWithText("Current Group").assertExists(); onNodeWithText("First Group").assertDoesNotExist()
    }

    @Test fun `create name emits controlled value`() = runComposeUiTest {
        var value = ""; create(onNameChange = { value = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Training Group")
        assertEquals("Training Group", value)
    }

    @Test fun `create timezone emits controlled value`() = runComposeUiTest {
        var value = ""; create(onTimeZoneChange = { value = it })
        onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("America/Sao_Paulo")
        assertEquals("America/Sao_Paulo", value)
    }

    @Test fun `invalid create fields stay local`() = runComposeUiTest {
        var calls = 0; create(onSubmit = { calls++ })
        onNodeWithTag(GroupOnboardingTags.CreateSubmit).performClick()
        onNodeWithText("Informe o nome do grupo").assertExists()
        onNodeWithText("Informe uma timezone IANA").assertExists()
        assertEquals(0, calls)
    }

    @Test fun `valid create form submits once`() = runComposeUiTest {
        var calls = 0; create(name = "Training Group", timeZone = "America/Sao_Paulo", onSubmit = { calls++ })
        onNodeWithTag(GroupOnboardingTags.CreateSubmit).performClick(); assertEquals(1, calls)
    }

    @Test fun `large text keeps loading create action stable`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    CreateGroupScreen(GroupAdministrationState(isLoading = true), "Training Group", "America/Sao_Paulo", {}, {}, {}, {})
                }
            }
        }
        onNodeWithTag(GroupOnboardingTags.CreateSubmit).assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.bootstrap(state: SessionAccessState, retry: () -> Unit = {}) =
        setContent { SaqzTheme { BootstrapAccessScreen(state, retry) } }

    private fun androidx.compose.ui.test.ComposeUiTest.groups(
        state: GroupSelectionState,
        onSelect: (String) -> Unit = {}, onCreate: () -> Unit = {}, onRetry: () -> Unit = {},
    ) = setContent { SaqzTheme { GroupOnboardingScreen(state, onSelect, onCreate, onRetry) } }

    private fun androidx.compose.ui.test.ComposeUiTest.create(
        state: GroupAdministrationState = GroupAdministrationState(), name: String = "", timeZone: String = "",
        onNameChange: (String) -> Unit = {}, onTimeZoneChange: (String) -> Unit = {},
        onSubmit: () -> Unit = {}, onBack: () -> Unit = {},
    ) = setContent { SaqzTheme { CreateGroupScreen(state, name, timeZone, onNameChange, onTimeZoneChange, onSubmit, onBack) } }

    private companion object {
        val selector = GroupSelectionState.Selector(
            listOf(SessionMembershipDto("group-1", "First Group", "OWNER"), SessionMembershipDto("group-2", "Second Group", "ATHLETE")),
        )
        val versioned = VersionedGroupDto(GroupDto("group-2", "Current Group", "America/Sao_Paulo", 1, GroupRoleDto.OWNER), "etag")
    }
}
