package br.com.saqz.groups.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzTopBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.presentation.GroupSelectionMembership
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_create
import br.com.saqz.groups.resources.groups_create_card_hint
import br.com.saqz.groups.resources.groups_create_card_title
import br.com.saqz.groups.resources.groups_list_subtitle
import br.com.saqz.groups.resources.groups_list_title
import br.com.saqz.groups.resources.groups_location_empty
import br.com.saqz.groups.resources.groups_new_group
import br.com.saqz.groups.resources.groups_next_game
import br.com.saqz.groups.resources.groups_private
import br.com.saqz.groups.resources.groups_schedule_empty
import br.com.saqz.groups.resources.material_add
import br.com.saqz.groups.resources.material_arrow_forward
import br.com.saqz.groups.resources.material_calendar
import br.com.saqz.groups.resources.material_group_add
import br.com.saqz.groups.resources.material_location_on
import br.com.saqz.groups.resources.material_more_horiz
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
private fun sessionRoleLabel(role: GroupRole): String = groupRoleLabel(role)

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