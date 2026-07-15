package br.com.saqz.composeapp

import androidx.compose.runtime.Immutable
import br.com.saqz.core.common.state.SaqzUiState
import br.com.saqz.designsystem.theme.SaqzAccessibilityPreferences

// Test seam for the app root: the startup state that drives the shell's state host plus the two
// primitive accessibility booleans. The native boundary only ever supplies the two booleans;
// startupState defaults to Content(Unit), so no core SaqzUiState type crosses into native code.
@Immutable
internal data class SaqzAppEnvironment(
    val startupState: SaqzUiState<Unit> = SaqzUiState.Content(Unit),
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
)

internal fun SaqzAppEnvironment.toPreferences() = SaqzAccessibilityPreferences(
    reduceMotion = reduceMotion,
    reduceTransparency = reduceTransparency,
)
