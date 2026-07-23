package br.com.saqz.groups.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupProfileStatus
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.group.VersionedGroup
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
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.ui.setup.weekdayLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_back_home
import br.com.saqz.groups.resources.groups_complete_profile
import br.com.saqz.groups.resources.groups_create
import br.com.saqz.groups.resources.groups_create_card_hint
import br.com.saqz.groups.resources.groups_create_card_title
import br.com.saqz.groups.resources.groups_finance
import br.com.saqz.groups.resources.groups_games
import br.com.saqz.groups.resources.groups_invite_hint
import br.com.saqz.groups.resources.groups_invite_title
import br.com.saqz.groups.resources.groups_list_subtitle
import br.com.saqz.groups.resources.groups_list_title
import br.com.saqz.groups.resources.groups_load_error
import br.com.saqz.groups.resources.groups_location_empty
import br.com.saqz.groups.resources.groups_logout
import br.com.saqz.groups.resources.groups_members
import br.com.saqz.groups.resources.groups_members_count
import br.com.saqz.groups.resources.groups_members_empty
import br.com.saqz.groups.resources.groups_new_group
import br.com.saqz.groups.resources.groups_next_game
import br.com.saqz.groups.resources.groups_next_game_empty
import br.com.saqz.groups.resources.groups_next_game_hint
import br.com.saqz.groups.resources.groups_notices
import br.com.saqz.groups.resources.groups_notices_empty
import br.com.saqz.groups.resources.groups_own_charges
import br.com.saqz.groups.resources.groups_people
import br.com.saqz.groups.resources.groups_private
import br.com.saqz.groups.resources.groups_profile_incomplete
import br.com.saqz.groups.resources.groups_retry
import br.com.saqz.groups.resources.groups_role_admin
import br.com.saqz.groups.resources.groups_role_athlete
import br.com.saqz.groups.resources.groups_role_owner
import br.com.saqz.groups.resources.groups_schedule_empty
import br.com.saqz.groups.resources.groups_schedule_pattern
import br.com.saqz.groups.resources.groups_see_games
import br.com.saqz.groups.resources.groups_send_invite
import br.com.saqz.groups.resources.groups_settings
import br.com.saqz.groups.resources.groups_view_all
import br.com.saqz.groups.resources.groups_weekday_friday
import br.com.saqz.groups.resources.groups_weekday_monday
import br.com.saqz.groups.resources.groups_weekday_saturday
import br.com.saqz.groups.resources.groups_weekday_sunday
import br.com.saqz.groups.resources.groups_weekday_thursday
import br.com.saqz.groups.resources.groups_weekday_tuesday
import br.com.saqz.groups.resources.groups_weekday_wednesday
import br.com.saqz.groups.resources.material_add
import br.com.saqz.groups.resources.material_arrow_forward
import br.com.saqz.groups.resources.material_campaign
import br.com.saqz.groups.resources.material_calendar
import br.com.saqz.groups.resources.material_group_add
import br.com.saqz.groups.resources.material_location_on
import br.com.saqz.groups.resources.material_more_horiz
import br.com.saqz.groups.resources.material_payments
import br.com.saqz.groups.resources.material_settings
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzTopBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupSelectionMembership
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GroupsListScreen(
    memberships: List<GroupSelectionMembership>,
    onSelectGroup: (String) -> Unit,
    onOpenCreateGroup: () -> Unit,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)? = null,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)? = null,
) {
    Column(
        Modifier.fillMaxSize().testTag(GroupsNavigationTags.List),
    ) {
        SaqzTopBar(title = stringResource(Res.string.groups_list_title))
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = SaqzTheme.metrics.horizontalPadding,
                    vertical = SaqzTheme.metrics.grid,
                ),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.groups_list_subtitle),
                    modifier = Modifier.weight(1f),
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textSecondary,
                )
                SaqzButton(
                    label = stringResource(Res.string.groups_new_group),
                    onClick = onOpenCreateGroup,
                    leadingContent = { tint ->
                        MaterialIcon(Res.drawable.material_add, tint, 18.dp)
                    },
                )
            }
            memberships.forEach { membership ->
                GroupListCard(
                    membership = membership,
                    onSelectGroup = onSelectGroup,
                    loadListPhoto = loadListPhoto,
                    groupPhotoPreview = groupPhotoPreview,
                )
            }
            CreateGroupCard(onOpenCreateGroup)
        }
    }
}

