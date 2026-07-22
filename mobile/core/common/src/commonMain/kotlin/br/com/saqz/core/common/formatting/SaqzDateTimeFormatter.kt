package br.com.saqz.core.common.formatting

import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class SaqzDateTimeFormatter(
    private val timeZoneProvider: SaqzTimeZoneProvider,
) {
    fun formatDate(instant: Instant): String {
        val dateTime = instant.toLocalDateTime(timeZoneProvider.timeZone())
        return "${pad2(dateTime.day)}/${pad2(dateTime.monthNumber)}/${dateTime.year}"
    }

    fun formatTime(instant: Instant): String {
        val dateTime = instant.toLocalDateTime(timeZoneProvider.timeZone())
        return "${pad2(dateTime.hour)}:${pad2(dateTime.minute)}"
    }

    fun formatDateTime(instant: Instant): String =
        "${formatDate(instant)} ${formatTime(instant)}"

    fun formatLocalDatePtBr(iso: String): String = formatLocalDatePtBrString(iso)

    fun formatMonthPtBr(iso: String): String = formatMonthPtBrString(iso)

    private fun pad2(value: Int): String =
        if (value < 10) "0$value" else value.toString()
}

fun formatLocalDatePtBrString(iso: String): String {
    val parts = iso.split('-')
    if (parts.size != 3) return iso
    val (year, month, day) = parts
    if (year.length != 4 || !year.all(Char::isDigit)) return iso
    if (month.length != 2 || !month.all(Char::isDigit)) return iso
    if (day.length != 2 || !day.all(Char::isDigit)) return iso
    return "$day/$month/$year"
}

fun formatMonthPtBrString(iso: String): String {
    val parts = iso.split('-')
    if (parts.size != 2) return iso
    val (year, month) = parts
    if (year.length != 4 || !year.all(Char::isDigit)) return iso
    if (month.length != 2 || !month.all(Char::isDigit)) return iso
    return "$month/$year"
}
