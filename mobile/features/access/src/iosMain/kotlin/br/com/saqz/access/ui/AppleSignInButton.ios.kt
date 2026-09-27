package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleBlack
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeContinue
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventTouchUpInside
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
internal actual fun AppleSignInButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val callback = rememberUpdatedState(onClick)
    val target = remember { AppleButtonTarget { callback.value() } }
    UIKitView(
        factory = {
            ASAuthorizationAppleIDButton(
                authorizationButtonType = ASAuthorizationAppleIDButtonTypeContinue,
                authorizationButtonStyle = ASAuthorizationAppleIDButtonStyleBlack,
            ).apply {
                addTarget(target, NSSelectorFromString("pressed"), UIControlEventTouchUpInside)
            }
        },
        modifier = modifier,
        update = { it.enabled = enabled },
    )
}

@OptIn(BetaInteropApi::class)
private class AppleButtonTarget(private val onClick: () -> Unit) : NSObject() {
    @ObjCAction fun pressed() = onClick()
}
