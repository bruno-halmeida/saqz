package br.com.saqz.groups.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailState
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.presentation.navigation.bottomMenuDestination
import br.com.saqz.groups.presentation.navigation.showsGroupChrome
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_back
import br.com.saqz.groups.resources.groups_complete_profile
import br.com.saqz.groups.resources.groups_finance
import br.com.saqz.groups.resources.groups_game_detail
import br.com.saqz.groups.resources.groups_games
import br.com.saqz.groups.resources.groups_more_options
import br.com.saqz.groups.resources.groups_own_charges
import br.com.saqz.groups.resources.groups_people
import br.com.saqz.groups.resources.groups_title
import br.com.saqz.groups.resources.material_arrow_back
import br.com.saqz.groups.resources.material_calendar
import br.com.saqz.groups.resources.material_group_add
import br.com.saqz.groups.resources.material_home
import br.com.saqz.groups.resources.material_more_horiz
import br.com.saqz.groups.resources.material_payments
import br.com.saqz.groups.resources.nav_home
import br.com.saqz.designsystem.component.SaqzBottomNav
import br.com.saqz.designsystem.component.SaqzBottomNavItem
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.component.SaqzTopBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.ui.games.detail.GameDetailScreen
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GroupsRouteChrome(
    navigation: GroupsNavigationState,
    administration: GroupAdministrationState,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        GroupTopBar(
            title = groupRouteTitle(navigation.destination, administration.group?.group?.name.orEmpty()),
            onBack = when {
                navigation.destination != GroupsDestination.HOME -> {
                    { onNavigationIntent(GroupsNavigationIntent.OpenHome) }
                }
                navigation.memberships.size > 1 -> {
                    { onNavigationIntent(GroupsNavigationIntent.OpenGroups) }
                }
                else -> null
            },
            onOpenSettings = onOpenSettings.takeIf {
                navigation.destination == GroupsDestination.HOME && administration.actions.canEditSettings
            },
        )
        Box(Modifier.weight(1f)) { content() }
        GroupBottomMenu(navigation, onNavigationIntent)
    }
}

