package br.com.saqz.composeapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.ComposeUIViewController
import br.com.saqz.composeapp.di.startSaqzKoin
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

fun MainViewController(
    accessibilityController: SaqzAccessibilityController,
    dependencies: SaqzPlatformDependencies,
): UIViewController {
    startSaqzKoin(dependencies)
    return ComposeUIViewController {
    // ponytail: launch-arg-gated preflight — lets XCUITest prove the umbrella
    // framework's compose resources resolve on-device. Absent -> app unchanged.
    if (NSProcessInfo.processInfo.arguments.contains("-saqzResourcePreflight")) {
        ResourcePreflightScreen()
    } else {
        accessibilityController.Content(dependencies)
    }
    }
}

@Composable
private fun ResourcePreflightScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "preflight-full-screen-root" },
    ) {
        Column {
            Text(stringResource(ResourcePreflight.sentinelString))
            Image(
                painter = painterResource(ResourcePreflight.sentinelDrawable),
                contentDescription = "preflight-sentinel-drawable",
            )
        }
    }
}
