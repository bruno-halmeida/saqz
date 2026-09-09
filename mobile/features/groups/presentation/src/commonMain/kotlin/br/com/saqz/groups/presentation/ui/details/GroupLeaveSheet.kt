package br.com.saqz.groups.presentation.ui.details

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_leave_title
import br.com.saqz.groups.resources.group_leave_description
import br.com.saqz.groups.resources.group_leave_failure
import br.com.saqz.groups.resources.group_leave_confirm
import br.com.saqz.groups.resources.group_leave_cancel
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GroupLeaveSheet(state: GroupDetailsState, onIntent: (GroupDetailsIntent) -> Unit) {
    SaqzBottomSheet(
        open = state.confirmingLeave,
        title = stringResource(Res.string.group_leave_title),
        description = stringResource(Res.string.group_leave_description),
        onClose = { onIntent(GroupDetailsIntent.CancelLeave) },
        footer = {
            SaqzButton(
                label = stringResource(Res.string.group_leave_confirm),
                onClick = { onIntent(GroupDetailsIntent.ConfirmLeave) },
                loading = state.leaving,
                fullWidth = true,
                modifier = Modifier.testTag("group-leave-confirm"),
            )
            SaqzButton(
                label = stringResource(Res.string.group_leave_cancel),
                onClick = { onIntent(GroupDetailsIntent.CancelLeave) },
                enabled = !state.leaving,
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                modifier = Modifier.testTag("group-leave-cancel"),
            )
        },
    ) {
        if (state.leaveFailed) {
            Text(
                text = stringResource(Res.string.group_leave_failure),
                color = SaqzTheme.colors.textPrimary,
                style = SaqzTheme.typography.body,
                modifier = Modifier.testTag("group-leave-error"),
            )
        }
    }
}
