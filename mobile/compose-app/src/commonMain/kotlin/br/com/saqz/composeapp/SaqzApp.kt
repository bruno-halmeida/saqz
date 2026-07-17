package br.com.saqz.composeapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import br.com.saqz.composeapp.navigation.AuthenticatedAccessRuntime
import br.com.saqz.composeapp.navigation.AccessRuntimeIntent
import br.com.saqz.composeapp.navigation.AccessRuntime
import br.com.saqz.composeapp.shell.SaqzAppShell
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SaqzAppRuntime(
    dependencies: SaqzAppDependencies,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val access = AccessRuntime(dependencies, scope)
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        access.onIntent(AccessRuntimeIntent.Close)
        scope.cancel()
    }
}

@Composable
fun SaqzApp(
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
) = SaqzApp(
    dependencies = SaqzAppDependencies.Unconfigured,
    reduceMotion = reduceMotion,
    reduceTransparency = reduceTransparency,
)

@Composable
fun SaqzApp(
    dependencies: SaqzAppDependencies,
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
) {
    val runtime = remember(dependencies) { SaqzAppRuntime(dependencies) }
    DisposableEffect(runtime) {
        onDispose(runtime::close)
    }
    SaqzApp(runtime, reduceMotion, reduceTransparency)
}

@Composable
fun SaqzApp(
    runtime: SaqzAppRuntime,
    reduceMotion: Boolean = false,
    reduceTransparency: Boolean = false,
) {
    SaqzTheme(
        preferences = SaqzAppEnvironment(
            reduceMotion = reduceMotion,
            reduceTransparency = reduceTransparency,
        ).toPreferences(),
    ) {
        AuthenticatedAccessRuntime(runtime.access)
    }
}

// Retained as a package-local initialization fixture while native launchers migrate to
// SaqzAppDependencies. Product entry points use the authenticated root above.
@Composable
internal fun SaqzApp(environment: SaqzAppEnvironment) {
    SaqzAppShell(environment = environment)
}
