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

    private fun pad2(value: Int): String =
        if (value < 10) "0$value" else value.toString()
}
