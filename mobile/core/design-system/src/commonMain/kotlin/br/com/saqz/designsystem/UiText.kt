package br.com.saqz.designsystem

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

sealed interface UiText {
    data class Res(val res: StringResource, val args: List<Any> = emptyList()) : UiText

    /** Frase que concorda com um número: [quantity] escolhe a forma, [args] preenchem o modelo. */
    data class Plural(val res: PluralStringResource, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val value: String) : UiText
}

@Composable
// A API de stringResource só aceita vararg; o spread é o único caminho aqui.
@Suppress("SpreadOperator")
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Plural -> pluralStringResource(res, quantity, *args.toTypedArray())
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(res)
    } else {
        stringResource(res, *args.toTypedArray())
    }
}