@Composable
private fun GroupListCard(
    membership: GroupSelectionMembership,
    onSelectGroup: (String) -> Unit,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)?,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
) {
    HomeCard(
        modifier = Modifier.fillMaxWidth()
            .testTag("${GroupsNavigationTags.ListItemPrefix}${membership.groupId}"),
        contentPadding = 10.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(SaqzTheme.metrics.compactControlRadius))
                .clickable(
                    onClickLabel = membership.groupName,
                    onClick = { onSelectGroup(membership.groupId) },
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                GroupListPhoto(
                    groupId = membership.groupId,
                    groupName = membership.groupName,
                    loadListPhoto = loadListPhoto,
                    groupPhotoPreview = groupPhotoPreview,
                )
                BoxWithConstraints(Modifier.weight(1f)) {
                    val fontScale = LocalDensity.current.fontScale
                    val inlineRole = maxWidth >= 200.dp && fontScale <= 1f
                    val textLines = when {
                        fontScale >= 1.5f -> 3
                        fontScale > 1f -> 2
                        else -> 1
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                membership.groupName,
                                modifier = Modifier.weight(1f),
                                style = SaqzTheme.typography.bodyStrong,
                                color = SaqzTheme.colors.textPrimary,
                                maxLines = textLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (inlineRole) {
                                SaqzBadge(sessionRoleLabel(membership.role), SaqzBadgeVariant.Info)
                            }
                            MaterialIcon(
                                Res.drawable.material_more_horiz,
                                SaqzTheme.colors.textSecondary,
                                20.dp,
                            )
                        }
                        if (!inlineRole) {
                            SaqzBadge(sessionRoleLabel(membership.role), SaqzBadgeVariant.Info)
                        }
                        MetaLine(
                            Res.drawable.material_group_add,
                            stringResource(Res.string.groups_private),
                            textLines,
                        )
                        MetaLine(
                            Res.drawable.material_location_on,
                            stringResource(Res.string.groups_location_empty),
                            textLines,
                        )
                        MetaLine(
                            Res.drawable.material_calendar,
                            stringResource(Res.string.groups_schedule_empty),
                            textLines,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(SaqzTheme.metrics.compactControlRadius))
                    .background(SaqzTheme.colors.infoSurface)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialIcon(Res.drawable.material_calendar, SaqzTheme.colors.primary, 16.dp)
                Text(
                    stringResource(Res.string.groups_next_game),
                    modifier = Modifier.weight(1f),
                    style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = SaqzTheme.colors.primary,
                    maxLines = 1,
                )
                MaterialIcon(Res.drawable.material_arrow_forward, SaqzTheme.colors.primary, 16.dp)
            }
        }
    }
}

@Composable
private fun GroupListPhoto(
    groupId: String,
    groupName: String,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)?,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
) {
    var loading by remember(groupId) { mutableStateOf(loadListPhoto != null) }
    var existing by remember(groupId) { mutableStateOf<ExistingGroupPhoto?>(null) }
    LaunchedEffect(groupId, loadListPhoto) {
        if (loadListPhoto == null) {
            loading = false
            existing = null
            return@LaunchedEffect
        }
        loading = true
        existing = loadListPhoto(groupId)
        loading = false
    }
    val photoSize = 88.dp
    val shape = RoundedCornerShape(12.dp)
    val photo = existing
    Box(
        Modifier.size(photoSize)
            .clip(shape)
            .testTag(GroupsNavigationTags.ListPhoto),
    ) {
        when {
            loading -> GroupPhotoSkeleton(Modifier.fillMaxSize())
            photo == null || groupPhotoPreview == null -> InitialsAvatar(
                groupName,
                photoSize,
                Modifier.testTag(GroupsNavigationTags.ListPhotoFallback),
            )
            else -> {
                val rendered = groupPhotoPreview(
                    photo.preview,
                    Modifier.fillMaxSize().testTag(GroupsNavigationTags.ListPhotoImage),
                )
                when (rendered) {
                    GroupPhotoRenderState.LOADING -> GroupPhotoSkeleton(Modifier.fillMaxSize())
                    GroupPhotoRenderState.FAILURE -> InitialsAvatar(
                        groupName,
                        photoSize,
                        Modifier.testTag(GroupsNavigationTags.ListPhotoFallback),
                    )
                    GroupPhotoRenderState.SUCCESS -> Unit
                }
            }
        }
    }
}

