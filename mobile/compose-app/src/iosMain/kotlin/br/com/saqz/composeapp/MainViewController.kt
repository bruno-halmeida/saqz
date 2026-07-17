package br.com.saqz.composeapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

fun MainViewController(
    accessibilityController: SaqzAccessibilityController,
    dependencies: SaqzAppDependencies,
): UIViewController = ComposeUIViewController {
    // ponytail: launch-arg-gated preflight — lets XCUITest prove the umbrella
    // framework's compose resources resolve on-device. Absent -> app unchanged.
    if (NSProcessInfo.processInfo.arguments.contains("-saqzResourcePreflight")) {
        ResourcePreflightScreen()
    } else {
        accessibilityController.Content(dependencies)
    }
}

@Composable
private fun ResourcePreflightScreen() {
    Column {
        Text(stringResource(ResourcePreflight.sentinelString))
        Image(
            painter = painterResource(ResourcePreflight.sentinelDrawable),
            contentDescription = "preflight-sentinel-drawable",
        )
    }
}
