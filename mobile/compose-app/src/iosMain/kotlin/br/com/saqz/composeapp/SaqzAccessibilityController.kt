package br.com.saqz.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Runtime accessibility boundary between the native launcher and Compose. Swift owns a single
// instance, observes the two UIKit preferences and pushes them here. The controller carries
// only two booleans — Compose stays the owner of semantics, focus and Dynamic Type.
class SaqzAccessibilityController {
    private var reduceMotion by mutableStateOf(false)
    private var reduceTransparency by mutableStateOf(false)

    fun update(reduceMotion: Boolean, reduceTransparency: Boolean) {
        this.reduceMotion = reduceMotion
        this.reduceTransparency = reduceTransparency
    }

    @Composable
    internal fun Content(dependencies: SaqzPlatformDependencies) {
        SaqzApp(
            dependencies = dependencies,
            reduceMotion = reduceMotion,
            reduceTransparency = reduceTransparency,
        )
    }
}