@Composable
private fun CreateGroupCard(onOpenCreateGroup: () -> Unit) {
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
            .padding(14.dp)
            .testTag(GroupsNavigationTags.CreateCard),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialIcon(Res.drawable.material_group_add, SaqzTheme.colors.primary, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(Res.string.groups_create_card_title),
                style = SaqzTheme.typography.bodyStrong,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                stringResource(Res.string.groups_create_card_hint),
                style = SaqzTheme.typography.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = SaqzTheme.colors.textSecondary,
            )
            TextAction(
                label = stringResource(Res.string.groups_create),
                onClick = onOpenCreateGroup,
                trailingIcon = Res.drawable.material_arrow_forward,
            )
        }
    }
}

@Composable
fun GroupDetailScreen(
    group: Group,
    administration: GroupAdministrationState,
    navigation: GroupsNavigationState,
    groupPhotoState: GroupPhotoState,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
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
        GroupSummary(group, administration.memberships, groupPhotoState, groupPhotoPreview)
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
            incomplete = group.profileStatus == GroupProfileStatus.INCOMPLETE,
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
fun RoutePage(
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
fun GroupLoadError(onRetry: () -> Unit) {
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
private fun GroupSummary(
    group: Group,
    memberships: List<GroupMembership>,
    photoState: GroupPhotoState,
    photoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
) {
    HomeCard(
        modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.Summary),
        contentPadding = 12.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupSummaryPhoto(group, photoState, photoPreview)
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
                            weekdayLabel(slot.weekday.name, compact = true),
                            slot.startTime.take(5),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun GroupSummaryPhoto(
    group: Group,
    state: GroupPhotoState,
    preview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(104.dp)
            .clip(shape)
            .testTag(GroupsNavigationTags.SummaryPhoto),
    ) {
        val belongsToGroup = state.groupId == group.id.value
        val existing = state.existing
        when {
            !belongsToGroup || state.stage == GroupPhotoStage.LOADING -> GroupPhotoSkeleton(Modifier.fillMaxSize())
            existing == null -> InitialsAvatar(
                group.name,
                104.dp,
                Modifier.testTag(GroupsNavigationTags.SummaryPhotoFallback),
            )
            else -> {
                val rendered = preview?.invoke(
                    existing.preview,
                    Modifier.fillMaxSize().testTag(GroupsNavigationTags.SummaryPhotoImage),
                ) ?: GroupPhotoRenderState.FAILURE
                when (rendered) {
                    GroupPhotoRenderState.LOADING -> GroupPhotoSkeleton(Modifier.fillMaxSize())
                    GroupPhotoRenderState.FAILURE -> InitialsAvatar(
                        group.name,
                        104.dp,
                        Modifier.testTag(GroupsNavigationTags.SummaryPhotoFallback),
                    )
                    GroupPhotoRenderState.SUCCESS -> Unit
                }
            }
        }
    }
}

@Composable
private fun GroupPhotoSkeleton(modifier: Modifier) {
    val reducedMotion = SaqzTheme.motion.maxTranslation == 0.dp
    val progress = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "group-photo-shimmer")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "group-photo-shimmer-progress",
        )
        animated
    }
    val base = SaqzTheme.colors.surfaceSubtle
    val highlight = SaqzTheme.colors.surface
    Box(
        modifier
            .drawWithCache {
                val center = size.width * (progress * 2f - 0.5f)
                val brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(center - size.width, 0f),
                    end = Offset(center + size.width, size.height),
                )
                onDrawBehind { drawRect(brush) }
            }
            .testTag(GroupsNavigationTags.SummaryPhotoSkeleton),
    )
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
            .semantics { contentDescription = label }
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MaterialIcon(icon, SaqzTheme.colors.primary, 22.dp)
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics { },
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
    memberships: List<GroupMembership>,
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
private fun HomeCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 12.dp,
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
private fun InitialsAvatar(
    name: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val shape = if (size <= 48.dp) CircleShape else RoundedCornerShape(14.dp)
    Box(
        modifier.size(size)
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
private fun MetaLine(icon: DrawableResource, text: String, maxLines: Int = 1) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialIcon(icon, SaqzTheme.colors.textSecondary, 16.dp)
        Text(
            text,
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary,
            maxLines = maxLines,
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
private fun groupRoleLabel(role: GroupRole): String = when (role) {
    GroupRole.OWNER -> stringResource(Res.string.groups_role_owner)
    GroupRole.ADMIN -> stringResource(Res.string.groups_role_admin)
    GroupRole.ATHLETE -> stringResource(Res.string.groups_role_athlete)
}

@Composable
private fun sessionRoleLabel(role: GroupRole): String = groupRoleLabel(role)

private fun initials(name: String): String = name.trim()
    .split(' ')
    .filter(String::isNotBlank)
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "G" }

private val previewGroup = Group(
    id = GroupId("preview-group"),
    name = "Futebol de terça",
    timeZone = GroupTimeZone("America/Sao_Paulo"),
    version = 1,
    role = GroupRole.OWNER,
    profile = GroupProfile(
        modality = null,
        composition = null,
        description = null,
        city = null,
        level = null,
        customLevel = null,
        playStyle = null,
        customPlayStyle = null,
        defaultVenue = GroupVenue(id = "venue-1", name = "Quadra do Parque", address = "Rua das Flores, 10"),
        regularSlots = listOf(
            GroupRegularSlot(
                id = "slot-1",
                weekday = GroupWeekday.TUESDAY,
                startTime = "19:30",
                durationMinutes = 90,
            ),
        ),
        defaultCapacity = null,
        defaultConfirmationLeadMinutes = null,
    ),
)

private val previewMemberships = listOf(
    GroupMembership(userId = "user-1", displayName = "Ana Lima", role = GroupRole.ATHLETE),
    GroupMembership(userId = "user-2", displayName = "Bruno Reis", role = GroupRole.ADMIN),
)

private val previewAdministration = GroupAdministrationState(
    group = VersionedGroup(
        previewGroup,
        br.com.saqz.groups.domain.group.GroupVersionToken("preview-etag"),
    ),
    memberships = previewMemberships,
    actions = GroupActions(canEditSettings = true, canManageRoles = true, canManageInvite = true),
)

private val previewNavigation = GroupsNavigationState(
    destination = GroupsDestination.HOME,
    groupId = previewGroup.id.value,
    access = GroupsNavigationAccess(
        showPeople = true,
        showGames = true,
        showFinance = true,
        canMutateOperations = true,
        financeDestination = GroupsDestination.FINANCE,
    ),
    memberships = listOf(
        GroupSelectionMembership(previewGroup.id.value, previewGroup.name, GroupRole.OWNER),
    ),
)

@Preview
@Composable
private fun GroupsListScreenPreview() = SaqzTheme {
    GroupsListScreen(
        memberships = listOf(
            GroupSelectionMembership("alpha", "Alpha", GroupRole.OWNER),
            GroupSelectionMembership("beta", "Beta", GroupRole.ATHLETE),
        ),
        onSelectGroup = {},
        onOpenCreateGroup = {},
    )
}

@Preview
@Composable
private fun GroupDetailScreenPreview() = SaqzTheme {
    GroupDetailScreen(
        group = previewGroup,
        administration = previewAdministration,
        navigation = previewNavigation,
        groupPhotoState = GroupPhotoState(),
        groupPhotoPreview = null,
        onNavigationIntent = {},
        onOpenSettings = {},
        onOpenInvite = {},
        onRequestLogout = {},
    )
}

@Preview
@Composable
private fun RoutePagePreview() = SaqzTheme {
    RoutePage(
        title = "Jogos",
        body = "Consulte jogos e crie novas partidas.",
        tag = GroupsNavigationTags.Games,
        onNavigationIntent = {},
    )
}

@Preview
@Composable
private fun GroupLoadErrorPreview() = SaqzTheme {
    GroupLoadError(onRetry = {})
}
