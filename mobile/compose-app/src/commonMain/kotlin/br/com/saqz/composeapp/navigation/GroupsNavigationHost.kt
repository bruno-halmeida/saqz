package br.com.saqz.composeapp.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.groups_back_home
import br.com.saqz.composeapp.resources.groups_back
import br.com.saqz.composeapp.resources.groups_choose
import br.com.saqz.composeapp.resources.groups_complete_profile
import br.com.saqz.composeapp.resources.groups_create
import br.com.saqz.composeapp.resources.groups_finance
import br.com.saqz.composeapp.resources.groups_games
import br.com.saqz.composeapp.resources.groups_invite_hint
import br.com.saqz.composeapp.resources.groups_invite_title
import br.com.saqz.composeapp.resources.groups_load_error
import br.com.saqz.composeapp.resources.groups_location_empty
import br.com.saqz.composeapp.resources.groups_logout
import br.com.saqz.composeapp.resources.groups_members
import br.com.saqz.composeapp.resources.groups_members_count
import br.com.saqz.composeapp.resources.groups_members_empty
import br.com.saqz.composeapp.resources.groups_more_options
import br.com.saqz.composeapp.resources.groups_next_game
import br.com.saqz.composeapp.resources.groups_next_game_empty
import br.com.saqz.composeapp.resources.groups_next_game_hint
import br.com.saqz.composeapp.resources.groups_notices
import br.com.saqz.composeapp.resources.groups_notices_empty
import br.com.saqz.composeapp.resources.groups_own_charges
import br.com.saqz.composeapp.resources.groups_people
import br.com.saqz.composeapp.resources.groups_private
import br.com.saqz.composeapp.resources.groups_profile_incomplete
import br.com.saqz.composeapp.resources.groups_retry
import br.com.saqz.composeapp.resources.groups_role_admin
import br.com.saqz.composeapp.resources.groups_role_athlete
import br.com.saqz.composeapp.resources.groups_role_owner
import br.com.saqz.composeapp.resources.groups_schedule_empty
import br.com.saqz.composeapp.resources.groups_schedule_pattern
import br.com.saqz.composeapp.resources.groups_see_games
import br.com.saqz.composeapp.resources.groups_send_invite
import br.com.saqz.composeapp.resources.groups_settings
import br.com.saqz.composeapp.resources.groups_title
import br.com.saqz.composeapp.resources.groups_view_all
import br.com.saqz.composeapp.resources.groups_weekday_friday
import br.com.saqz.composeapp.resources.groups_weekday_monday
import br.com.saqz.composeapp.resources.groups_weekday_saturday
import br.com.saqz.composeapp.resources.groups_weekday_sunday
import br.com.saqz.composeapp.resources.groups_weekday_thursday
import br.com.saqz.composeapp.resources.groups_weekday_tuesday
import br.com.saqz.composeapp.resources.groups_weekday_wednesday
import br.com.saqz.composeapp.resources.material_calendar
import br.com.saqz.composeapp.resources.material_arrow_back
import br.com.saqz.composeapp.resources.material_arrow_forward
import br.com.saqz.composeapp.resources.material_campaign
import br.com.saqz.composeapp.resources.material_group_add
import br.com.saqz.composeapp.resources.material_location_on
import br.com.saqz.composeapp.resources.material_more_horiz
import br.com.saqz.composeapp.resources.material_payments
import br.com.saqz.composeapp.resources.material_settings
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzCard
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.GroupWeekdayDto
import br.com.saqz.groups.data.MembershipDto
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.network.SessionMembershipDto
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal object GroupsNavigationTags {
    const val List = "groups-list"
    const val ListItemPrefix = "groups-list-item-"
    const val Home = "groups-home"
    const val BackToList = "groups-back-to-list"
    const val Summary = "groups-summary"
    const val NextGame = "groups-next-game"
    const val Shortcuts = "groups-shortcuts"
    const val ShortcutGames = "groups-shortcut-games"
    const val ShortcutPeople = "groups-shortcut-people"
    const val ShortcutFinance = "groups-shortcut-finance"
    const val ShortcutSettings = "groups-shortcut-settings"
    const val Notices = "groups-notices"
    const val Members = "groups-members"
    const val Invite = "groups-invite"
    const val ProfileCompletion = "groups-profile-completion"
    const val People = "groups-people"
    const val Games = "groups-games"
    const val GameDetail = "groups-game-detail"
    const val Finance = "groups-finance"
    const val OwnCharges = "groups-own-charges"
}

