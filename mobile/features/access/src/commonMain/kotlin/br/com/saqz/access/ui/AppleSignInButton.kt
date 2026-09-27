package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Uses Apple's official native control and localized branding on iOS. */
@Composable
internal expect fun AppleSignInButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier)