@Composable
fun GroupsDestinationContent(
    navigation: GroupsNavigationState,
    administration: GroupAdministrationState,
    groupPhotoState: GroupPhotoState,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
    gameDetailState: GameDetailState?,
    onGameDetailIntent: (GameDetailIntent) -> Unit,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onRetryGroup: () -> Unit,
    onOpenInvite: () -> Unit,
    onRequestLogout: () -> Unit,
) {
    when (navigation.destination) {
        GroupsDestination.SETUP -> Unit
        GroupsDestination.SELECTOR -> GroupsListScreen(
            memberships = navigation.memberships,
            onSelectGroup = onSelectGroup,
            onOpenCreateGroup = onOpenCreateGroup,
        )
        GroupsDestination.LOADING -> SaqzLoadingState(
            Modifier.fillMaxSize().testTag("group-loading"),
        )
        GroupsDestination.LOAD_ERROR -> GroupLoadError(onRetryGroup)
        GroupsDestination.HOME -> administration.group?.group?.let { group ->
            GroupDetailScreen(
                group = group,
                administration = administration,
                navigation = navigation,
                groupPhotoState = groupPhotoState,
                groupPhotoPreview = groupPhotoPreview,
                onNavigationIntent = onNavigationIntent,
                onOpenSettings = onOpenSettings,
                onOpenInvite = onOpenInvite,
                onRequestLogout = onRequestLogout,
            )
        }
        GroupsDestination.PROFILE_COMPLETION -> RoutePage(
            title = stringResource(Res.string.groups_complete_profile),
            body = "Complete modalidade e composição antes de criar jogos ou alterar presença e finanças.",
            tag = GroupsNavigationTags.ProfileCompletion,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.PEOPLE -> RoutePage(
            title = stringResource(Res.string.groups_people),
            body = "Gerencie participantes, funções e convites privados.",
            tag = GroupsNavigationTags.People,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.GAMES -> RoutePage(
            title = stringResource(Res.string.groups_games),
            body = if (navigation.access.canMutateOperations) {
                "Consulte jogos e crie novas partidas."
            } else {
                "Consulte os jogos deste grupo."
            },
            tag = GroupsNavigationTags.Games,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.GAME_DETAIL -> if (gameDetailState == null) {
            RoutePage(
                title = "Detalhes do jogo",
                body = "Jogo ${navigation.gameId.orEmpty()}",
                tag = GroupsNavigationTags.GameDetail,
                onNavigationIntent = onNavigationIntent,
            )
        } else {
            Box(Modifier.fillMaxSize().testTag(GroupsNavigationTags.GameDetail)) {
                GameDetailScreen(gameDetailState, onGameDetailIntent)
            }
        }
        GroupsDestination.FINANCE -> RoutePage(
            title = stringResource(Res.string.groups_finance),
            body = "Cobranças e despesas são registros manuais; o Saqz não processa pagamentos.",
            tag = GroupsNavigationTags.Finance,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.OWN_CHARGES -> RoutePage(
            title = stringResource(Res.string.groups_own_charges),
            body = "Somente as suas cobranças são exibidas.",
            tag = GroupsNavigationTags.OwnCharges,
            onNavigationIntent = onNavigationIntent,
        )
    }
}

@Composable
private fun GroupTopBar(
    title: String,
    onBack: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
) {
    SaqzTopBar(
        title = title,
        onBack = onBack,
        trailingContent = onOpenSettings?.let {
            {
                CompactAction(
                    Res.drawable.material_more_horiz,
                    stringResource(Res.string.groups_more_options),
                    "groups-settings",
                    it,
                )
            }
        },
    )
}

@Composable
private fun GroupBottomMenu(
    navigation: GroupsNavigationState,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
) {
    val selected = navigation.destination.bottomMenuDestination()
    val colors = SaqzTheme.colors
    val items = buildList {
        add(
            SaqzBottomNavItem(
                label = stringResource(Res.string.nav_home),
                selected = selected == GroupsDestination.HOME,
                onClick = { onNavigationIntent(GroupsNavigationIntent.OpenHome) },
                icon = {
                    MaterialIcon(
                        Res.drawable.material_home,
                        if (selected == GroupsDestination.HOME) colors.primary else colors.textSecondary,
                        20.dp,
                    )
                },
            ),
        )
        if (navigation.access.showGames) {
            add(
                SaqzBottomNavItem(
                    label = stringResource(Res.string.groups_games),
                    selected = selected == GroupsDestination.GAMES,
                    onClick = { onNavigationIntent(GroupsNavigationIntent.OpenGames) },
                    icon = {
                        MaterialIcon(
                            Res.drawable.material_calendar,
                            if (selected == GroupsDestination.GAMES) colors.primary else colors.textSecondary,
                            20.dp,
                        )
                    },
                ),
            )
        }
        if (navigation.access.showPeople) {
            add(
                SaqzBottomNavItem(
                    label = stringResource(Res.string.groups_people),
                    selected = selected == GroupsDestination.PEOPLE,
                    onClick = { onNavigationIntent(GroupsNavigationIntent.OpenPeople) },
                    icon = {
                        MaterialIcon(
                            Res.drawable.material_group_add,
                            if (selected == GroupsDestination.PEOPLE) colors.primary else colors.textSecondary,
                            20.dp,
                        )
                    },
                ),
            )
        }
        if (navigation.access.showFinance) {
            val financeDestination = navigation.access.financeDestination
            add(
                SaqzBottomNavItem(
                    label = stringResource(
                        if (financeDestination == GroupsDestination.OWN_CHARGES) {
                            Res.string.groups_own_charges
                        } else {
                            Res.string.groups_finance
                        },
                    ),
                    selected = selected == financeDestination,
                    onClick = { onNavigationIntent(GroupsNavigationIntent.OpenFinance) },
                    icon = {
                        MaterialIcon(
                            Res.drawable.material_payments,
                            if (selected == financeDestination) colors.primary else colors.textSecondary,
                            20.dp,
                        )
                    },
                ),
            )
        }
    }
    SaqzBottomNav(
        items = items,
        modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.BottomMenu),
    )
}

@Composable
private fun groupRouteTitle(destination: GroupsDestination, groupName: String): String = when (destination) {
    GroupsDestination.HOME -> groupName
    GroupsDestination.PROFILE_COMPLETION -> stringResource(Res.string.groups_complete_profile)
    GroupsDestination.PEOPLE -> stringResource(Res.string.groups_people)
    GroupsDestination.GAMES -> stringResource(Res.string.groups_games)
    GroupsDestination.GAME_DETAIL -> stringResource(Res.string.groups_game_detail)
    GroupsDestination.FINANCE -> stringResource(Res.string.groups_finance)
    GroupsDestination.OWN_CHARGES -> stringResource(Res.string.groups_own_charges)
    GroupsDestination.SETUP,
    GroupsDestination.SELECTOR,
    GroupsDestination.LOADING,
    GroupsDestination.LOAD_ERROR,
    -> stringResource(Res.string.groups_title)
}

@Composable
private fun CompactAction(
    icon: DrawableResource,
    description: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).testTag(tag),
    ) {
        MaterialIcon(icon, SaqzTheme.colors.primary, 24.dp, description)
    }
}

@Composable
private fun MaterialIcon(
    resource: DrawableResource,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    description: String? = null,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size),
    )
}