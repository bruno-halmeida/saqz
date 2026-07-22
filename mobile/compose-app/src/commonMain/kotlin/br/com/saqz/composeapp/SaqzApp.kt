package br.com.saqz.composeapp

import androidx.compose.runtime.Composable
import br.com.saqz.composeapp.navigation.AuthenticatedAccessRoute
import br.com.saqz.composeapp.shell.SaqzAppShell
import br.com.saqz.designsystem.theme.SaqzTheme

@Composable
fun SaqzApp(
    dependencies: SaqzAppDependencies,
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
) {
    SaqzTheme(
        preferences = SaqzAppEnvironment(
            reduceMotion = reduceMotion,
            reduceTransparency = reduceTransparency,
        ).toPreferences(),
    ) {
        AuthenticatedAccessRoute(dependencies)
    }
}

// Retained as a package-local initialization fixture while native launchers migrate to
// SaqzAppDependencies. Product entry points use the authenticated root above.
@Composable
internal fun SaqzApp(environment: SaqzAppEnvironment) {
    SaqzAppShell(environment = environment)
}
