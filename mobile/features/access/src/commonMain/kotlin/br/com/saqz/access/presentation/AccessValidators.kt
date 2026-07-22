package br.com.saqz.access.presentation

fun isValidEmailAddress(input: String): Boolean {
    val at = input.indexOf('@')
    return at > 0 && at < input.lastIndex && input.substring(at + 1).contains('.')
}

fun isValidDisplayName(input: String): Boolean {
    val value = input.trim()
    return value.length in 2..80 && value.none(Char::isISOControl)
}

fun normalizedDisplayName(input: String): String? =
    input.trim().takeIf(::isValidDisplayName)

fun String.isValidEmail(): Boolean = isValidEmailAddress(this)