@Composable
internal fun GroupsNavigationHost(
    navigation: GroupsNavigationState,
    administration: GroupAdministrationState,
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
        GroupsDestination.GAME_DETAIL -> RoutePage(
            title = "Detalhes do jogo",
            body = "Jogo ${navigation.gameId.orEmpty()}",
            tag = GroupsNavigationTags.GameDetail,
            onNavigationIntent = onNavigationIntent,
        )
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
private fun GroupsListScreen(
    memberships: List<SessionMembershipDto>,
    onSelectGroup: (String) -> Unit,
    onOpenCreateGroup: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = SaqzTheme.metrics.horizontalPadding,
                vertical = SaqzTheme.metrics.grid,
            )
            .testTag(GroupsNavigationTags.List),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Text(
            stringResource(Res.string.groups_title),
            style = SaqzTheme.typography.displayMedium,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            stringResource(Res.string.groups_choose),
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary,
        )
        memberships.forEach { membership ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
                    .testTag("${GroupsNavigationTags.ListItemPrefix}${membership.groupId}"),
                onClick = { onSelectGroup(membership.groupId) },
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InitialsAvatar(membership.groupName, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            membership.groupName,
                            style = SaqzTheme.typography.bodyStrong,
                            color = SaqzTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            sessionRoleLabel(membership.role),
                            style = SaqzTheme.typography.caption,
                            color = SaqzTheme.colors.textSecondary,
                        )
                    }
                    Text("›", style = SaqzTheme.typography.lead, color = SaqzTheme.colors.primary)
                }
            }
        }
        SaqzButton(
            label = stringResource(Res.string.groups_create),
            onClick = onOpenCreateGroup,
            modifier = Modifier.fillMaxWidth(),
            variant = SaqzButtonVariant.Secondary,
        )
    }
}

@Composable
private fun GroupDetailScreen(
    group: GroupDto,
    administration: GroupAdministrationState,
    navigation: GroupsNavigationState,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInvite: () -> Unit,
    onRequestLogout: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.grid)
            .testTag(GroupsNavigationTags.Home),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        GroupTopBar(
            title = group.name,
            showBack = navigation.memberships.size > 1,
            showSettings = administration.actions.canEditSettings,
            onBack = { onNavigationIntent(GroupsNavigationIntent.OpenGroups) },
            onOpenSettings = onOpenSettings,
        )
        GroupSummary(group, administration.memberships)
        NextGameCard(
            showGames = navigation.access.showGames,
            onOpenGames = { onNavigationIntent(GroupsNavigationIntent.OpenGames) },
        )
        ShortcutCard(
            navigation = navigation,
            administration = administration,
            onNavigationIntent = onNavigationIntent,
            onOpenSettings = onOpenSettings,
        )
        NoticesCard(
            incomplete = group.profileStatus == GroupProfileStatusDto.INCOMPLETE,
            canCompleteProfile = navigation.access.canCompleteProfile,
            onCompleteProfile = { onNavigationIntent(GroupsNavigationIntent.OpenProfileCompletion) },
        )
        MembersCard(
            memberships = administration.memberships,
            canOpenPeople = navigation.access.showPeople,
            onOpenPeople = { onNavigationIntent(GroupsNavigationIntent.OpenPeople) },
        )
        if (administration.actions.canManageInvite) {
            InviteCard(onOpenInvite)
        }
        SaqzButton(
            label = stringResource(Res.string.groups_logout),
            onClick = onRequestLogout,
            modifier = Modifier.fillMaxWidth().testTag("groups-logout"),
            variant = SaqzButtonVariant.Ghost,
        )
    }
}

