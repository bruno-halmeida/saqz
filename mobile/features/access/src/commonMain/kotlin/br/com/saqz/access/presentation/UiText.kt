package br.com.saqz.access.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed interface UiText {
    data class Res(val res: StringResource, val args: List<Any> = emptyList()) : UiText
    data class Raw(val value: String) : UiText
}

@Composable
// A API de stringResource só aceita vararg; o spread é o único caminho aqui.
@Suppress("SpreadOperator")
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(res)
    } else {
        stringResource(res, *args.toTypedArray())
    }
}
