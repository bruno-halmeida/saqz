package br.com.saqz.groups.domain

import java.time.ZoneId

@JvmInline
value class IanaTimeZone private constructor(
    val value: String,
) {
    companion object {
        private val availableZoneIds = ZoneId.getAvailableZoneIds()

        fun from(raw: String): IanaTimeZone {
            require(raw in availableZoneIds) { "timezone must be a valid IANA identifier" }
            return IanaTimeZone(raw)
        }
    }
}
