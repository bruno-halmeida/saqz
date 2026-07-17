package br.com.saqz.access.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.data.GroupDto
import br.com.saqz.access.data.GroupRoleDto
import br.com.saqz.access.data.VersionedGroupDto
import br.com.saqz.access.presentation.GroupActions
import br.com.saqz.access.presentation.GroupAdministrationState
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupContextScreensTest {
    @Test fun `context shows authoritative group and role`() = runComposeUiTest { context(owner); onNodeWithText("Current Group").assertExists(); onNodeWithText("OWNER").assertExists() }
    @Test fun `athlete has read only context`() = runComposeUiTest { context(athlete); onNodeWithTag(GroupContextTags.Settings).assertDoesNotExist(); onNodeWithTag(GroupContextTags.Invite).assertDoesNotExist() }
    @Test fun `admin can edit and invite but not roles`() = runComposeUiTest { context(admin); onNodeWithTag(GroupContextTags.Settings).assertExists(); onNodeWithTag(GroupContextTags.Invite).assertExists(); onNodeWithTag(GroupContextTags.Roles).assertDoesNotExist() }
    @Test fun `owner can manage roles`() = runComposeUiTest { context(owner); onNodeWithTag(GroupContextTags.Roles).assertExists() }
    @Test fun `role refresh removes stale actions`() = runComposeUiTest {
        var state by mutableStateOf(owner); setContent { SaqzTheme { GroupContextScreen(state, {}, {}, {}, {}, {}) } }
        onNodeWithTag(GroupContextTags.Roles).assertExists(); state = athlete; waitForIdle(); onNodeWithTag(GroupContextTags.Roles).assertDoesNotExist()
    }
    @Test fun `group switch callback is accessible`() = runComposeUiTest { var calls=0; context(owner,onSwitch={calls++}); onNodeWithTag(GroupContextTags.Switch).performClick(); assertEquals(1,calls) }
    @Test fun `settings callback is routed`() = runComposeUiTest { var calls=0; context(owner,onSettings={calls++}); onNodeWithTag(GroupContextTags.Settings).performClick(); assertEquals(1,calls) }
    @Test fun `settings fields emit values`() = runComposeUiTest { var n=""; var z=""; settings(onName={n=it},onZone={z=it}); onAllNodes(hasSetTextAction(),true)[0].performTextInput("New"); onAllNodes(hasSetTextAction(),true)[1].performTextInput("America/Sao_Paulo"); assertEquals("New",n); assertEquals("America/Sao_Paulo",z) }
    @Test fun `settings save invokes update`() = runComposeUiTest { var calls=0; settings(onSave={calls++}); onNodeWithTag(GroupContextTags.Save).performClick(); assertEquals(1,calls) }
    @Test fun `settings loading prevents duplicate save`() = runComposeUiTest { settings(owner.copy(isLoading=true)); onNodeWithTag(GroupContextTags.Save).assertIsNotEnabled() }
    @Test fun `version conflict asks for reload`() = runComposeUiTest { var calls=0; settings(owner.copy(versionConflict=true),onReload={calls++}); onNodeWithText("O grupo mudou. Recarregue antes de salvar").assertExists(); onNodeWithTag(GroupContextTags.Reload).performClick(); assertEquals(1,calls) }
    @Test fun `logout opens through context callback`() = runComposeUiTest { var calls=0; context(owner,onLogout={calls++}); onNodeWithTag(GroupContextTags.Logout).performClick(); assertEquals(1,calls) }
    @Test fun `logout dialog requires explicit confirmation`() = runComposeUiTest { var calls=0; setContent { SaqzTheme { LogoutConfirmationDialog({calls++},{}) } }; onNodeWithTag(GroupContextTags.LogoutConfirm).performClick(); assertEquals(1,calls) }

    private fun androidx.compose.ui.test.ComposeUiTest.context(state: GroupAdministrationState,onSwitch:()->Unit={},onSettings:()->Unit={},onLogout:()->Unit={}) = setContent { SaqzTheme { GroupContextScreen(state,onSwitch,onSettings,{},{},onLogout) } }
    private fun androidx.compose.ui.test.ComposeUiTest.settings(state: GroupAdministrationState=owner,onName:(String)->Unit={},onZone:(String)->Unit={},onSave:()->Unit={},onReload:()->Unit={}) = setContent { SaqzTheme { GroupSettingsScreen(state,"","",onName,onZone,onSave,onReload,{}) } }
    private companion object {
        val group=VersionedGroupDto(GroupDto("id","Current Group","America/Sao_Paulo",1,GroupRoleDto.OWNER),"etag")
        val owner=GroupAdministrationState(group=group,actions=GroupActions(true,true,true))
        val admin=GroupAdministrationState(group=group.copy(group=group.group.copy(role=GroupRoleDto.ADMIN)),actions=GroupActions(true,false,true))
        val athlete=GroupAdministrationState(group=group.copy(group=group.group.copy(role=GroupRoleDto.ATHLETE)),actions=GroupActions(false,false,false))
    }
}
