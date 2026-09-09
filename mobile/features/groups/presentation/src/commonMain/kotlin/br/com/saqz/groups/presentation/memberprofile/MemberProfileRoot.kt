package br.com.saqz.groups.presentation.memberprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.connected_load_failure_title
import br.com.saqz.groups.resources.member_profile_title
import br.com.saqz.groups.resources.member_profile_games
import br.com.saqz.groups.resources.member_profile_attendance
import br.com.saqz.groups.resources.member_profile_absences
import br.com.saqz.groups.resources.member_profile_stats_retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.tooling.preview.Preview

object MemberProfileTags {
    const val Screen = "member-profile"
    const val Phone = "member-profile-phone"
}

@Composable
fun MemberProfileRoot(groupId: String, userId: String, onBack: () -> Unit) {
    val viewModel: MemberProfileViewModel = koinViewModel(
        key = "member-profile/$groupId/$userId", parameters = { parametersOf(groupId, userId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    MemberProfileScreen(state, onBack) { viewModel.onIntent(MemberProfileIntent.Retry) }
}

@Composable
internal fun MemberProfileScreen(state: MemberProfileState, onBack: () -> Unit, onRetry: () -> Unit) {
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
                }
            }
        }
    }
}

@Preview
@Composable
private fun MemberProfilePreview() = SaqzTheme {
    MemberProfileScreen(MemberProfileState(loading = false, name = "Ana Souza", attributes = listOf("Ponteira")), {}, {})
}
