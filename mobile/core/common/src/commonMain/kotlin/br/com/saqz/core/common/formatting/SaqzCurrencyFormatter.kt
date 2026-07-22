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

fun formatBrlPlain(cents: Long): String =
    formatBrl(cents).removePrefix("R$").trimStart(NBSP, ' ')

fun parseBrlToCents(input: String): Long? {
    val normalized = input
        .trim()
        .replace("R$", "", ignoreCase = true)
        .replace(NBSP.toString(), "")
        .trim()
        .replace(".", "")
    if (!Regex("""\d{1,8}([,.]\d{0,2})?""").matches(normalized)) return null
    val parts = normalized.replace(',', '.').split('.')
    val reais = parts[0].toLongOrNull() ?: return null
    val decimals = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    return reais * 100 + decimals
}

fun sanitizeBrlInput(input: String): String {
    val normalized = when {
        ',' in input -> input.replace(".", "")
        input.count { it == '.' } == 1 && input.substringAfter('.').count(Char::isDigit) <= 2 -> input.replace('.', ',')
        else -> input.replace(".", "")
    }
    val separator = normalized.indexOf(',')
    val reaisSource = if (separator >= 0) normalized.substring(0, separator) else normalized
    val reais = reaisSource.filter(Char::isDigit).take(8)
    if (separator < 0) return reais
    val decimals = normalized.substring(separator + 1).filter(Char::isDigit).take(2)
    return "$reais,$decimals"
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
