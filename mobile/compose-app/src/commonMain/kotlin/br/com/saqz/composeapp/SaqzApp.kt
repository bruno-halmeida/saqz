package br.com.saqz.composeapp

import androidx.compose.runtime.Composable
import br.com.saqz.composeapp.shell.SaqzAppShell

// Native boundary: exactly two accessibility booleans, no core type and no font scale.
@Composable
fun SaqzApp(
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
) {
    SaqzApp(
        environment = SaqzAppEnvironment(
            reduceMotion = reduceMotion,
            reduceTransparency = reduceTransparency,
        ),
    )
}

@Composable
internal fun SaqzApp(environment: SaqzAppEnvironment) {
    SaqzAppShell(environment = environment)
}
