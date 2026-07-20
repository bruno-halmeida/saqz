package br.com.saqz.composeapp.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.VersionedGroupDto
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupsNavigationHostTest {
    @Test
    fun `owner home exposes people games and organizer finance`() = runComposeUiTest {
        host(owner, access(GroupRoleDto.OWNER))
        onNodeWithText("Pessoas").assertExists()
        onNodeWithText("Jogos").assertExists()
        onNodeWithText("Financeiro").assertExists()
        onNodeWithText("Minhas cobranças").assertDoesNotExist()
    }

    @Test
    fun `athlete home hides people and exposes own charges label`() = runComposeUiTest {
        host(athlete, access(GroupRoleDto.ATHLETE))
        onNodeWithText("Pessoas").assertDoesNotExist()
        onNodeWithText("Jogos").assertExists()
        onNodeWithText("Minhas cobranças").assertExists()
        onNodeWithText("Financeiro").assertDoesNotExist()
    }

    @Test
    fun `incomplete organizer receives readable warning and completion action`() = runComposeUiTest {
        val group = owner.copy(profileStatus = GroupProfileStatusDto.INCOMPLETE)
        host(group, access(GroupRoleDto.OWNER, complete = false).copy(destination = GroupsDestination.HOME))
        onNodeWithText("precisa ser concluído", substring = true).assertExists()
        onNodeWithText("Concluir perfil").assertExists()
    }

    @Test
    fun `games action emits one typed navigation intent`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(owner, access(GroupRoleDto.OWNER), intents)
        onNodeWithText("Jogos").performClick()
        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenGames), intents)
    }

    @Test
    fun `athlete finance action emits role neutral intent for policy resolution`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(athlete, access(GroupRoleDto.ATHLETE), intents)
        onNodeWithText("Minhas cobranças").performClick()
        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenFinance), intents)
    }

    @Test
    fun `route page returns to exact selected group home`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(owner, access(GroupRoleDto.OWNER).copy(destination = GroupsDestination.GAMES), intents)
        onNodeWithTag(GroupsNavigationTags.Games).assertExists()
        onNodeWithText("Voltar ao grupo").performClick()
        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenHome), intents)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.host(
        group: GroupDto,
        navigation: GroupsNavigationState,
        intents: MutableList<GroupsNavigationIntent> = mutableListOf(),
    ) = setContent {
        SaqzTheme {
            GroupsNavigationHost(
                navigation = navigation,
                administration = GroupAdministrationState(
                    group = VersionedGroupDto(group, "etag"),
                    actions = GroupActions(
                        canEditSettings = group.role != GroupRoleDto.ATHLETE,
                        canManageRoles = group.role != GroupRoleDto.ATHLETE,
                        canManageInvite = group.role != GroupRoleDto.ATHLETE,
                    ),
                ),
                onNavigationIntent = intents::add,
                onOpenSettings = {},
                onSwitchGroup = {},
                onRequestLogout = {},
            )
        }
    }

    private fun access(role: GroupRoleDto, complete: Boolean = true) = GroupsNavigationState(
        destination = if (!complete && role != GroupRoleDto.ATHLETE) {
            GroupsDestination.PROFILE_COMPLETION
        } else {
            GroupsDestination.HOME
        },
        groupId = "group-1",
        access = GroupsNavigationAccess(
            showPeople = role != GroupRoleDto.ATHLETE,
            showGames = true,
            showFinance = true,
            canCompleteProfile = !complete && role != GroupRoleDto.ATHLETE,
            canMutateOperations = complete && role != GroupRoleDto.ATHLETE,
            financeDestination = if (role == GroupRoleDto.ATHLETE) {
                GroupsDestination.OWN_CHARGES
            } else {
                GroupsDestination.FINANCE
            },
        ),
    )

    private companion object {
        val owner = group(GroupRoleDto.OWNER)
        val athlete = group(GroupRoleDto.ATHLETE)

        fun group(role: GroupRoleDto) = GroupDto(
            id = "group-1",
            name = "Private Group",
            timeZone = "America/Sao_Paulo",
            version = 1,
            role = role,
        )
    }
}
