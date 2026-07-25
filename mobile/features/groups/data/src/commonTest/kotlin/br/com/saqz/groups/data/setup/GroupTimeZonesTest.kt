package br.com.saqz.groups.data.setup

import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupTimeZonesTest {
    @Test
    fun `system timezone provider returns a validated timezone value`() {
        var result: GroupSystemTimeZoneResult? = null

        DefaultGroupSystemTimeZonePort().detect { result = it }

        val available = assertIs<GroupSystemTimeZoneResult.Available>(result)
        assertIs<GroupTimeZone.ParseResult.Valid>(GroupTimeZones.parse(available.value.id))
    }

    @Test
    fun `invalid timezone becomes a typed failure instead of raw text`() {
        val result = GroupTimeZones.parse("Mars/Olympus")

        assertEquals(GroupTimeZone.ParseResult.Invalid, result)
    }
}
