package br.com.saqz.access.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccessNameTest {
    @Test
    fun `normalizes surrounding whitespace on a valid name`() {
        assertEquals("Valid Name", AccessName.from("  Valid Name  ").value)
    }

    @Test
    fun `accepts both inclusive name length boundaries`() {
        assertEquals("ab", AccessName.from("ab").value)
        assertEquals(80, AccessName.from("a".repeat(80)).value.length)
    }

    @Test
    fun `rejects blank and one character names after trim`() {
        listOf("", "   ", " a ").forEach { raw ->
            assertFailsWith<IllegalArgumentException> { AccessName.from(raw) }
        }
    }

    @Test
    fun `rejects names longer than eighty characters`() {
        assertFailsWith<IllegalArgumentException> { AccessName.from("a".repeat(81)) }
    }

    @Test
    fun `rejects every control character mutation`() {
        listOf("line\nbreak", "tab\tname", "null\u0000name").forEach { raw ->
            assertFailsWith<IllegalArgumentException> { AccessName.from(raw) }
        }
    }
}
