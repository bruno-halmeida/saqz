package br.com.saqz.composeapp.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestIdGeneratorTest {
    @Test fun `generator creates RFC 4122 version 4 identifiers`() {
        val value = UuidV4RequestIdGenerator().next()

        assertTrue(V4_UUID.matches(value), "expected v4 UUID, got $value")
        assertEquals('4', value[14])
        assertTrue(value[19] in "89ab")
    }

    @Test fun `injected generator stays deterministic for callers`() {
        val generator = RequestIdGenerator { "fixed-request-id" }

        assertEquals("fixed-request-id", generator.next())
    }

    private companion object {
        val V4_UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }
}
