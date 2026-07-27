package br.com.saqz.designsystem.theme

import androidx.compose.runtime.Immutable

// Two primitive booleans a native adapter can publish; no typographic preference
// belongs here — Compose owns font scale and Dynamic Type.
@Immutable
data class SaqzAccessibilityPreferences(
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
)