@Composable
private fun GroupTopBar(
    title: String,
    showBack: Boolean,
    showSettings: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CompactAction(
                Res.drawable.material_arrow_back,
                stringResource(Res.string.groups_back),
                GroupsNavigationTags.BackToList,
                onBack,
            )
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = SaqzTheme.typography.lead.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = SaqzTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showSettings) {
            CompactAction(
                Res.drawable.material_more_horiz,
                stringResource(Res.string.groups_more_options),
                "groups-settings",
                onOpenSettings,
            )
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun CompactAction(
    icon: DrawableResource,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).testTag(tag),
    ) {
        MaterialIcon(icon, SaqzTheme.colors.primary, 24.dp, description)
    }
}

@Composable
private fun GroupSummary(group: GroupDto, memberships: List<MembershipDto>) {
    HomeCard(
        modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.Summary),
        contentPadding = 12.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialsAvatar(group.name, 104.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group.name,
                        modifier = Modifier.weight(1f),
                        style = SaqzTheme.typography.bodyStrong.copy(fontSize = 18.sp),
                        color = SaqzTheme.colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SaqzBadge(groupRoleLabel(group.role), SaqzBadgeVariant.Info)
                }
                MetaLine(
                    Res.drawable.material_group_add,
                    if (memberships.isEmpty()) {
                        stringResource(Res.string.groups_private)
                    } else {
                        stringResource(Res.string.groups_members_count, memberships.size)
                    },
                )
                MetaLine(
                    Res.drawable.material_location_on,
                    group.profile?.defaultVenue?.name ?: stringResource(Res.string.groups_location_empty),
                )
                val slot = group.profile?.regularSlots?.firstOrNull()
                MetaLine(
                    Res.drawable.material_calendar,
                    if (slot == null) {
                        stringResource(Res.string.groups_schedule_empty)
                    } else {
                        stringResource(
                            Res.string.groups_schedule_pattern,
                            weekdayLabel(slot.weekday),
                            slot.startTime.take(5),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun NextGameCard(showGames: Boolean, onOpenGames: () -> Unit) {
    val shape = RoundedCornerShape(SaqzTheme.metrics.cardRadius)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    listOf(SaqzTheme.colors.infoSurface, SaqzTheme.colors.surfacePearl),
                ),
                shape = shape,
            )
            .border(0.5.dp, SaqzTheme.colors.primary.copy(alpha = 0.12f), shape)
            .padding(14.dp)
            .testTag(GroupsNavigationTags.NextGame),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeading(
            icon = Res.drawable.material_calendar,
            title = stringResource(Res.string.groups_next_game),
        )
        Text(
            stringResource(Res.string.groups_next_game_empty),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            stringResource(Res.string.groups_next_game_hint),
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary,
        )
        if (showGames) {
            SaqzButton(
                label = stringResource(Res.string.groups_see_games),
                onClick = onOpenGames,
                modifier = Modifier.fillMaxWidth(),
                variant = SaqzButtonVariant.Secondary,
                borderColor = SaqzTheme.colors.primary,
            )
        }
    }
}

@Composable
private fun ShortcutCard(
    navigation: GroupsNavigationState,
    administration: GroupAdministrationState,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
    onOpenSettings: () -> Unit,
) {
    HomeCard(
        modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.Shortcuts),
        contentPadding = 8.dp,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (navigation.access.showGames) Shortcut(
                label = stringResource(Res.string.groups_games),
                icon = Res.drawable.material_calendar,
                tag = GroupsNavigationTags.ShortcutGames,
                modifier = Modifier.weight(1f),
            ) { onNavigationIntent(GroupsNavigationIntent.OpenGames) }
            if (navigation.access.showPeople) Shortcut(
                label = stringResource(Res.string.groups_people),
                icon = Res.drawable.material_group_add,
                tag = GroupsNavigationTags.ShortcutPeople,
                modifier = Modifier.weight(1f),
            ) { onNavigationIntent(GroupsNavigationIntent.OpenPeople) }
            if (navigation.access.showFinance) Shortcut(
                label = if (navigation.access.financeDestination == GroupsDestination.OWN_CHARGES) {
                    stringResource(Res.string.groups_own_charges)
                } else {
                    stringResource(Res.string.groups_finance)
                },
                icon = Res.drawable.material_payments,
                tag = GroupsNavigationTags.ShortcutFinance,
                modifier = Modifier.weight(1f),
            ) { onNavigationIntent(GroupsNavigationIntent.OpenFinance) }
            if (administration.actions.canEditSettings) Shortcut(
                label = stringResource(Res.string.groups_settings),
                icon = Res.drawable.material_settings,
                tag = GroupsNavigationTags.ShortcutSettings,
                modifier = Modifier.weight(1f),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun Shortcut(
    label: String,
    icon: DrawableResource,
    tag: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = SaqzTheme.motion
    val scale by animateFloatAsState(
        targetValue = if (pressed) motion.pressScale else 1f,
        animationSpec = tween(motion.pressDurationMillis),
        label = "shortcutPressScale",
    )
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .shadow(1.dp, shape, clip = false)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.86f else 1f
            }
            .clip(shape)
            .background(SaqzTheme.colors.surface, shape)
            .border(0.5.dp, SaqzTheme.colors.hairline.copy(alpha = 0.7f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MaterialIcon(icon, SaqzTheme.colors.primary, 22.dp)
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            style = SaqzTheme.typography.navigation.copy(fontWeight = FontWeight.Medium),
            color = SaqzTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoticesCard(
    incomplete: Boolean,
    canCompleteProfile: Boolean,
    onCompleteProfile: () -> Unit,
) {
    HomeCard(Modifier.fillMaxWidth().testTag(GroupsNavigationTags.Notices)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(
                icon = Res.drawable.material_campaign,
                title = stringResource(Res.string.groups_notices),
            )
            Text(
                if (incomplete) {
                    stringResource(Res.string.groups_profile_incomplete)
                } else {
                    stringResource(Res.string.groups_notices_empty)
                },
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
            if (canCompleteProfile) {
                SaqzButton(
                    label = stringResource(Res.string.groups_complete_profile),
                    onClick = onCompleteProfile,
                    modifier = Modifier.fillMaxWidth(),
                    variant = SaqzButtonVariant.Secondary,
                )
            }
        }
    }
}

@Composable
private fun MembersCard(
    memberships: List<MembershipDto>,
    canOpenPeople: Boolean,
    onOpenPeople: () -> Unit,
) {
    HomeCard(Modifier.fillMaxWidth().testTag(GroupsNavigationTags.Members)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(
                icon = Res.drawable.material_group_add,
                title = stringResource(Res.string.groups_members),
            ) {
                if (memberships.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.groups_members_count, memberships.size),
                        style = SaqzTheme.typography.navigation,
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
                if (canOpenPeople) TextAction(
                    label = stringResource(Res.string.groups_view_all),
                    onClick = onOpenPeople,
                    trailingIcon = Res.drawable.material_arrow_forward,
                )
            }
            if (memberships.isEmpty()) {
                Text(
                    stringResource(Res.string.groups_members_empty),
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textSecondary,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    memberships.take(5).forEach { membership ->
                        InitialsAvatar(membership.displayName, 36.dp)
                    }
                    val remaining = memberships.size - 5
                    if (remaining > 0) CountAvatar(remaining)
                }
            }
        }
    }
}

@Composable
private fun InviteCard(onInvite: () -> Unit) {
    val shape = RoundedCornerShape(SaqzTheme.metrics.cardRadius)
    Row(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    listOf(SaqzTheme.colors.infoSurface, SaqzTheme.colors.surfacePearl),
                ),
                shape = shape,
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(SaqzTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            MaterialIcon(Res.drawable.material_group_add, SaqzTheme.colors.primary, 24.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.groups_invite_title),
                style = SaqzTheme.typography.bodyStrong,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                stringResource(Res.string.groups_invite_hint),
                style = SaqzTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = SaqzTheme.colors.textSecondary,
            )
        }
        TextAction(
            label = stringResource(Res.string.groups_send_invite),
            onClick = onInvite,
            modifier = Modifier.testTag(GroupsNavigationTags.Invite),
            trailingIcon = Res.drawable.material_arrow_forward,
        )
    }
}

@Composable
private fun SectionHeading(
    icon: DrawableResource,
    title: String,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(SaqzTheme.colors.infoSurface),
            contentAlignment = Alignment.Center,
        ) {
            MaterialIcon(icon, SaqzTheme.colors.primary, 19.dp)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = SaqzTheme.typography.bodyStrong,
            color = SaqzTheme.colors.textPrimary,
        )
        trailing()
    }
}

