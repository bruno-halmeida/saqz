package br.com.saqz.groups.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.presentation.*
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupContextScreensTest {
    @Test fun `settings fields emit typed values`() = runComposeUiTest { val intents=mutableListOf<GroupSettingsIntent>(); settings(onIntent=intents::add); onAllNodes(hasSetTextAction(),true)[0].performTextInput("New"); onAllNodes(hasSetTextAction(),true)[1].performTextInput("America/Sao_Paulo"); assertEquals(listOf<GroupSettingsIntent>(GroupSettingsIntent.UpdateName("New"),GroupSettingsIntent.UpdateTimeZone("America/Sao_Paulo")),intents) }
    @Test fun `settings save emits typed intent`() = runComposeUiTest { val intents=mutableListOf<GroupSettingsIntent>(); settings(onIntent=intents::add); onNodeWithTag(GroupSettingsTags.Save).performClick(); assertEquals(listOf<GroupSettingsIntent>(GroupSettingsIntent.Save),intents) }
    @Test fun `settings loading prevents duplicate save`() = runComposeUiTest { settings(owner.copy(isLoading=true)); onNodeWithTag(GroupSettingsTags.Save).assertIsNotEnabled() }
    @Test fun `version conflict asks for typed reload`() = runComposeUiTest { val intents=mutableListOf<GroupSettingsIntent>(); settings(owner.copy(versionConflict=true),onIntent=intents::add); onNodeWithText("O grupo mudou. Recarregue antes de salvar").assertExists(); onNodeWithTag(GroupSettingsTags.Reload).performClick(); assertEquals(listOf<GroupSettingsIntent>(GroupSettingsIntent.Reload),intents) }
    @Test fun `logout dialog emits confirmation intent`() = runComposeUiTest { val intents=mutableListOf<LogoutConfirmationIntent>(); setContent { SaqzTheme { LogoutConfirmationDialog(intents::add) } }; onNodeWithTag(GroupSettingsTags.LogoutConfirm).performClick(); assertEquals(listOf<LogoutConfirmationIntent>(LogoutConfirmationIntent.Confirm),intents) }

    private fun androidx.compose.ui.test.ComposeUiTest.settings(state: GroupAdministrationState=owner,onIntent:(GroupSettingsIntent)->Unit={}) = setContent { SaqzTheme { GroupSettingsScreen(GroupSettingsUiState(state,"",""),onIntent) } }
    private companion object {
        val group=VersionedGroup(Group("id","Current Group","America/Sao_Paulo",1,GroupRole.OWNER),"etag")
        val owner=GroupAdministrationState(group=group,actions=GroupActions(true,true,true))
    }
}
