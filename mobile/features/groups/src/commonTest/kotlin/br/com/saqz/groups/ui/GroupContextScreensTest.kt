package br.com.saqz.groups.ui

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
import br.com.saqz.groups.data.*
import br.com.saqz.groups.presentation.*
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupContextScreensTest {
    @Test fun `context shows authoritative group and role`() = runComposeUiTest { context(owner); onNodeWithText("Current Group").assertExists(); onNodeWithText("OWNER").assertExists() }
    @Test fun `athlete has read only context`() = runComposeUiTest { context(athlete); onNodeWithTag(GroupContextTags.Settings).assertDoesNotExist(); onNodeWithTag(GroupContextTags.Invite).assertDoesNotExist() }
    @Test fun `admin can edit and invite but not roles`() = runComposeUiTest {
        var intent: GroupContextIntent? = null
        context(admin) { intent = it }
        onNodeWithTag(GroupContextTags.Settings).assertExists()
        onNodeWithText("Convidar a galera").assertExists()
        onNodeWithTag(GroupContextTags.Invite).performClick()
        assertEquals(GroupContextIntent.OpenInvite, intent)
        onNodeWithTag(GroupContextTags.Roles).assertDoesNotExist()
    }
    @Test fun `owner can manage roles`() = runComposeUiTest { context(owner); onNodeWithTag(GroupContextTags.Roles).assertExists() }
    @Test fun `role refresh removes stale actions`() = runComposeUiTest {
        var state by mutableStateOf(owner); setContent { SaqzTheme { GroupContextScreen(state) {} } }
        onNodeWithTag(GroupContextTags.Roles).assertExists(); state = athlete; waitForIdle(); onNodeWithTag(GroupContextTags.Roles).assertDoesNotExist()
    }
    @Test fun `group switch emits typed intent`() = runComposeUiTest { val intents=mutableListOf<GroupContextIntent>(); context(owner,intents::add); onNodeWithTag(GroupContextTags.Switch).performClick(); assertEquals(listOf<GroupContextIntent>(GroupContextIntent.SwitchGroup),intents) }
    @Test fun `settings action emits typed intent`() = runComposeUiTest { val intents=mutableListOf<GroupContextIntent>(); context(owner,intents::add); onNodeWithTag(GroupContextTags.Settings).performClick(); assertEquals(listOf<GroupContextIntent>(GroupContextIntent.OpenSettings),intents) }
    @Test fun `settings fields emit typed values`() = runComposeUiTest { val intents=mutableListOf<GroupSettingsIntent>(); settings(onIntent=intents::add); onAllNodes(hasSetTextAction(),true)[0].performTextInput("New"); onAllNodes(hasSetTextAction(),true)[1].performTextInput("America/Sao_Paulo"); assertEquals(listOf<GroupSettingsIntent>(GroupSettingsIntent.UpdateName("New"),GroupSettingsIntent.UpdateTimeZone("America/Sao_Paulo")),intents) }
    @Test fun `settings save emits typed intent`() = runComposeUiTest { val intents=mutableListOf<GroupSettingsIntent>(); settings(onIntent=intents::add); onNodeWithTag(GroupContextTags.Save).performClick(); assertEquals(listOf<GroupSettingsIntent>(GroupSettingsIntent.Save),intents) }
    @Test fun `settings loading prevents duplicate save`() = runComposeUiTest { settings(owner.copy(isLoading=true)); onNodeWithTag(GroupContextTags.Save).assertIsNotEnabled() }
    @Test fun `version conflict asks for typed reload`() = runComposeUiTest { val intents=mutableListOf<GroupSettingsIntent>(); settings(owner.copy(versionConflict=true),onIntent=intents::add); onNodeWithText("O grupo mudou. Recarregue antes de salvar").assertExists(); onNodeWithTag(GroupContextTags.Reload).performClick(); assertEquals(listOf<GroupSettingsIntent>(GroupSettingsIntent.Reload),intents) }
    @Test fun `logout emits typed request`() = runComposeUiTest { val intents=mutableListOf<GroupContextIntent>(); context(owner,intents::add); onNodeWithTag(GroupContextTags.Logout).performClick(); assertEquals(listOf<GroupContextIntent>(GroupContextIntent.RequestLogout),intents) }
    @Test fun `logout dialog emits confirmation intent`() = runComposeUiTest { val intents=mutableListOf<LogoutConfirmationIntent>(); setContent { SaqzTheme { LogoutConfirmationDialog(intents::add) } }; onNodeWithTag(GroupContextTags.LogoutConfirm).performClick(); assertEquals(listOf<LogoutConfirmationIntent>(LogoutConfirmationIntent.Confirm),intents) }

    private fun androidx.compose.ui.test.ComposeUiTest.context(state: GroupAdministrationState,onIntent:(GroupContextIntent)->Unit={}) = setContent { SaqzTheme { GroupContextScreen(state,onIntent) } }
    private fun androidx.compose.ui.test.ComposeUiTest.settings(state: GroupAdministrationState=owner,onIntent:(GroupSettingsIntent)->Unit={}) = setContent { SaqzTheme { GroupSettingsScreen(GroupSettingsUiState(state,"",""),onIntent) } }
    private companion object {
        val group=VersionedGroupDto(GroupDto("id","Current Group","America/Sao_Paulo",1,GroupRoleDto.OWNER),"etag")
        val owner=GroupAdministrationState(group=group,actions=GroupActions(true,true,true))
        val admin=GroupAdministrationState(group=group.copy(group=group.group.copy(role=GroupRoleDto.ADMIN)),actions=GroupActions(true,false,true))
        val athlete=GroupAdministrationState(group=group.copy(group=group.group.copy(role=GroupRoleDto.ATHLETE)),actions=GroupActions(false,false,false))
    }
}