@Composable
private fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: DrawableResource? = null,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(SaqzTheme.metrics.compactControlRadius))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.navigation.copy(fontWeight = FontWeight.Medium),
            color = SaqzTheme.colors.primary,
            maxLines = 1,
        )
        trailingIcon?.let { MaterialIcon(it, SaqzTheme.colors.primary, 16.dp) }
    }
}

@Composable
private fun GroupLoadError(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.groups_load_error),
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzButton(stringResource(Res.string.groups_retry), onRetry)
    }
}

@Composable
private fun RoutePage(
    title: String,
    body: String,
    tag: String,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Text(title, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
        Text(body, color = SaqzTheme.colors.textSecondary)
        SaqzButton(
            label = stringResource(Res.string.groups_back_home),
            onClick = { onNavigationIntent(GroupsNavigationIntent.OpenHome) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("groups-back"),
            variant = SaqzButtonVariant.Secondary,
        )
    }
}

@Composable
private fun OutlinedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SaqzCard(
        modifier = modifier.border(
            1.dp,
            SaqzTheme.colors.hairline,
            RoundedCornerShape(SaqzTheme.metrics.cardRadius),
        ),
        onClick = onClick,
        content = content,
    )
}

@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(SaqzTheme.metrics.cardRadius)
    Box(
        modifier = modifier
            .shadow(1.dp, shape, clip = false)
            .clip(shape)
            .background(SaqzTheme.colors.surface, shape)
            .border(0.5.dp, SaqzTheme.colors.hairline.copy(alpha = 0.65f), shape)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
private fun InitialsAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val shape = if (size <= 48.dp) CircleShape else RoundedCornerShape(14.dp)
    Box(
        Modifier.size(size)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        SaqzTheme.colors.infoSurface,
                        SaqzTheme.colors.primary.copy(alpha = 0.14f),
                    ),
                ),
                shape = shape,
            )
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            style = if (size <= 48.dp) SaqzTheme.typography.caption else SaqzTheme.typography.lead,
            color = SaqzTheme.colors.primary,
        )
    }
}

