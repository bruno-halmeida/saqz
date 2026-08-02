package br.com.saqz.profile.presentation.exit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.resources.Res
import br.com.saqz.profile.resources.profile_exit_body
import br.com.saqz.profile.resources.profile_exit_confirm_action
import br.com.saqz.profile.resources.profile_exit_confirm_body
import br.com.saqz.profile.resources.profile_exit_confirm_cancel
import br.com.saqz.profile.resources.profile_exit_confirm_email_hint
import br.com.saqz.profile.resources.profile_exit_confirm_email_label
import br.com.saqz.profile.resources.profile_exit_confirm_email_placeholder
import br.com.saqz.profile.resources.profile_exit_confirm_title
import br.com.saqz.profile.resources.profile_exit_delete_action
import br.com.saqz.profile.resources.profile_exit_delete_body
import br.com.saqz.profile.resources.profile_exit_delete_error
import br.com.saqz.profile.resources.profile_exit_delete_title
import br.com.saqz.profile.resources.profile_exit_email_mismatch
import br.com.saqz.profile.resources.profile_exit_logout
import br.com.saqz.profile.resources.profile_exit_stay
import br.com.saqz.profile.resources.profile_exit_title
import org.jetbrains.compose.resources.stringResource

object ProfileExitTags {
    const val Sheet = "profile-exit-sheet"
    const val Logout = "profile-exit-logout"
    const val Stay = "profile-exit-stay"
    const val Delete = "profile-exit-delete"
    const val ConfirmationEmail = "profile-exit-confirmation-email"
    const val ConfirmDelete = "profile-exit-confirm-delete"
    const val CancelDelete = "profile-exit-cancel-delete"
}

@Composable
fun ProfileExitScreen(
    state: ProfileExitState,
    onIntent: (ProfileExitIntent) -> Unit,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzBottomSheet(
        open = true,
        onClose = onClose,
        modifier = modifier.testTag(ProfileExitTags.Sheet),
    ) {
        when (state.sheet) {
            ProfileExitSheet.Exit -> ExitSheetContent(
                onLogout = onLogout,
                onClose = onClose,
                onOpenDeleteConfirmation = {
                    onIntent(ProfileExitIntent.OpenDeleteConfirmation)
                },
            )

            ProfileExitSheet.ConfirmDelete -> DeleteConfirmationContent(
                state = state,
                onIntent = onIntent,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ExitSheetContent(
    onLogout: () -> Unit,
    onClose: () -> Unit,
    onOpenDeleteConfirmation: () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Text(
        text = stringResource(Res.string.profile_exit_title),
        style = SaqzTheme.typography.title,
        color = colors.textPrimary,
    )
    Text(
        text = stringResource(Res.string.profile_exit_body),
        style = SaqzTheme.typography.body,
        color = colors.textSecondary,
    )
    SaqzButton(
        label = stringResource(Res.string.profile_exit_logout),
        onClick = onLogout,
        fullWidth = true,
        modifier = Modifier.testTag(ProfileExitTags.Logout),
    )
    SaqzButton(
        label = stringResource(Res.string.profile_exit_stay),
        onClick = onClose,
        variant = SaqzButtonVariant.Ghost,
        fullWidth = true,
        modifier = Modifier.testTag(ProfileExitTags.Stay),
    )
    SaqzDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(colors.errorForeground.copy(alpha = 0.1f))
            .padding(metrics.blockGap),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.Top,
    ) {
        SaqzIcon(
            icon = SaqzIcons.Trash,
            tint = colors.errorForeground,
            modifier = Modifier.size(metrics.sectionGap),
        )
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Text(
                text = stringResource(Res.string.profile_exit_delete_title),
                style = SaqzTheme.typography.label,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.profile_exit_delete_body),
                style = SaqzTheme.typography.support,
                color = colors.textPrimary,
            )
        }
    }
    SaqzButton(
        label = stringResource(Res.string.profile_exit_delete_action),
        onClick = onOpenDeleteConfirmation,
        variant = SaqzButtonVariant.Danger,
        fullWidth = true,
        modifier = Modifier.testTag(ProfileExitTags.Delete),
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DeleteConfirmationContent(
    state: ProfileExitState,
    onIntent: (ProfileExitIntent) -> Unit,
    onClose: () -> Unit,
) {
    val colors = SaqzTheme.colors
    Text(
        text = stringResource(Res.string.profile_exit_confirm_title),
        style = SaqzTheme.typography.title,
        color = colors.textPrimary,
    )
    Text(
        text = stringResource(Res.string.profile_exit_confirm_body),
        style = SaqzTheme.typography.body,
        color = colors.textSecondary,
    )
    SaqzInput(
        value = state.confirmationEmail,
        onValueChange = { onIntent(ProfileExitIntent.UpdateConfirmationEmail(it)) },
        label = stringResource(Res.string.profile_exit_confirm_email_label),
        kind = SaqzInputKind.Email,
        placeholder = stringResource(Res.string.profile_exit_confirm_email_placeholder),
        helperText = stringResource(Res.string.profile_exit_confirm_email_hint, state.email),
        errorText = state.error?.let { errorText(it, state.email) },
        invalid = state.error != null,
        enabled = !state.isDeleting,
        modifier = Modifier.testTag(ProfileExitTags.ConfirmationEmail),
    )
    SaqzButton(
        label = stringResource(Res.string.profile_exit_confirm_action),
        onClick = { onIntent(ProfileExitIntent.ConfirmDelete) },
        variant = SaqzButtonVariant.Danger,
        fullWidth = true,
        loading = state.isDeleting,
        modifier = Modifier.testTag(ProfileExitTags.ConfirmDelete),
    )
    SaqzButton(
        label = stringResource(Res.string.profile_exit_confirm_cancel),
        onClick = onClose,
        variant = SaqzButtonVariant.Ghost,
        fullWidth = true,
        enabled = !state.isDeleting,
        modifier = Modifier.testTag(ProfileExitTags.CancelDelete),
    )
}

@Composable
private fun errorText(error: ProfileExitError, email: String): String = when (error) {
    ProfileExitError.EmailMismatch -> stringResource(Res.string.profile_exit_email_mismatch, email)
    ProfileExitError.DeleteFailed -> stringResource(Res.string.profile_exit_delete_error)
}

@Preview
@Composable
private fun ProfileExitScreenPreview() = SaqzTheme {
    Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        ProfileExitScreen(
            state = ProfileExitState(email = "rafael@email.com"),
            onIntent = {},
            onClose = {},
            onLogout = {},
        )
    }
}

@Preview
@Composable
private fun ProfileExitConfirmationPreview() = SaqzTheme {
    Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        ProfileExitScreen(
            state = ProfileExitState(
                email = "rafael@email.com",
                sheet = ProfileExitSheet.ConfirmDelete,
            ),
            onIntent = {},
            onClose = {},
            onLogout = {},
        )
    }
}
