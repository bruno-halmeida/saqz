package br.com.saqz.composeapp

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.theme.SaqzAccessibilityPreferences

// The accessibility boundary between the native launcher and the app root: two primitive
// booleans, nothing else. The startup-state seam died with the legacy Home/Catalog shell
// (C1) — the session gate is the only thing that decides what the app shows now.
@Immutable
internal data class SaqzAppEnvironment(
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
)

internal fun SaqzAppEnvironment.toPreferences() = SaqzAccessibilityPreferences(
    reduceMotion = reduceMotion,
    reduceTransparency = reduceTransparency,
)
