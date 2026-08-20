package br.com.saqz.subscriptions.presentation.changeplan

import br.com.saqz.core.common.formatting.formatLocalDatePtBrString

internal fun isoDateToPtBr(iso: String): String = formatLocalDatePtBrString(iso.substringBefore('T'))

internal fun Long.toBrlString(): String {
    val reais = this / 100
    val centavos = (this % 100).toString().padStart(2, '0')
    return "R$ $reais,$centavos"
}
