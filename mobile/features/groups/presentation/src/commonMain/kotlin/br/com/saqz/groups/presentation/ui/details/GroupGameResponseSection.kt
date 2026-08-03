package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_response_auto_confirmation
import br.com.saqz.groups.resources.game_response_auto_confirmation_failed
import br.com.saqz.groups.resources.game_response_confirmed
import br.com.saqz.groups.resources.game_response_declined
import br.com.saqz.groups.resources.game_response_deadline_closed
import br.com.saqz.groups.resources.game_response_day_member_fee
import br.com.saqz.groups.resources.game_response_no
import br.com.saqz.groups.resources.game_response_question
import br.com.saqz.groups.resources.game_response_request_failed
import br.com.saqz.groups.resources.game_response_retry_roster
import br.com.saqz.groups.resources.game_response_roster_stale
import br.com.saqz.groups.resources.game_response_waitlisted
import br.com.saqz.groups.resources.game_response_waitlisted_unknown
import br.com.saqz.groups.resources.game_response_yes
import org.jetbrains.compose.resources.stringResource

internal object GroupGameResponseTags {
    const val Section = "group-game-response-section"
    const val Going = "group-game-response-going"
    const val NotGoing = "group-game-response-not-going"
    const val AutoConfirmation = "group-game-response-auto-confirmation"
}

@Composable
internal fun GroupGameResponseSection(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val response = state.memberResponse
    val canRespond = state.nextGame?.confirmationOpen == true && !state.responding && !state.rosterRefreshing
    SaqzCard(modifier = modifier.testTag(GroupGameResponseTags.Section)) {
        Text(
            text = stringResource(Res.string.game_response_question),
            color = SaqzTheme.colors.textPrimary,
            style = SaqzTheme.typography.title,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            SaqzButton(
                label = stringResource(Res.string.game_response_yes),
                onClick = { onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Confirm)) },
                modifier = Modifier.weight(1f).testTag(GroupGameResponseTags.Going),
                variant = if (response?.status == GroupDetailsResponseStatus.Confirmed) {
                    SaqzButtonVariant.Primary
                } else {
                    SaqzButtonVariant.Secondary
                },
                enabled = canRespond,
                loading = state.responding && response?.status == GroupDetailsResponseStatus.Confirmed,
            )
            SaqzButton(
                label = stringResource(Res.string.game_response_no),
                onClick = { onIntent(GroupDetailsIntent.Respond(AttendanceIntent.Decline)) },
                modifier = Modifier.weight(1f).testTag(GroupGameResponseTags.NotGoing),
                variant = if (response?.status == GroupDetailsResponseStatus.Declined) {
                    SaqzButtonVariant.Danger
                } else {
                    SaqzButtonVariant.Secondary
                },
                enabled = canRespond,
                loading = state.responding && response?.status == GroupDetailsResponseStatus.Declined,
            )
        }
        GroupResponseFeedback(response)
        if (state.nextGame?.confirmationOpen != true) {
            Text(
                text = stringResource(Res.string.game_response_deadline_closed),
                color = SaqzTheme.colors.textSecondary,
                style = SaqzTheme.typography.support,
            )
        }
        if (state.membershipType == AthleteMembershipType.AVULSO && state.nextGame?.hasGameFee == true) {
            Text(
                text = stringResource(Res.string.game_response_day_member_fee),
                color = SaqzTheme.colors.textSecondary,
                style = SaqzTheme.typography.support,
            )
        }
        if (state.responseFailed) {
            Text(
                text = stringResource(Res.string.game_response_request_failed),
                color = SaqzTheme.colors.errorForeground,
                style = SaqzTheme.typography.support,
            )
        }
        if (state.rosterStale) {
            Text(
                text = stringResource(Res.string.game_response_roster_stale),
                color = SaqzTheme.colors.errorForeground,
                style = SaqzTheme.typography.support,
            )
            SaqzButton(
                label = stringResource(Res.string.game_response_retry_roster),
                onClick = { onIntent(GroupDetailsIntent.RetryRoster) },
                variant = SaqzButtonVariant.Ghost,
                enabled = !state.rosterRefreshing,
                loading = state.rosterRefreshing,
                fullWidth = true,
            )
        }
        if (state.autoConfirmationVisible) {
            SaqzSwitch(
                checked = state.autoConfirmationEnabled,
                onCheckedChange = { onIntent(GroupDetailsIntent.ToggleAutoConfirmation(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = metrics.subGrid)
                    .testTag(GroupGameResponseTags.AutoConfirmation),
                label = stringResource(Res.string.game_response_auto_confirmation),
                enabled = !state.autoConfirmationUpdating,
            )
            if (state.autoConfirmationFailed) {
                Text(
                    text = stringResource(Res.string.game_response_auto_confirmation_failed),
                    color = SaqzTheme.colors.errorForeground,
                    style = SaqzTheme.typography.support,
                )
            }
        }
    }
}

@Composable
private fun GroupResponseFeedback(response: GroupDetailsResponseUi?) {
    val text = when (response?.status) {
        GroupDetailsResponseStatus.Confirmed -> stringResource(Res.string.game_response_confirmed)
        GroupDetailsResponseStatus.Declined -> stringResource(Res.string.game_response_declined)
        GroupDetailsResponseStatus.Waitlisted -> response.waitlistPosition?.let {
            stringResource(Res.string.game_response_waitlisted, it)
        } ?: stringResource(Res.string.game_response_waitlisted_unknown)
        null -> null
    } ?: return
    Text(text = text, color = SaqzTheme.colors.textSecondary, style = SaqzTheme.typography.body)
}
