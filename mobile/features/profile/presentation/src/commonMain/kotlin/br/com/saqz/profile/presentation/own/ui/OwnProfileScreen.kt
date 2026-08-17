package br.com.saqz.profile.presentation.own.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.saqzInitials
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.presentation.own.OwnProfileGroupUi
import br.com.saqz.profile.presentation.own.OwnProfileIntent
import br.com.saqz.profile.presentation.own.OwnProfileState
import br.com.saqz.profile.presentation.own.OwnProfileStatsUi
import br.com.saqz.profile.presentation.own.OwnProfileUserUi
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import br.com.saqz.profile.presentation.own.toOwnProfileGroupUi
import br.com.saqz.profile.presentation.own.toOwnProfileStatsUi
import br.com.saqz.profile.presentation.own.toOwnProfileUserUi
import br.com.saqz.profile.presentation.photo.profilePhotoImageRequest
import br.com.saqz.profile.resources.Res
import br.com.saqz.profile.resources.profile_account
import br.com.saqz.profile.resources.profile_attendance
import br.com.saqz.profile.resources.profile_change_password
import br.com.saqz.profile.resources.profile_edit_data
import br.com.saqz.profile.resources.profile_empty_groups
import br.com.saqz.profile.resources.profile_error_message
import br.com.saqz.profile.resources.profile_error_title
import br.com.saqz.profile.resources.profile_games
import br.com.saqz.profile.resources.profile_groups
import br.com.saqz.profile.resources.profile_monthly_payments
import br.com.saqz.profile.resources.profile_notifications
import br.com.saqz.profile.resources.profile_photo_content_description
import br.com.saqz.profile.resources.profile_play_style
import br.com.saqz.profile.resources.profile_retry
import br.com.saqz.profile.resources.profile_settings_content_description
import br.com.saqz.profile.resources.profile_sign_out
import br.com.saqz.profile.resources.profile_title
import org.jetbrains.compose.resources.stringResource

internal object OwnProfileTags {
    const val Screen = "own-profile"
    const val Settings = "own-profile-settings"
    const val EditData = "own-profile-edit-data"
    const val Stats = "own-profile-stats"
    const val Attendance = "own-profile-attendance"
    const val GroupsEmpty = "own-profile-groups-empty"
    const val Failure = "own-profile-failure"
    const val Retry = "own-profile-retry"
    const val MonthlyPayments = "own-profile-monthly-payments"
    const val Notifications = "own-profile-notifications"
    const val ChangePassword = "own-profile-change-password"
    const val SignOut = "own-profile-sign-out"

    fun group(id: String) = "own-profile-group-$id"
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun OwnProfileScreen(
    state: OwnProfileState,
    onIntent: (OwnProfileIntent) -> Unit,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    topBarWindowInsets: WindowInsets = WindowInsets.statusBars,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isLoading,
        onRefresh = { onIntent(OwnProfileIntent.Refresh) },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag(OwnProfileTags.Screen),
    ) {
        SaqzTopAppBar(
            title = stringResource(Res.string.profile_title),
            windowInsets = topBarWindowInsets,
            actions = {
                SaqzIconButton(
                    onClick = { onIntent(OwnProfileIntent.OpenSettings) },
                    contentDescription = stringResource(Res.string.profile_settings_content_description),
                    modifier = Modifier.testTag(OwnProfileTags.Settings),
                ) {
                    OwnProfileSettingsIcon()
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pullRefresh(pullRefreshState, enabled = !state.isLoading),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = metrics.horizontalPadding,
                    vertical = metrics.blockGap,
                ),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                when {
                    state.isLoading -> item(key = "loading") {
                        OwnProfileLoading()
                    }

                    state.loadError -> item(key = "error") {
                        OwnProfileFailure(onRetry = { onIntent(OwnProfileIntent.Refresh) })
                    }

                    else -> {
                        state.user?.let { user ->
                            item(key = "profile-card") {
                                OwnProfileCard(
                                    user = user,
                                    stats = state.stats,
                                    imageLoader = imageLoader,
                                    onEdit = { onIntent(OwnProfileIntent.EditData) },
                                )
                            }
                        }
                        item(key = "play-style-header") {
                            SaqzSectionHeader(title = stringResource(Res.string.profile_play_style))
                        }
                        if (state.isEmpty) {
                            item(key = "groups-empty") {
                                SaqzEmptyState(
                                    title = stringResource(Res.string.profile_empty_groups),
                                    icon = SaqzIcons.Users,
                                    modifier = Modifier.testTag(OwnProfileTags.GroupsEmpty),
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = state.groups,
                                key = { _, group -> group.id },
                            ) { _, group ->
                                OwnProfileGroupRow(
                                    group = group,
                                    onClick = { onIntent(OwnProfileIntent.OpenGroup(group.id)) },
                                    modifier = Modifier.testTag(OwnProfileTags.group(group.id)),
                                )
                            }
                        }
                        item(key = "account-header") {
                            SaqzSectionHeader(title = stringResource(Res.string.profile_account))
                        }
                        item(key = "account-card") {
                            OwnProfileAccountCard(onIntent = onIntent)
                        }
                    }
                }
            }
            PullRefreshIndicator(
                refreshing = state.isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = colors.surface,
                contentColor = colors.primary,
            )
        }
    }
}

@Composable
private fun OwnProfileLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SaqzTheme.metrics.sectionVerticalPadding)
            .testTag("${OwnProfileTags.Screen}-loading"),
        contentAlignment = Alignment.Center,
    ) {
        SaqzSpinner()
    }
}

