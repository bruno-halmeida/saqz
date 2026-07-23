package br.com.saqz.groups.ui.group

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_members
import br.com.saqz.groups.resources.groups_role_admin
import br.com.saqz.groups.resources.groups_role_athlete
import br.com.saqz.groups.resources.groups_role_owner
import br.com.saqz.groups.resources.material_calendar
import br.com.saqz.groups.resources.material_campaign
import br.com.saqz.groups.resources.material_group_add
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MaterialIcon(
    resource: DrawableResource,
    tint: Color,
    size: Dp,
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
internal fun HomeCard(
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
internal fun SectionHeading(
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
internal fun TextAction(
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
internal fun MetaLine(icon: DrawableResource, text: String, maxLines: Int = 1) {
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
internal fun InitialsAvatar(
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
internal fun CountAvatar(remaining: Int) {
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
internal fun GroupPhotoSkeleton(modifier: Modifier) {
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
internal fun groupRoleLabel(role: GroupRole): String = when (role) {
    GroupRole.OWNER -> stringResource(Res.string.groups_role_owner)
    GroupRole.ADMIN -> stringResource(Res.string.groups_role_admin)
    GroupRole.ATHLETE -> stringResource(Res.string.groups_role_athlete)
}

internal fun initials(name: String): String = name.trim()
    .split(' ')
    .filter(String::isNotBlank)
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "G" }

@Preview
@Composable
private fun HomeCardPreview() = SaqzTheme {
    HomeCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Home card content", color = SaqzTheme.colors.textPrimary)
    }
}

@Preview
@Composable
private fun SectionHeadingPreview() = SaqzTheme {
    SectionHeading(
        icon = Res.drawable.material_campaign,
        title = "Section heading",
    )
}

@Preview
@Composable
private fun SectionHeadingWithTrailingPreview() = SaqzTheme {
    SectionHeading(
        icon = Res.drawable.material_group_add,
        title = stringResource(Res.string.groups_members),
        trailing = {
            Text("12", color = SaqzTheme.colors.textSecondary)
        },
    )
}

@Preview
@Composable
private fun TextActionPreview() = SaqzTheme {
    TextAction(
        label = "Ver jogos",
        onClick = {},
        trailingIcon = Res.drawable.material_calendar,
    )
}

@Preview
@Composable
private fun MetaLinePreview() = SaqzTheme {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetaLine(Res.drawable.material_group_add, "Grupo privado")
        MetaLine(Res.drawable.material_calendar, "Toda terça, 19:30", maxLines = 2)
    }
}

@Preview
@Composable
private fun InitialsAvatarPreview() = SaqzTheme {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InitialsAvatar("Ana Lima", 36.dp)
        InitialsAvatar("Bruno Reis", 48.dp)
        InitialsAvatar("Futebol de terça", 88.dp)
    }
}

@Preview
@Composable
private fun CountAvatarPreview() = SaqzTheme {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CountAvatar(3)
        CountAvatar(12)
    }
}

@Preview
@Composable
private fun GroupPhotoSkeletonPreview() = SaqzTheme {
    Box(Modifier.size(104.dp).padding(16.dp)) {
        GroupPhotoSkeleton(Modifier.fillMaxSize())
    }
}

@Preview
@Composable
private fun MaterialIconPreview() = SaqzTheme {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        MaterialIcon(Res.drawable.material_calendar, SaqzTheme.colors.primary, 24.dp)
        MaterialIcon(Res.drawable.material_group_add, SaqzTheme.colors.textSecondary, 20.dp)
    }
}