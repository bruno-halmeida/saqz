package br.com.saqz.groups.presentation.memberprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.moderation.ReportTargetType
import br.com.saqz.groups.presentation.moderation.ModerationIntent
import br.com.saqz.groups.presentation.moderation.ModerationViewModel
import br.com.saqz.groups.presentation.moderation.ReportTargetUi
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.moderation.ModerationOverlay
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.connected_load_failure_title
import br.com.saqz.groups.resources.member_profile_title
import br.com.saqz.groups.resources.member_profile_games
import br.com.saqz.groups.resources.member_profile_attendance
import br.com.saqz.groups.resources.member_profile_absences
import br.com.saqz.groups.resources.member_profile_stats_retry
import br.com.saqz.groups.resources.moderation_action_block
import br.com.saqz.groups.resources.moderation_action_report
import br.com.saqz.groups.resources.moderation_action_unblock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.tooling.preview.Preview

object MemberProfileTags {
    const val Screen = "member-profile"
    const val Phone = "member-profile-phone"
    const val Report = "member-profile-report"
    const val Block = "member-profile-block"
}

@Composable
fun MemberProfileRoot(
    groupId: String,
    userId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    moderation: ModerationViewModel = koinViewModel(key = "moderation/member-profile/$groupId/$userId"),
) {
    val viewModel: MemberProfileViewModel = koinViewModel(
        key = "member-profile/$groupId/$userId", parameters = { parametersOf(groupId, userId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val moderationState by moderation.state.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize()) {
        MemberProfileScreen(
            state = state,
            onBack = onBack,
            onRetry = { viewModel.onIntent(MemberProfileIntent.Retry) },
            onReport = {
                moderation.onIntent(
                    ModerationIntent.StartReport(ReportTargetUi(groupId, ReportTargetType.USER, userId, state.name)),
                )
            },
            onToggleBlock = {
                moderation.onIntent(
                    if (state.blocked) {
                        ModerationIntent.Unblock(userId)
                    } else {
                        ModerationIntent.StartBlock(groupId, userId, state.name)
                    },
                )
            },
        )
        ModerationOverlay(state = moderationState, onIntent = moderation::onIntent)
    }
}

@Composable
internal fun MemberProfileScreen(
    state: MemberProfileState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReport: () -> Unit = {},
    onToggleBlock: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(MemberProfileTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.member_profile_title), onBack = onBack)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            when {
                state.loading -> SaqzSpinner()
                state.error != null -> GroupLoadFailure(
                    state.error, onRetry, failureTitle = stringResource(Res.string.connected_load_failure_title),
                )
                else -> {
                    SaqzCard {
                        SaqzAvatar(name = state.name)
                        Text(state.name, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
                        state.attributes.forEach { Text(it, color = SaqzTheme.colors.textSecondary) }
                        state.phone?.let { Text(it, modifier = Modifier.testTag(MemberProfileTags.Phone)) }
                    }
                    if (state.games != null) SaqzCard {
                        Text(stringResource(Res.string.member_profile_games, state.games))
                        state.attendance?.let { Text(stringResource(Res.string.member_profile_attendance, it)) }
                        state.absences?.let { Text(stringResource(Res.string.member_profile_absences, it)) }
                    }
                    if (state.statsFailed) SaqzButton(label = stringResource(Res.string.member_profile_stats_retry), onClick = onRetry)
                    if (state.moderationVisible) MemberModerationActions(state.blocked, onReport, onToggleBlock)
                }
            }
        }
    }
}

/** Discretas de propósito, no fim do perfil: denunciar e bloquear são exceção, não convite. */
@Composable
private fun MemberModerationActions(blocked: Boolean, onReport: () -> Unit, onToggleBlock: () -> Unit) {
    val danger = SaqzTheme.colors.errorForeground
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        SaqzButton(
            label = stringResource(Res.string.moderation_action_report),
            onClick = onReport,
            variant = SaqzButtonVariant.Ghost,
            fullWidth = true,
            leadingContent = { color -> SaqzIcon(SaqzIcons.Flag, tint = color) },
            modifier = Modifier.testTag(MemberProfileTags.Report),
        )
        SaqzButton(
            label = stringResource(if (blocked) Res.string.moderation_action_unblock else Res.string.moderation_action_block),
            onClick = onToggleBlock,
            variant = SaqzButtonVariant.Ghost,
            fullWidth = true,
            contentColor = if (blocked) null else danger,
            leadingContent = { color -> SaqzIcon(SaqzIcons.Ban, tint = color) },
            modifier = Modifier.testTag(MemberProfileTags.Block),
        )
    }
}

@Preview
@Composable
private fun MemberProfilePreview() = SaqzTheme {
    MemberProfileScreen(MemberProfileState(loading = false, name = "Ana Souza", attributes = listOf("Ponteira")), {}, {})
}
