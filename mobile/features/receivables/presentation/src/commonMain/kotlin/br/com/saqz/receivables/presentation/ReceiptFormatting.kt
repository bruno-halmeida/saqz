package br.com.saqz.receivables.presentation

/** Decimal point movement only; never recomputes a quote or converts money through Double. */
internal fun receiptPercentage(fraction: String): String {
    val parts = fraction.lowercase().split('e')
    val mantissa = parts.first().split('.')
    val digits = mantissa.joinToString("")
    val point = mantissa.first().length + (parts.getOrNull(1)?.toIntOrNull() ?: 0) + 2
    val shifted = when {
        point <= 0 -> "0." + "0".repeat(-point) + digits
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.take(point) + "." + digits.drop(point)
    }
    val whole = shifted.substringBefore('.').trimStart('0').ifEmpty { "0" }
    val decimal = shifted.substringAfter('.', "").trimEnd('0')
    return whole + (if (decimal.isEmpty()) "" else ",$decimal") + "%"
}
