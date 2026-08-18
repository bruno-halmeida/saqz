package br.com.saqz.profile.presentation.exit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.resources.Res
import br.com.saqz.profile.resources.profile_exit_body
import br.com.saqz.profile.resources.profile_exit_logout
import br.com.saqz.profile.resources.profile_exit_stay
import br.com.saqz.profile.resources.profile_exit_title
import org.jetbrains.compose.resources.stringResource

object ProfileExitTags {
    const val Sheet = "profile-exit-sheet"
    const val Logout = "profile-exit-logout"
    const val Stay = "profile-exit-stay"
}

@Composable
fun ProfileExitScreen(
    onClose: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzBottomSheet(
        open = true,
        onClose = onClose,
        modifier = modifier.testTag(ProfileExitTags.Sheet),
    ) {
        Text(
            text = stringResource(Res.string.profile_exit_title),
            style = SaqzTheme.typography.title,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.profile_exit_body),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
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
    }
}

@Preview
@Composable
private fun ProfileExitScreenPreview() = SaqzTheme {
    Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        ProfileExitScreen(onClose = {}, onLogout = {})
    }
}
