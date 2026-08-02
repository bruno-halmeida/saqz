package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzGameSummaryCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.InviteLandingError
import br.com.saqz.groups.presentation.invite.InviteLandingIntent
import br.com.saqz.groups.presentation.invite.InviteLandingState
import br.com.saqz.groups.presentation.invite.InviteNextGameUi
import br.com.saqz.groups.presentation.invite.InvitePreviewUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.invite_error_expired_body
import br.com.saqz.groups.resources.invite_error_expired_title
import br.com.saqz.groups.resources.invite_error_invalid_body
import br.com.saqz.groups.resources.invite_error_invalid_title
import br.com.saqz.groups.resources.invite_error_new_invite
import br.com.saqz.groups.resources.invite_error_network_body
import br.com.saqz.groups.resources.invite_error_network_title
import br.com.saqz.groups.resources.invite_error_other_group
import br.com.saqz.groups.resources.invite_error_plan_body
import br.com.saqz.groups.resources.invite_error_plan_title
import br.com.saqz.groups.resources.invite_error_rate_body
import br.com.saqz.groups.resources.invite_error_rate_title
import br.com.saqz.groups.resources.invite_error_rate_unknown
import br.com.saqz.groups.resources.invite_error_retry
import br.com.saqz.groups.resources.invite_landing_approval_banner
import br.com.saqz.groups.resources.invite_landing_composition_men
import br.com.saqz.groups.resources.invite_landing_composition_mixed
import br.com.saqz.groups.resources.invite_landing_composition_women
import br.com.saqz.groups.resources.invite_landing_eyebrow
import br.com.saqz.groups.resources.invite_landing_invited_by
import br.com.saqz.groups.resources.invite_landing_join_action
import br.com.saqz.groups.resources.invite_landing_level_advanced
import br.com.saqz.groups.resources.invite_landing_level_beginner
import br.com.saqz.groups.resources.invite_landing_level_custom
import br.com.saqz.groups.resources.invite_landing_level_intermediate
import br.com.saqz.groups.resources.invite_landing_level_mixed
import br.com.saqz.groups.resources.invite_landing_members
import br.com.saqz.groups.resources.invite_landing_next_game
import br.com.saqz.groups.resources.invite_landing_open_banner
import br.com.saqz.groups.resources.invite_landing_other_groups
import br.com.saqz.groups.resources.invite_landing_request_action
import br.com.saqz.groups.resources.invite_landing_schedule_empty
import br.com.saqz.groups.resources.invite_landing_title
import br.com.saqz.groups.resources.invite_landing_weekday_friday
import br.com.saqz.groups.resources.invite_landing_weekday_monday
import br.com.saqz.groups.resources.invite_landing_weekday_saturday
import br.com.saqz.groups.resources.invite_landing_weekday_sunday
import br.com.saqz.groups.resources.invite_landing_weekday_thursday
import br.com.saqz.groups.resources.invite_landing_weekday_tuesday
import br.com.saqz.groups.resources.invite_landing_weekday_wednesday
import br.com.saqz.groups.resources.invite_landing_weekday_short_friday
import br.com.saqz.groups.resources.invite_landing_weekday_short_monday
import br.com.saqz.groups.resources.invite_landing_weekday_short_saturday
import br.com.saqz.groups.resources.invite_landing_weekday_short_sunday
import br.com.saqz.groups.resources.invite_landing_weekday_short_thursday
import br.com.saqz.groups.resources.invite_landing_weekday_short_tuesday
import br.com.saqz.groups.resources.invite_landing_weekday_short_wednesday
import br.com.saqz.groups.resources.invite_request_sent_body
import br.com.saqz.groups.resources.invite_request_sent_explore
import br.com.saqz.groups.resources.invite_request_sent_organizer
import br.com.saqz.groups.resources.invite_request_sent_other_group
import br.com.saqz.groups.resources.invite_request_sent_title
import br.com.saqz.groups.resources.invite_request_sent_waiting
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object InviteLandingTags {
    const val Screen = "invite-landing"
    const val Preview = "invite-landing-preview"
    const val RequestSent = "invite-request-sent"
    const val Error = "invite-error"
    const val GroupCard = "invite-group-card"
    const val NextGame = "invite-next-game"
    const val PrimaryAction = "invite-primary-action"
    const val OtherGroups = "invite-other-groups"
    const val Retry = "invite-retry"
    const val NewInvite = "invite-new-invite"
    const val OtherGroup = "invite-other-group"
}

