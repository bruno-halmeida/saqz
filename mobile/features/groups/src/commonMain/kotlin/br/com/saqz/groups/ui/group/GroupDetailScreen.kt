package br.com.saqz.groups.ui.group

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupProfileStatus
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.presentation.GroupActions
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationAccess
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_complete_profile
import br.com.saqz.groups.resources.groups_finance
import br.com.saqz.groups.resources.groups_games
import br.com.saqz.groups.resources.groups_invite_hint
import br.com.saqz.groups.resources.groups_invite_title
import br.com.saqz.groups.resources.groups_location_empty
import br.com.saqz.groups.resources.groups_logout
import br.com.saqz.groups.resources.groups_members
import br.com.saqz.groups.resources.groups_members_count
import br.com.saqz.groups.resources.groups_members_empty
import br.com.saqz.groups.resources.groups_next_game
import br.com.saqz.groups.resources.groups_next_game_empty
import br.com.saqz.groups.resources.groups_next_game_hint
import br.com.saqz.groups.resources.groups_notices
import br.com.saqz.groups.resources.groups_notices_empty
import br.com.saqz.groups.resources.groups_own_charges
import br.com.saqz.groups.resources.groups_people
import br.com.saqz.groups.resources.groups_private
import br.com.saqz.groups.resources.groups_profile_incomplete
import br.com.saqz.groups.resources.groups_schedule_empty
import br.com.saqz.groups.resources.groups_schedule_pattern
import br.com.saqz.groups.resources.groups_see_games
import br.com.saqz.groups.resources.groups_send_invite
import br.com.saqz.groups.resources.groups_settings
import br.com.saqz.groups.resources.groups_view_all
import br.com.saqz.groups.resources.material_arrow_forward
import br.com.saqz.groups.resources.material_calendar
import br.com.saqz.groups.resources.material_campaign
import br.com.saqz.groups.resources.material_group_add
import br.com.saqz.groups.resources.material_location_on
import br.com.saqz.groups.resources.material_payments
import br.com.saqz.groups.resources.material_settings
import br.com.saqz.groups.ui.setup.weekdayLabel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun GroupDetailScreen(
    group: Group,
    administration: GroupAdministrationState,
    access: GroupsNavigationAccess,
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
            showGames = access.showGames,
            onOpenGames = { onNavigationIntent(GroupsNavigationIntent.OpenGames) },
        )
        ShortcutCard(
            access = access,
            administration = administration,
            onNavigationIntent = onNavigationIntent,
            onOpenSettings = onOpenSettings,
        )
        NoticesCard(
            incomplete = group.profileStatus == GroupProfileStatus.INCOMPLETE,
            canCompleteProfile = access.canCompleteProfile,
            onCompleteProfile = { onNavigationIntent(GroupsNavigationIntent.OpenProfileCompletion) },
        )
        MembersCard(
            memberships = administration.memberships,
            canOpenPeople = access.showPeople,
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
    access: GroupsNavigationAccess,
    administration: GroupAdministrationState,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
    onOpenSettings: () -> Unit,
) {
    HomeCard(
        modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.Shortcuts),
        contentPadding = 8.dp,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (access.showGames) Shortcut(
                label = stringResource(Res.string.groups_games),
                icon = Res.drawable.material_calendar,
                tag = GroupsNavigationTags.ShortcutGames,
                modifier = Modifier.weight(1f),
            ) { onNavigationIntent(GroupsNavigationIntent.OpenGames) }
            if (access.showPeople) Shortcut(
                label = stringResource(Res.string.groups_people),
                icon = Res.drawable.material_group_add,
                tag = GroupsNavigationTags.ShortcutPeople,
                modifier = Modifier.weight(1f),
            ) { onNavigationIntent(GroupsNavigationIntent.OpenPeople) }
            if (access.showFinance) Shortcut(
                label = if (access.financeDestination == GroupsDestination.OWN_CHARGES) {
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
    actions = GroupActions(canEditSettings = true, canManageRoles = true, canManageInvite = true, canManageAthletes = true),
)

private val previewAccess = GroupsNavigationAccess(
    showPeople = true,
    showGames = true,
    showFinance = true,
    canMutateOperations = true,
    financeDestination = GroupsDestination.FINANCE,
)

@Preview
@Composable
private fun GroupDetailScreenPreview() = SaqzTheme {
    GroupDetailScreen(
        group = previewGroup,
        administration = previewAdministration,
        access = previewAccess,
        groupPhotoState = GroupPhotoState(),
        groupPhotoPreview = null,
        onNavigationIntent = {},
        onOpenSettings = {},
        onOpenInvite = {},
        onRequestLogout = {},
    )
}