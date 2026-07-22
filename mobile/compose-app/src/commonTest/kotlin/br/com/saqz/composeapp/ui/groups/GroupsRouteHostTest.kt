package br.com.saqz.composeapp.ui.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.component.SaqzTopBarBackTag
import br.com.saqz.designsystem.component.SaqzTopBarTag
import br.com.saqz.designsystem.component.SaqzTopBarTitleTag
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
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationAccess
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.network.SessionMembershipDto
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupsRouteHostTest {
    @Test
    fun `selected group home renders its top bar without bottom menu`() = runComposeUiTest {
        host(owner, access(GroupRoleDto.OWNER))

        onNodeWithTag(SaqzTopBarTag).assertIsDisplayed()
        onNodeWithTag(SaqzTopBarTitleTag).assertTextEquals("Private Group")
        onNodeWithTag(GroupsNavigationTags.BottomMenu).assertDoesNotExist()
    }

    @Test
    fun `game detail shows its exact title without bottom menu`() = runComposeUiTest {
        host(
            owner,
            access(GroupRoleDto.OWNER).copy(
                destination = GroupsDestination.GAME_DETAIL,
                gameId = "game-1",
            ),
        )

        onNodeWithTag(SaqzTopBarTitleTag).assertTextEquals("Detalhes do jogo")
        onNodeWithTag(GroupsNavigationTags.BottomMenu).assertDoesNotExist()
    }

    @Test
    fun `every group scoped destination omits bottom menu`() = runComposeUiTest {
        val destination = mutableStateOf(GroupsDestination.HOME)
        setContent {
            SaqzTheme {
                GroupsRouteHost(
                    navigation = access(GroupRoleDto.OWNER).copy(destination = destination.value),
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

        listOf(
            GroupsDestination.HOME,
            GroupsDestination.PROFILE_COMPLETION,
            GroupsDestination.PEOPLE,
            GroupsDestination.GAMES,
            GroupsDestination.GAME_DETAIL,
            GroupsDestination.FINANCE,
            GroupsDestination.OWN_CHARGES,
            GroupsDestination.NOTICES,
            GroupsDestination.MORE,
        ).forEach { route ->
            runOnIdle { destination.value = route }
            onNodeWithTag(GroupsNavigationTags.BottomMenu).assertDoesNotExist()
        }
    }

    @Test
    fun `bottom menu emits each fixed tab intent once`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(
            owner,
            GroupsNavigationState(
                destination = GroupsDestination.SELECTOR,
                memberships = sessionGroups,
            ),
            intents,
        )

        onNodeWithTag("saqz-bottom-nav-item-0").performClick()
        onNodeWithTag("saqz-bottom-nav-item-1").performClick()
        onNodeWithTag("saqz-bottom-nav-item-2").performClick()
        onNodeWithTag("saqz-bottom-nav-item-3").performClick()

        assertEquals(
            listOf(
                GroupsNavigationIntent.OpenHome,
                GroupsNavigationIntent.OpenGroups,
                GroupsNavigationIntent.OpenNotices,
                GroupsNavigationIntent.OpenMore,
            ),
            intents,
        )
    }

    @Test
    fun `group list bottom menu renders four fixed tabs`() = runComposeUiTest {
        host(
            athlete,
            GroupsNavigationState(
                destination = GroupsDestination.SELECTOR,
                memberships = sessionGroups,
            ),
        )

        onNodeWithTag("saqz-bottom-nav-item-0").assertTextEquals("Início")
        onNodeWithTag("saqz-bottom-nav-item-1").assertTextEquals("Grupos")
        onNodeWithTag("saqz-bottom-nav-item-2").assertTextEquals("Avisos")
        onNodeWithTag("saqz-bottom-nav-item-3").assertTextEquals("Mais")
    }

    @Test
    fun `notices destination shows the placeholder without bottom menu`() = runComposeUiTest {
        host(owner, access(GroupRoleDto.OWNER).copy(destination = GroupsDestination.NOTICES))

        onNodeWithTag(SaqzTopBarTitleTag).assertTextEquals("Avisos")
        onNodeWithTag(GroupsNavigationTags.BottomMenu).assertDoesNotExist()
        onNodeWithTag(GroupsNavigationTags.NoticesScreen).assertExists()
        onNodeWithText("Os avisos do grupo aparecerão nesta área em breve.").assertExists()
    }

    @Test
    fun `more destination lists owner shortcuts and emits typed intents`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(owner, access(GroupRoleDto.OWNER).copy(destination = GroupsDestination.MORE), intents)

        onNodeWithTag(GroupsNavigationTags.BottomMenu).assertDoesNotExist()
        onNodeWithTag(GroupsNavigationTags.MorePeople).performClick()
        onNodeWithTag(GroupsNavigationTags.MoreFinance).performClick()

        assertEquals(
            listOf(GroupsNavigationIntent.OpenPeople, GroupsNavigationIntent.OpenFinance),
            intents,
        )
    }

    @Test
    fun `more destination hides people and labels own charges for athletes`() = runComposeUiTest {
        host(athlete, access(GroupRoleDto.ATHLETE).copy(destination = GroupsDestination.MORE))

        onNodeWithTag(GroupsNavigationTags.MorePeople).assertDoesNotExist()
        onNodeWithText("Minhas cobranças").assertExists()
        onNodeWithText("Financeiro").assertDoesNotExist()
    }

    @Test
    fun `group list renders its top bar and selected groups menu`() = runComposeUiTest {
        host(
            owner,
            GroupsNavigationState(
                destination = GroupsDestination.SELECTOR,
                memberships = sessionGroups,
            ),
        )

        onNodeWithTag(SaqzTopBarTag).assertIsDisplayed()
        onNodeWithTag(SaqzTopBarTitleTag).assertTextEquals("Meus grupos")
        onNodeWithTag(SaqzTopBarBackTag).assertDoesNotExist()
        onNodeWithTag(GroupsNavigationTags.BottomMenu).assertIsDisplayed()
        onNodeWithTag("saqz-bottom-nav-item-0").assertIsNotSelected()
        onNodeWithTag("saqz-bottom-nav-item-1").assertIsSelected()
        assertEquals(
            4,
            onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            onNodeWithTag(GroupsNavigationTags.List).getUnclippedBoundsInRoot().bottom,
            onNodeWithTag(GroupsNavigationTags.BottomMenu).getUnclippedBoundsInRoot().top,
        )
    }

    @Test
    fun `nested route top bar returns to group home once`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(
            owner,
            access(GroupRoleDto.OWNER).copy(destination = GroupsDestination.GAMES),
            intents,
        )

        onNodeWithTag(SaqzTopBarBackTag).performClick()

        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenHome), intents)
    }

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

        onNodeWithText("Meus grupos").assertExists()
        onNodeWithText("Private Group").assertExists()
        onNodeWithText("Organizador").assertExists()
        onNodeWithText("PG").assertExists()
        onNodeWithTag("${GroupsNavigationTags.ListItemPrefix}next").assertHeightIsAtLeast(48.dp)
        onNodeWithText("Next").performClick()
        assertEquals(listOf("next"), selectedGroups)
    }

    @Test
    fun `group list card follows the reference hierarchy`() = runComposeUiTest {
        host(
            group = owner,
            navigation = GroupsNavigationState(
                destination = GroupsDestination.SELECTOR,
                memberships = listOf(sessionGroups.first()),
            ),
        )

        onNodeWithTag("${GroupsNavigationTags.ListItemPrefix}group-1")
            .assertHeightIsAtLeast(140.dp)
        onNodeWithTag(GroupsNavigationTags.ListPhoto, useUnmergedTree = true)
            .assertWidthIsEqualTo(88.dp)
            .assertHeightIsEqualTo(88.dp)
        onNodeWithText("Private Group").assertExists()
        onNodeWithText("Organizador").assertExists()
        onNodeWithText("Grupo privado").assertExists()
        onNodeWithText("Local ainda não definido").assertExists()
        onNodeWithText("Horário ainda não definido").assertExists()
        onNodeWithText("Próximo jogo").assertExists()
    }

    @Test
    fun `compact large text keeps group card content visible`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.size(320.dp, 420.dp)) {
                    SaqzTheme {
                        GroupsRouteHost(
                            navigation = GroupsNavigationState(
                                destination = GroupsDestination.SELECTOR,
                                memberships = listOf(sessionGroups.first()),
                            ),
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

        onNodeWithTag("${GroupsNavigationTags.ListItemPrefix}group-1").performScrollTo()
        onNodeWithText("Private Group").assertIsDisplayed()
        onNodeWithText("Organizador").assertIsDisplayed()
        onNodeWithText("Próximo jogo").assertIsDisplayed()
        onNodeWithContentDescription("Mais opções").assertDoesNotExist()
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
        onNodeWithTag(GroupsNavigationTags.Shortcuts).assertExists()
        onNodeWithTag(GroupsNavigationTags.ShortcutGames).assertHeightIsAtLeast(56.dp)
        onNodeWithTag(GroupsNavigationTags.ShortcutPeople).assertHeightIsAtLeast(56.dp)
        onNodeWithTag(GroupsNavigationTags.ShortcutFinance).assertHeightIsAtLeast(56.dp)
        onNodeWithTag(GroupsNavigationTags.ShortcutSettings).assertHeightIsAtLeast(56.dp)
        onNodeWithTag(GroupsNavigationTags.Notices).assertExists()
        onNodeWithText("Nenhum aviso recente").assertExists()
        onNodeWithText("Membros").assertExists()
        onNodeWithContentDescription("Ana Lima").assertExists()
        onNodeWithText("Convidar mais gente").assertExists()
    }

    @Test
    fun `unloaded private data renders truthful fallback states`() = runComposeUiTest {
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER),
            photoState = GroupPhotoState(groupId = owner.id),
        )

        onNodeWithText("Local ainda não definido").assertExists()
        onNodeWithText("Horário ainda não definido").assertExists()
        onNodeWithText("Nenhum jogo agendado").assertExists()
        onNodeWithText("Nenhum aviso recente").assertExists()
        onNodeWithText("Participantes ainda não carregados").assertExists()
    }

    @Test
    fun `matching group loading renders a stable 104 dp skeleton`() = runComposeUiTest {
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER),
            photoState = GroupPhotoState(groupId = owner.id, stage = GroupPhotoStage.LOADING),
        )

        onNodeWithTag(GroupsNavigationTags.SummaryPhoto)
            .assertWidthIsEqualTo(104.dp)
            .assertHeightIsEqualTo(104.dp)
        onNodeWithTag(GroupsNavigationTags.SummaryPhotoSkeleton).assertExists()
        onNodeWithTag(GroupsNavigationTags.SummaryPhotoFallback).assertDoesNotExist()
    }

    @Test
    fun `valid photo occupies the stable crop slot`() = runComposeUiTest {
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER),
            photoState = photoState(owner.id),
            photoPreview = { _, modifier ->
                Box(modifier)
                GroupPhotoRenderState.SUCCESS
            },
        )

        onNodeWithTag(GroupsNavigationTags.SummaryPhoto)
            .assertWidthIsEqualTo(104.dp)
            .assertHeightIsEqualTo(104.dp)
        onNodeWithTag(GroupsNavigationTags.SummaryPhotoImage).assertExists()
        onNodeWithTag(GroupsNavigationTags.SummaryPhotoSkeleton).assertDoesNotExist()
        onNodeWithTag(GroupsNavigationTags.SummaryPhotoFallback).assertDoesNotExist()
    }

    @Test
    fun `decode failure falls back to initials without visible error`() = runComposeUiTest {
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER),
            photoState = photoState(owner.id),
            photoPreview = { _, _ -> GroupPhotoRenderState.FAILURE },
        )

        onNodeWithTag(GroupsNavigationTags.SummaryPhotoFallback).assertExists()
        onNodeWithText("PG").assertExists()
        onNodeWithTag(GroupsNavigationTags.SummaryPhotoSkeleton).assertDoesNotExist()
    }

    @Test
    fun `photo from another group is never composed while selection reconciles`() = runComposeUiTest {
        val rendered = mutableListOf<GroupPhotoPreviewHandle>()
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER),
            photoState = photoState("previous-group"),
            photoPreview = { handle, _ ->
                rendered += handle
                GroupPhotoRenderState.SUCCESS
            },
        )

        onNodeWithTag(GroupsNavigationTags.SummaryPhotoSkeleton).assertExists()
        onNodeWithTag(GroupsNavigationTags.SummaryPhotoImage).assertDoesNotExist()
        assertEquals(emptyList(), rendered)
    }

    @Test
    fun `multi group detail returns to group list`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER).copy(memberships = sessionGroups),
            intents = intents,
        )

        onNodeWithTag(SaqzTopBarBackTag).performClick()

        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenGroups), intents)
    }

    @Test
    fun `single group detail has no return to list action`() = runComposeUiTest {
        host(
            group = owner,
            navigation = access(GroupRoleDto.OWNER).copy(memberships = sessionGroups.take(1)),
        )

        onNodeWithTag(SaqzTopBarBackTag).assertDoesNotExist()
    }

    @Test
    fun `compact large text keeps invitation action reachable at 48 dp`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.size(320.dp, 420.dp)) {
                    SaqzTheme {
                        GroupsRouteHost(
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
        onNodeWithTag(SaqzTopBarTag).assertIsDisplayed()
        onNodeWithTag(GroupsNavigationTags.BottomMenu).assertDoesNotExist()
    }

    @Test
    fun `owner home exposes people games and organizer finance`() = runComposeUiTest {
        host(owner, access(GroupRoleDto.OWNER))
        onNodeWithContentDescription("Pessoas").assertExists()
        onNodeWithTag(GroupsNavigationTags.ShortcutGames).assertExists()
        onNodeWithContentDescription("Financeiro").assertExists()
        onNodeWithContentDescription("Minhas cobranças").assertDoesNotExist()
    }

    @Test
    fun `athlete home hides people and exposes own charges label`() = runComposeUiTest {
        host(athlete, access(GroupRoleDto.ATHLETE))
        onNodeWithContentDescription("Pessoas").assertDoesNotExist()
        onNodeWithTag(GroupsNavigationTags.ShortcutGames).assertExists()
        onNodeWithContentDescription("Minhas cobranças").assertExists()
        onNodeWithContentDescription("Financeiro").assertDoesNotExist()
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
        onNodeWithTag(GroupsNavigationTags.ShortcutGames).performClick()
        assertEquals(listOf<GroupsNavigationIntent>(GroupsNavigationIntent.OpenGames), intents)
    }

    @Test
    fun `athlete finance action emits role neutral intent for policy resolution`() = runComposeUiTest {
        val intents = mutableListOf<GroupsNavigationIntent>()
        host(athlete, access(GroupRoleDto.ATHLETE), intents)
        onNodeWithTag(GroupsNavigationTags.ShortcutFinance).performClick()
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
        photoState: GroupPhotoState = GroupPhotoState(),
        photoPreview: (@Composable (
            GroupPhotoPreviewHandle,
            Modifier,
        ) -> GroupPhotoRenderState)? = null,
    ) = setContent {
        SaqzTheme {
            GroupsRouteHost(
                navigation = navigation,
                administration = administration(group, groupMembers),
                groupPhotoState = photoState,
                groupPhotoPreview = photoPreview,
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
        fun photoState(groupId: String) = GroupPhotoState(
            groupId = groupId,
            existing = ExistingGroupPhoto(GroupPhotoPreviewHandle("preview"), "photo-etag"),
        )

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
