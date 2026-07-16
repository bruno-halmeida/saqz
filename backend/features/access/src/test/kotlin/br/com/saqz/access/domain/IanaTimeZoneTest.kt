package br.com.saqz.access.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IanaTimeZoneTest {
    @Test
    fun `accepts a real IANA region identifier`() {
        assertEquals("America/Sao_Paulo", IanaTimeZone.from("America/Sao_Paulo").value)
    }

    @Test
    fun `rejects an unknown or offset-only timezone`() {
        listOf("Unknown/Nowhere", "+03:00", "").forEach { raw ->
            assertFailsWith<IllegalArgumentException> { IanaTimeZone.from(raw) }
        }
    }
}
