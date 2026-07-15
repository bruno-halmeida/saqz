package br.com.saqz.core.common.formatting

private const val SAFE_INTEGER_LIMIT = 9_007_199_254_740_991L
private const val NBSP = '\u00A0'

fun formatBrl(cents: Long): String {
    require(cents in -SAFE_INTEGER_LIMIT..SAFE_INTEGER_LIMIT) {
        "cents out of safe integer range: $cents"
    }
    val negative = cents < 0
    val magnitude = if (negative) -cents else cents
    val reais = magnitude / 100
    val remainder = (magnitude % 100).toInt()
    val centsText = if (remainder < 10) "0$remainder" else remainder.toString()
    val sign = if (negative) "-" else ""
    return "${sign}R$$NBSP${groupThousands(reais)},$centsText"
}

private fun groupThousands(value: Long): String {
    val digits = value.toString()
    val firstGroup = digits.length % 3
    val builder = StringBuilder()
    for (index in digits.indices) {
        if (index != 0 && (index - firstGroup) % 3 == 0) builder.append('.')
        builder.append(digits[index])
    }
    return builder.toString()
}