@Composable
private fun CountAvatar(remaining: Int) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(SaqzTheme.colors.surfaceSubtle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$remaining",
            style = SaqzTheme.typography.navigation,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun MetaLine(icon: DrawableResource, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialIcon(icon, SaqzTheme.colors.textSecondary, 16.dp)
        Text(
            text,
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

@Composable
private fun groupRoleLabel(role: GroupRoleDto): String = when (role) {
    GroupRoleDto.OWNER -> stringResource(Res.string.groups_role_owner)
    GroupRoleDto.ADMIN -> stringResource(Res.string.groups_role_admin)
    GroupRoleDto.ATHLETE -> stringResource(Res.string.groups_role_athlete)
}

@Composable
private fun sessionRoleLabel(role: String): String = when (role.uppercase()) {
    GroupRoleDto.OWNER.name -> stringResource(Res.string.groups_role_owner)
    GroupRoleDto.ADMIN.name -> stringResource(Res.string.groups_role_admin)
    else -> stringResource(Res.string.groups_role_athlete)
}

@Composable
private fun weekdayLabel(weekday: GroupWeekdayDto): String = stringResource(
    when (weekday) {
        GroupWeekdayDto.MONDAY -> Res.string.groups_weekday_monday
        GroupWeekdayDto.TUESDAY -> Res.string.groups_weekday_tuesday
        GroupWeekdayDto.WEDNESDAY -> Res.string.groups_weekday_wednesday
        GroupWeekdayDto.THURSDAY -> Res.string.groups_weekday_thursday
        GroupWeekdayDto.FRIDAY -> Res.string.groups_weekday_friday
        GroupWeekdayDto.SATURDAY -> Res.string.groups_weekday_saturday
        GroupWeekdayDto.SUNDAY -> Res.string.groups_weekday_sunday
    },
)

private fun initials(name: String): String = name.trim()
    .split(' ')
    .filter(String::isNotBlank)
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "G" }
