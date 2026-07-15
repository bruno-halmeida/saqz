package br.com.saqz.core.common.formatting

import kotlinx.datetime.TimeZone

fun interface SaqzTimeZoneProvider {
    fun timeZone(): TimeZone
}
