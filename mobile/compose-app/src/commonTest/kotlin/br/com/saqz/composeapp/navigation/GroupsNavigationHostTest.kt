package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupProfileDto
import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.data.GroupRegularSlotDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.GroupVenueDto
import br.com.saqz.groups.data.GroupWeekdayDto
import br.com.saqz.groups.data.MembershipDto
import br.com.saqz.groups.data.VersionedGroupDto
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.network.SessionMembershipDto
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupsNavigationHostTest {
    @Test
    fun `multiple groups render the complete list and select the exact row`() = runComposeUiTest {
        val selectedGroups = mutableListOf<String>()
        host(
            group = owner,
            navigation = GroupsNavigationState(
                destination = GroupsDestination.SELECTOR,
                memberships = sessionGroups,
            ),
            selectedGroups = selectedGroups,
        )

        onNodeWithText("Grupos").assertExists()
        onNodeWithText("Private Group").assertExists()
        onNodeWithText("Organizador").assertExists()
        onNodeWithText("PG").assertExists()
        onNodeWithTag("${GroupsNavigationTags.ListItemPrefix}next").assertHeightIsAtLeast(48.dp)
        onNodeWithText("Next").performClick()
        assertEquals(listOf("next"), selectedGroups)
    }

    @Test
    fun `group detail follows the reference hierarchy with truthful empty states`() = runComposeUiTest {
        host(
            group = ownerWithRoutine,
            navigation = access(GroupRoleDto.OWNER),
            groupMembers = listOf(MembershipDto("person-1", "Ana Lima", GroupRoleDto.ATHLETE)),
        )

        onNodeWithTag(GroupsNavigationTags.Summary).assertExists()
        onNodeWithText("Quadra do Parque").assertExists()
        onNodeWithText("Ter • 19:30").assertExists()
        onNodeWithText("Próximo jogo").assertExists()
        onNodeWithText("Nenhum jogo agendado").assertExists()
        onNodeWithText("Atalhos").assertExists()
        onNodeWithText("Avisos").assertExists()
        onNodeWithText("Nenhum aviso recente").assertExists()
        onNodeWithText("Membros").assertExists()
        onNodeWithText("Ana Lima").assertExists()
        onNodeWithText("Convidar mais gente").assertExists()
    }

    @Test
    fun `unloaded private data renders truthful fallback states`() = runComposeUiTest {
        host(group = owner, navigation = access(GroupRoleDto.OWNER))

        onNodeWithText("Local ainda não definido").assertExists()
        onNodeWithText("Horário ainda não definido").assertExists()
        onNodeWithText("Nenhum jogo agendado").assertExists()
        onNodeWithText("Nenhum aviso recente").assertExists()
        onNodeWithText("Participantes ainda não carregados").assertExists()
    }

    @Test
    fun `multi group detail returns to group list`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER).copy(memberships = sessionGroups),
            intents = intents,
        )

        onNodeWithTag(GroupsNavigationTags.BackToList).performClick()

        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenGroups), intents)
    }

    @Test
    fun `single group detail has no return to list action`() = runComposeUiTest {
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER).copy(memberships = sessionGroups.take(1)),
        )

        onNodeWithTag(GroupsNavigationTags.BackToList).assertDoesNotExist()
    }

    @Test
    fun `compact large text keeps invitation action reachable at 48 dp`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.size(320.dp, 420.dp)) {
                    SaqzTheme {
                        GroupsNavigationHost(
                            navigation = access(GroupRoleDto.OWNER),
                            administration = administration(owner),
                            onNavigationIntent = {},
                            onOpenSettings = {},
                            onSelectGroup = {},
                            onOpenCreateGroup = {},
                            onRetryGroup = {},
                            onOpenInvite = {},
                            onRequestLogout = {},
                        )
                    }
                }
            }
        }

        onNodeWithTag(GroupsNavigationTags.Invite)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

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
        onNodeWithTag("groups-settings").assertDoesNotExist()
        onNodeWithTag(GroupsNavigationTags.Invite).assertDoesNotExist()
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
        selectedGroups: MutableList<String> = mutableListOf(),
        groupMembers: List<MembershipDto> = emptyList(),
    ) = setContent {
        SaqzTheme {
            GroupsNavigationHost(
                navigation = navigation,
                administration = administration(group, groupMembers),
                onNavigationIntent = intents::add,
                onOpenSettings = {},
                onSelectGroup = selectedGroups::add,
                onOpenCreateGroup = {},
                onRetryGroup = {},
                onOpenInvite = {},
                onRequestLogout = {},
            )
        }
    }

    private fun administration(group: GroupDto, members: List<MembershipDto> = emptyList()) =
        GroupAdministrationState(
            group = VersionedGroupDto(group, "etag"),
            memberships = members,
            actions = GroupActions(
                canEditSettings = group.role != GroupRoleDto.ATHLETE,
                canManageRoles = group.role != GroupRoleDto.ATHLETE,
                canManageInvite = group.role != GroupRoleDto.ATHLETE,
            ),
        )

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
        val sessionGroups = listOf(
            SessionMembershipDto("group-1", "Private Group", "OWNER"),
            SessionMembershipDto("next", "Next", "ADMIN"),
        )
        val ownerWithRoutine = owner.copy(
            profile = GroupProfileDto(
                defaultVenue = GroupVenueDto(
                    id = "venue-1",
                    name = "Quadra do Parque",
                    address = "Rua das Flores, 10",
                ),
                regularSlots = listOf(
                    GroupRegularSlotDto(
                        id = "slot-1",
                        weekday = GroupWeekdayDto.TUESDAY,
                        startTime = "19:30",
                        durationMinutes = 90,
                    ),
                ),
            ),
        )

        fun group(role: GroupRoleDto) = GroupDto(
            id = "group-1",
            name = "Private Group",
            timeZone = "America/Sao_Paulo",
            version = 1,
            role = role,
        )
    }
}
