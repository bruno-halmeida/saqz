package br.com.saqz.access.presentation

fun isValidDisplayName(input: String): Boolean {
    val value = input.trim()
    return value.length in 2..80 && value.none(Char::isISOControl)
}

fun normalizedDisplayName(input: String): String? =
    input.trim().takeIf(::isValidDisplayName)

/**
 * Normalizes a Brazilian mobile phone to E.164 (+55 + 2-digit DDD + leading 9 + 8 digits),
 * accepting common masked inputs; returns null for anything else (including landlines).
 */
fun normalizedBrMobilePhone(input: String): String? {
    val digits = input.filter(Char::isDigit)
    val national = when {
        digits.length == 13 && digits.startsWith("55") -> digits.drop(2)
        digits.length == 11 -> digits
        else -> return null
    }
    val ddd = national.take(2)
    if (ddd[0] == '0' || ddd == "10") return null
    if (national[2] != '9') return null
    return "+55$national"
}
