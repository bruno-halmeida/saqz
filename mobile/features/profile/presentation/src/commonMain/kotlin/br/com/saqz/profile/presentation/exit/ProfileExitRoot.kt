package br.com.saqz.profile.presentation.exit

import androidx.compose.runtime.Composable

@Composable
fun ProfileExitRoot(
    onClose: () -> Unit,
    onLogout: () -> Unit,
) {
    ProfileExitScreen(
        onClose = onClose,
        onLogout = onLogout,
    )
}
