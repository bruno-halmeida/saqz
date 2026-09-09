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
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.member_profile_title
import br.com.saqz.groups.resources.member_profile_games
import br.com.saqz.groups.resources.member_profile_attendance
import br.com.saqz.groups.resources.member_profile_absences
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
    Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag("member-profile")) {
        SaqzTopAppBar(title = stringResource(Res.string.member_profile_title), onBack = onBack)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            when {
                state.loading -> SaqzSpinner()
                state.error != null -> GroupLoadFailure(state.error, onRetry)
                else -> {
                    SaqzCard {
                        SaqzAvatar(name = state.name)
                        Text(state.name, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
                        state.attributes.forEach { Text(it, color = SaqzTheme.colors.textSecondary) }
                        state.phone?.let { Text(it, modifier = Modifier.testTag("member-profile-phone")) }
                    }
                    SaqzCard {
                        Text(stringResource(Res.string.member_profile_games, state.games))
                        state.attendance?.let { Text(stringResource(Res.string.member_profile_attendance, it)) }
                        Text(stringResource(Res.string.member_profile_absences, state.absences))
                    }
                }
            }
        }
    }
}
