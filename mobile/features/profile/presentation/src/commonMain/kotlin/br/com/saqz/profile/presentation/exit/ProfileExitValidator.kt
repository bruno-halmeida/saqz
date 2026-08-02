package br.com.saqz.profile.presentation.exit

internal fun deletionEmailMatches(expected: String, typed: String): Boolean =
    expected.trim().equals(typed.trim(), ignoreCase = true)