@Composable
private fun OwnProfileSettingsIcon() {
    val color = SaqzTheme.colors.textPrimary
    Canvas(Modifier.size(SaqzTheme.metrics.iconButtonSize / 2)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val innerRadius = size.minDimension * 0.27f
        val outerRadius = size.minDimension * 0.48f
        val strokeWidth = size.minDimension * 0.11f
        drawCircle(
            color = color,
            radius = innerRadius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
        )
        repeat(8) { index ->
            val angle = index * (2f * kotlin.math.PI.toFloat() / 8f)
            val start = androidx.compose.ui.geometry.Offset(
                x = center.x + kotlin.math.cos(angle) * innerRadius,
                y = center.y + kotlin.math.sin(angle) * innerRadius,
            )
            val end = androidx.compose.ui.geometry.Offset(
                x = center.x + kotlin.math.cos(angle) * outerRadius,
                y = center.y + kotlin.math.sin(angle) * outerRadius,
            )
            drawLine(color, start, end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun OwnProfileFailure(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OwnProfileTags.Failure),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SaqzEmptyState(
            title = stringResource(Res.string.profile_error_title),
            description = stringResource(Res.string.profile_error_message),
            icon = SaqzIcons.CircleAlert,
            action = stringResource(Res.string.profile_retry),
            onAction = onRetry,
            modifier = Modifier.testTag(OwnProfileTags.Retry),
        )
    }
}

@Composable
private fun OwnProfileCard(
    user: OwnProfileUserUi,
    stats: OwnProfileStatsUi?,
    imageLoader: ImageLoader,
    onEdit: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    SaqzCard {
        OwnProfileAvatar(
            user = user,
            imageLoader = imageLoader,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            Text(
                text = user.displayName,
                style = SaqzTheme.typography.subtitle,
                color = SaqzTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            user.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        stats?.let { OwnProfileStatsRow(stats = it) }
        SaqzButton(
            label = stringResource(Res.string.profile_edit_data),
            onClick = onEdit,
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            modifier = Modifier.testTag(OwnProfileTags.EditData),
        )
    }
}

@Composable
private fun OwnProfileAvatar(
    user: OwnProfileUserUi,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val context = LocalPlatformContext.current
    val description = stringResource(Res.string.profile_photo_content_description)
    val imageRequest = user.photoUrl?.let { photoUrl ->
        remember(context, photoUrl) { profilePhotoImageRequest(context, photoUrl) }
    }
    SaqzAvatar(
        name = user.displayName,
        size = metrics.iconButtonSize * 2,
        modifier = modifier
            .testTag("${OwnProfileTags.Screen}-photo")
            .semantics { contentDescription = description },
        photo = imageRequest?.let { request ->
            {
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { OwnProfileAvatarFallback(user.displayName) },
                    error = { OwnProfileAvatarFallback(user.displayName) },
                )
            }
        },
    )
}

@Composable
private fun OwnProfileAvatarFallback(name: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.surfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = saqzInitials(name),
            style = SaqzTheme.typography.subtitle,
            color = SaqzTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun OwnProfileStatsRow(stats: OwnProfileStatsUi) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag(OwnProfileTags.Stats),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OwnProfileStat(
            value = stats.gamesLabel,
            label = stringResource(Res.string.profile_games),
            modifier = Modifier.weight(1f),
        )
        if (stats.attendanceLabel != null) {
            SaqzDivider(vertical = true, modifier = Modifier.padding(vertical = metrics.subGrid))
            OwnProfileStat(
                value = stats.attendanceLabel,
                label = stringResource(Res.string.profile_attendance),
                valueColor = SaqzTheme.colors.success,
                modifier = Modifier.weight(1f).testTag(OwnProfileTags.Attendance),
            )
        }
        SaqzDivider(vertical = true, modifier = Modifier.padding(vertical = metrics.subGrid))
        OwnProfileStat(
            value = stats.groupsLabel,
            label = stringResource(Res.string.profile_groups),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OwnProfileStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = SaqzTheme.colors.textPrimary,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Text(text = value, style = SaqzTheme.typography.title, color = valueColor)
        Text(text = label, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
    }
}

@Composable
private fun OwnProfileGroupRow(
    group: OwnProfileGroupUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .clickable(onClickLabel = group.name, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzAvatar(
            name = group.name,
            size = metrics.iconButtonSize,
            initialsColor = SaqzTheme.colors.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            Text(text = group.name, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
            group.details?.let { details ->
                Text(text = details, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
            }
        }
        SaqzStatusChip(
            text = group.membershipLabel,
            tone = br.com.saqz.designsystem.SaqzChipTone.Neutral,
        )
    }
}

@Composable
private fun OwnProfileAccountCard(onIntent: (OwnProfileIntent) -> Unit) {
    SaqzCard(padded = false) {
        OwnProfileAccountRow(
            icon = SaqzIcons.CreditCard,
            label = stringResource(Res.string.profile_monthly_payments),
            onClick = { onIntent(OwnProfileIntent.OpenMonthlyPayments) },
            modifier = Modifier.testTag(OwnProfileTags.MonthlyPayments),
        )
        SaqzDivider()
        OwnProfileAccountRow(
            icon = SaqzIcons.Bell,
            label = stringResource(Res.string.profile_notifications),
            onClick = { onIntent(OwnProfileIntent.OpenNotifications) },
            modifier = Modifier.testTag(OwnProfileTags.Notifications),
        )
        SaqzDivider()
        OwnProfileAccountRow(
            icon = SaqzIcons.Lock,
            label = stringResource(Res.string.profile_change_password),
            onClick = { onIntent(OwnProfileIntent.ChangePassword) },
            modifier = Modifier.testTag(OwnProfileTags.ChangePassword),
        )
        SaqzDivider()
        OwnProfileAccountRow(
            icon = SaqzIcons.ArrowRight,
            label = stringResource(Res.string.profile_sign_out),
            onClick = { onIntent(OwnProfileIntent.SignOut) },
            modifier = Modifier.testTag(OwnProfileTags.SignOut),
            muted = true,
        )
    }
}

@Composable
private fun OwnProfileAccountRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val metrics = SaqzTheme.metrics
    val color = if (muted) SaqzTheme.colors.textSecondary else SaqzTheme.colors.textPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzIcon(icon = icon, tint = color, size = metrics.iconButtonSize / 2)
        Text(
            text = label,
            style = SaqzTheme.typography.body,
            color = color,
            modifier = Modifier.weight(1f),
        )
        SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary, size = metrics.iconButtonSize / 2)
    }
}

@Composable
private fun OwnProfilePreview(state: OwnProfileState) {
    val context = LocalPlatformContext.current
    val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
    OwnProfileScreen(state = state, onIntent = {}, imageLoader = imageLoader)
}

internal object OwnProfilePreviewData {
    private val fake = FakeProfileGateway()
    private val user = fake.profile.user.toOwnProfileUserUi()
    private val stats = fake.stats.toOwnProfileStatsUi()
    private val groups = fake.athleteProfile.memberships.map { it.toOwnProfileGroupUi() }

    val filled = OwnProfileState(
        isLoading = false,
        user = user,
        stats = stats,
        groups = groups,
    )
    val noAttendance = filled.copy(stats = stats.copy(attendanceLabel = null))
    val empty = filled.copy(groups = emptyList())
    val loading = OwnProfileState()
    val error = OwnProfileState(isLoading = false, loadError = true)
}

@Preview(name = "7a — perfil", widthDp = 390, heightDp = 844)
@Composable
private fun OwnProfileFilledPreview() = SaqzTheme {
    OwnProfilePreview(OwnProfilePreviewData.filled)
}

@Preview(name = "7a — sem presença", widthDp = 390, heightDp = 844)
@Composable
private fun OwnProfileNoAttendancePreview() = SaqzTheme {
    OwnProfilePreview(OwnProfilePreviewData.noAttendance)
}

@Preview(name = "7a — sem grupo", widthDp = 390, heightDp = 844)
@Composable
private fun OwnProfileEmptyPreview() = SaqzTheme {
    OwnProfilePreview(OwnProfilePreviewData.empty)
}

@Preview(name = "7a — carregando", widthDp = 390, heightDp = 844)
@Composable
private fun OwnProfileLoadingPreview() = SaqzTheme {
    OwnProfilePreview(OwnProfilePreviewData.loading)
}

@Preview(name = "7a — erro", widthDp = 390, heightDp = 844)
@Composable
private fun OwnProfileErrorPreview() = SaqzTheme {
    OwnProfilePreview(OwnProfilePreviewData.error)
}
