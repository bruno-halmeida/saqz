package br.com.saqz.core.common.formatting

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

/** Hora do instante no fuso de São Paulo, como no aviso do servidor: "12h" ou "12h30". Nulo se o texto não for um instante. */
fun formatInstantClockSaoPaulo(iso: String?): String? =
    iso?.let {
        runCatching {
            val time = Instant.parse(it).toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
            if (time.minute == 0) "${time.hour}h" else "${time.hour}h${time.minute.toString().padStart(2, '0')}"
        }.getOrNull()
    }