@Composable
internal fun InviteLandingScreen(
    state: InviteLandingState,
    onBack: () -> Unit,
    onIntent: (InviteLandingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(InviteLandingTags.Screen),
    ) {
        SaqzTopAppBar(
            title = stringResource(Res.string.invite_landing_title),
            onBack = onBack,
        )
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
            state.requestSent -> InviteRequestSentScreen(
                inviterName = state.preview?.inviterName.orEmpty(),
                onExploreApp = { onIntent(InviteLandingIntent.ExploreApp) },
                onOpenAnotherGroup = { onIntent(InviteLandingIntent.OpenAnotherGroup) },
                modifier = Modifier.weight(1f),
            )
            state.error != null -> InviteErrorScreen(
                error = state.error,
                groupName = state.preview?.groupName,
                onRetry = { onIntent(InviteLandingIntent.Retry) },
                onRequestNewInvite = { onIntent(InviteLandingIntent.RequestNewInvite) },
                onOpenAnotherGroup = { onIntent(InviteLandingIntent.OpenAnotherGroup) },
                modifier = Modifier.weight(1f),
            )
            state.preview != null -> InvitePreviewScreen(
                preview = state.preview,
                isRedeeming = state.isRedeeming,
                onPrimary = { onIntent(InviteLandingIntent.PrimaryAction) },
                onOtherGroups = { onIntent(InviteLandingIntent.BrowseOtherGroups) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InvitePreviewScreen(
    preview: InvitePreviewUi,
    isRedeeming: Boolean,
    onPrimary: () -> Unit,
    onOtherGroups: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(InviteLandingTags.Preview),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Text(stringResource(Res.string.invite_landing_eyebrow), style = SaqzTheme.typography.eyebrow, color = SaqzTheme.colors.primary)
        Text(preview.groupName, style = SaqzTheme.typography.headline, color = SaqzTheme.colors.textPrimary)
        InviteGroupCard(preview)
        preview.nextGame?.let { nextGame ->
            SaqzGameSummaryCard(
                eyebrow = stringResource(Res.string.invite_landing_next_game),
                title = nextGame.title(),
                venue = nextGame.venueName,
                address = nextGame.court,
                modifier = Modifier.testTag(InviteLandingTags.NextGame),
            )
        }
        SaqzCard(tone = SaqzCardTone.Soft) {
            Text(
                text = if (preview.entryRequiresApproval) {
                    stringResource(Res.string.invite_landing_approval_banner)
                } else {
                    stringResource(Res.string.invite_landing_open_banner)
                },
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
        }
        SaqzButton(
            label = stringResource(
                if (preview.entryRequiresApproval) Res.string.invite_landing_request_action
                else Res.string.invite_landing_join_action,
            ),
            onClick = onPrimary,
            fullWidth = true,
            loading = isRedeeming,
            modifier = Modifier.testTag(InviteLandingTags.PrimaryAction),
        )
        SaqzButton(
            label = stringResource(Res.string.invite_landing_other_groups),
            onClick = onOtherGroups,
            variant = SaqzButtonVariant.Ghost,
            fullWidth = true,
            modifier = Modifier.testTag(InviteLandingTags.OtherGroups),
        )
    }
}

@Composable
private fun InviteGroupCard(preview: InvitePreviewUi) {
    SaqzCard(modifier = Modifier.testTag(InviteLandingTags.GroupCard)) {
        preview.city?.let { Text(it, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            preview.compositionCode?.toCompositionResource()?.let { stringResource(it) }?.let { InviteDetailText(it) }
            preview.levelCode?.toLevelResource()?.let { stringResource(it) }?.let { InviteDetailText(it) }
        }
        Text(
            text = stringResource(Res.string.invite_landing_members, preview.memberCount),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            text = preview.regularWeekdays.toScheduleLabel()
                ?: stringResource(Res.string.invite_landing_schedule_empty),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.invite_landing_invited_by, preview.inviterName),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun InviteNextGameUi.title(): String {
    val dateTime = if (time.isBlank()) date else "$date · $time"
    val weekday = weekdayCode.toShortWeekdayResource()?.let { stringResource(it) }
    return if (weekday == null) dateTime else "$weekday, $dateTime"
}

private fun String.toCompositionResource(): StringResource? = when (this) {
    "WOMEN" -> Res.string.invite_landing_composition_women
    "MEN" -> Res.string.invite_landing_composition_men
    "MIXED" -> Res.string.invite_landing_composition_mixed
    else -> null
}

private fun String.toLevelResource(): StringResource? = when (this) {
    "BEGINNER" -> Res.string.invite_landing_level_beginner
    "INTERMEDIATE" -> Res.string.invite_landing_level_intermediate
    "ADVANCED" -> Res.string.invite_landing_level_advanced
    "MIXED_LEVELS" -> Res.string.invite_landing_level_mixed
    "CUSTOM" -> Res.string.invite_landing_level_custom
    else -> null
}

@Composable
private fun List<String>.toScheduleLabel(): String? {
    val labels = mapNotNull { it.toWeekdayResource()?.let { resource -> stringResource(resource) } }
    return labels.takeIf { it.isNotEmpty() }?.joinToString(" e ")?.replaceFirstChar(Char::uppercaseChar)
}

private fun String.toWeekdayResource(): StringResource? = when (this) {
    "SUNDAY" -> Res.string.invite_landing_weekday_sunday
    "MONDAY" -> Res.string.invite_landing_weekday_monday
    "TUESDAY" -> Res.string.invite_landing_weekday_tuesday
    "WEDNESDAY" -> Res.string.invite_landing_weekday_wednesday
    "THURSDAY" -> Res.string.invite_landing_weekday_thursday
    "FRIDAY" -> Res.string.invite_landing_weekday_friday
    "SATURDAY" -> Res.string.invite_landing_weekday_saturday
    else -> null
}

private fun String.toShortWeekdayResource(): StringResource? = when (this) {
    "SUNDAY" -> Res.string.invite_landing_weekday_short_sunday
    "MONDAY" -> Res.string.invite_landing_weekday_short_monday
    "TUESDAY" -> Res.string.invite_landing_weekday_short_tuesday
    "WEDNESDAY" -> Res.string.invite_landing_weekday_short_wednesday
    "THURSDAY" -> Res.string.invite_landing_weekday_short_thursday
    "FRIDAY" -> Res.string.invite_landing_weekday_short_friday
    "SATURDAY" -> Res.string.invite_landing_weekday_short_saturday
    else -> null
}

@Composable
private fun InviteDetailText(text: String) = SaqzStatusChip(text = text, tone = SaqzChipTone.Neutral)

@Composable
internal fun InviteRequestSentScreen(
    inviterName: String,
    onExploreApp: () -> Unit,
    onOpenAnotherGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.sectionGap)
            .testTag(InviteLandingTags.RequestSent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.avatarSize)
                .background(SaqzTheme.colors.surfaceSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(SaqzIcons.Clock, tint = SaqzTheme.colors.primary)
        }
        Text(
            stringResource(Res.string.invite_request_sent_title),
            style = SaqzTheme.typography.headline,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            stringResource(Res.string.invite_request_sent_body, inviterName),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        SaqzCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                Box(
                    modifier = Modifier
                        .size(metrics.iconButtonSize)
                        .clip(CircleShape)
                        .background(SaqzTheme.colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = inviterName.take(1).uppercase(),
                        style = SaqzTheme.typography.subtitle,
                        color = SaqzTheme.colors.onPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(inviterName, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
                    Text(
                        stringResource(Res.string.invite_request_sent_organizer),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
                SaqzStatusChip(stringResource(Res.string.invite_request_sent_waiting), tone = SaqzChipTone.Warning)
            }
        }
        SaqzButton(stringResource(Res.string.invite_request_sent_explore), onExploreApp, fullWidth = true)
        SaqzButton(
            stringResource(Res.string.invite_request_sent_other_group),
            onOpenAnotherGroup,
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
        )
    }
}

@Composable
private fun InviteErrorScreen(
    error: InviteLandingError,
    groupName: String?,
    onRetry: () -> Unit,
    onRequestNewInvite: () -> Unit,
    onOpenAnotherGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val title: String
    val body: String
    val primaryLabel: String
    val primaryAction: () -> Unit
    when (error) {
        InviteLandingError.Invalid -> {
            title = stringResource(Res.string.invite_error_invalid_title)
            body = stringResource(Res.string.invite_error_invalid_body)
            primaryLabel = stringResource(Res.string.invite_error_new_invite)
            primaryAction = onRequestNewInvite
        }
        is InviteLandingError.Expired -> {
            title = stringResource(Res.string.invite_error_expired_title)
            body = stringResource(Res.string.invite_error_expired_body, groupName ?: "grupo", error.expiredAt)
            primaryLabel = stringResource(Res.string.invite_error_new_invite)
            primaryAction = onRequestNewInvite
        }
        is InviteLandingError.RateLimited -> {
            title = stringResource(Res.string.invite_error_rate_title)
            body = stringResource(
                Res.string.invite_error_rate_body,
                error.retryAfterSeconds?.let { "$it segundos" } ?: stringResource(Res.string.invite_error_rate_unknown),
            )
            primaryLabel = stringResource(Res.string.invite_error_retry)
            primaryAction = onRetry
        }
        InviteLandingError.PlanLimit -> {
            title = stringResource(Res.string.invite_error_plan_title)
            body = stringResource(Res.string.invite_error_plan_body)
            primaryLabel = stringResource(Res.string.invite_error_other_group)
            primaryAction = onOpenAnotherGroup
        }
        InviteLandingError.Network -> {
            title = stringResource(Res.string.invite_error_network_title)
            body = stringResource(Res.string.invite_error_network_body)
            primaryLabel = stringResource(Res.string.invite_error_retry)
            primaryAction = onRetry
        }
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.sectionGap)
            .testTag(InviteLandingTags.Error),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.avatarSize)
                .background(SaqzTheme.colors.errorForeground.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.errorForeground)
        }
        Text(title, style = SaqzTheme.typography.headline, color = SaqzTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textSecondary, textAlign = TextAlign.Center)
        SaqzButton(
            primaryLabel,
            primaryAction,
            fullWidth = true,
            modifier = Modifier.testTag(
                if (error is InviteLandingError.Network || error is InviteLandingError.RateLimited) {
                    InviteLandingTags.Retry
                } else {
                    InviteLandingTags.NewInvite
                },
            ),
        )
    }
}

internal object InviteLandingSamples {
    val preview = InviteLandingState(isLoading = false, preview = InvitePreviewUi(
        groupName = "Vôlei do CERET",
        city = "Tatuapé",
        compositionCode = "MIXED",
        levelCode = "INTERMEDIATE",
        memberCount = 26,
        regularWeekdays = listOf("TUESDAY", "THURSDAY"),
        inviterName = "Ana Lima",
        nextGame = InviteNextGameUi("TUESDAY", "04/08", "19h30", "CERET", "Quadra 2"),
        entryRequiresApproval = true,
    ))
    val openPreview = preview.copy(preview = preview.preview!!.copy(entryRequiresApproval = false))
    val requestSent = InviteLandingState(isLoading = false, requestSent = true, preview = preview.preview)
    val invalid = InviteLandingState(isLoading = false, error = InviteLandingError.Invalid)
    val expired = InviteLandingState(isLoading = false, error = InviteLandingError.Expired("31/08/2026"), preview = preview.preview)
    val rateLimited = InviteLandingState(isLoading = false, error = InviteLandingError.RateLimited(30), preview = preview.preview)
    val planLimit = InviteLandingState(isLoading = false, error = InviteLandingError.PlanLimit, preview = preview.preview)
    val network = InviteLandingState(isLoading = false, error = InviteLandingError.Network)
}

@Preview(name = "3d — preview com aprovação", widthDp = 390, heightDp = 844)
@Composable
private fun InviteLandingPreview() = SaqzTheme {
    InviteLandingScreen(InviteLandingSamples.preview, {}, {})
}

@Preview(name = "3l — entrada liberada", widthDp = 390, heightDp = 844)
@Composable
private fun InviteLandingOpenPreview() = SaqzTheme {
    InviteLandingScreen(InviteLandingSamples.openPreview, {}, {})
}

@Preview(name = "3e — pedido enviado", widthDp = 390, heightDp = 844)
@Composable
private fun InviteRequestSentPreview() = SaqzTheme {
    Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(title = stringResource(Res.string.invite_request_sent_title), onBack = {})
        InviteRequestSentScreen("Ana Lima", {}, {}, Modifier.weight(1f))
    }
}

@Preview(name = "3f — erro de limite", widthDp = 390, heightDp = 844)
@Composable
private fun InviteErrorPreview() = SaqzTheme {
    Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(title = stringResource(Res.string.invite_landing_title), onBack = {})
        InviteErrorScreen(InviteLandingSamples.planLimit.error!!, "Vôlei do CERET", {}, {}, {}, Modifier.weight(1f))
    }
}
