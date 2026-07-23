package br.com.saqz.designsystem.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed interface UiText {
    data class Res(val res: StringResource, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(res)
    } else {
        stringResource(res, *args.toTypedArray())
    }
}
