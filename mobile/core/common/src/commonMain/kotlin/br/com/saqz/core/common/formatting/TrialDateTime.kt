package br.com.saqz.core.common.formatting

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Date and time in the device zone; the time stays so an exact access boundary cannot be mistaken for midnight. */
fun formatInstantDateTimePtBr(iso: String?, zoneId: String = TimeZone.currentSystemDefault().id): String? =
    iso?.let {
        runCatching {
            val zone = TimeZone.of(zoneId)
            val formatter = SaqzDateTimeFormatter(SaqzTimeZoneProvider { zone })
            formatter.formatDateTime(Instant.parse(it))
        }.getOrNull()
    }
