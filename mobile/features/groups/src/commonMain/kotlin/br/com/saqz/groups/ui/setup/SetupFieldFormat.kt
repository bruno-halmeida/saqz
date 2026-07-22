package br.com.saqz.groups.ui.setup

import kotlin.math.roundToInt

internal fun formatHours(minutes: Int): String =
    if (minutes % 60 == 0) (minutes / 60).toString() else (minutes / 60.0).toString().replace('.', ',')

internal fun parseHours(value: String): Int? =
    value.replace(',', '.').toDoubleOrNull()?.let { (it * 60).roundToInt() }
