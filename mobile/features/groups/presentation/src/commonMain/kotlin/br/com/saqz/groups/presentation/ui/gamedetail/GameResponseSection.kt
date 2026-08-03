package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.Text
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseStatus
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
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
import br.com.saqz.groups.resources.game_response_waitlisted
import br.com.saqz.groups.resources.game_response_waitlisted_unknown
import br.com.saqz.groups.resources.game_response_yes
import org.jetbrains.compose.resources.stringResource

internal object GameResponseTags {
    const val Section = "game-response-section"
    const val Going = "game-response-going"
    const val NotGoing = "game-response-not-going"
    const val AutoConfirmation = "game-response-auto-confirmation"
}

@Composable
internal fun GameResponseSection(
    state: GameDetailState,
    onRespond: (AttendanceIntent) -> Unit,
    onAutoConfirmationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val response = state.memberResponse
    val canRespond = state.header?.confirmationOpen == true && !state.responding
    SaqzCard(modifier = modifier.testTag(GameResponseTags.Section)) {
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
                onClick = { onRespond(AttendanceIntent.Confirm) },
                modifier = Modifier.weight(1f).testTag(GameResponseTags.Going),
                variant = if (response?.status == GameDetailResponseStatus.Confirmed) {
                    SaqzButtonVariant.Primary
                } else {
                    SaqzButtonVariant.Secondary
                },
                enabled = canRespond,
                loading = state.responding && response?.status == GameDetailResponseStatus.Confirmed,
            )
            SaqzButton(
                label = stringResource(Res.string.game_response_no),
                onClick = { onRespond(AttendanceIntent.Decline) },
                modifier = Modifier.weight(1f).testTag(GameResponseTags.NotGoing),
                variant = if (response?.status == GameDetailResponseStatus.Declined) {
                    SaqzButtonVariant.Danger
                } else {
                    SaqzButtonVariant.Secondary
                },
                enabled = canRespond,
                loading = state.responding && response?.status == GameDetailResponseStatus.Declined,
            )
        }
        ResponseFeedback(response)
        if (!state.header?.confirmationOpen.orFalse()) {
            Text(
                text = stringResource(Res.string.game_response_deadline_closed),
                color = SaqzTheme.colors.textSecondary,
                style = SaqzTheme.typography.support,
            )
        }
        if (state.membershipType == AthleteMembershipType.AVULSO) {
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
        if (state.autoConfirmationVisible) {
            SaqzSwitch(
                checked = state.autoConfirmationEnabled,
                onCheckedChange = onAutoConfirmationChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = metrics.subGrid)
                    .testTag(GameResponseTags.AutoConfirmation),
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
private fun ResponseFeedback(response: GameDetailResponseUi?) {
    val text = when (response?.status) {
        GameDetailResponseStatus.Confirmed -> stringResource(Res.string.game_response_confirmed)
        GameDetailResponseStatus.Declined -> stringResource(Res.string.game_response_declined)
        GameDetailResponseStatus.Waitlisted -> response.waitlistPosition?.let {
            stringResource(Res.string.game_response_waitlisted, it)
        } ?: stringResource(Res.string.game_response_waitlisted_unknown)
        null -> null
    } ?: return
    Text(text = text, color = SaqzTheme.colors.textSecondary, style = SaqzTheme.typography.body)
}

private fun Boolean?.orFalse() = this == true
