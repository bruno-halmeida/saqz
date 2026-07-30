package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.core.common.formatting.formatLocalDatePtBrString

// A API já entrega o instante completo ("2026-08-30T00:00:00Z"); o formatador pt-BR só
// entende data crua ("2026-08-30"). Todo horário de assinatura chega à meia-noite UTC, e
// esta tela mostra só o dia — não vale a pena injetar um TimeZoneProvider por isso.
internal fun isoDateToPtBr(iso: String): String = formatLocalDatePtBrString(iso.substringBefore('T'))

internal fun Long.toBrlString(): String {
    val reais = this / 100
    val centavos = (this % 100).toString().padStart(2, '0')
    return "R$ $reais,$centavos"
}
