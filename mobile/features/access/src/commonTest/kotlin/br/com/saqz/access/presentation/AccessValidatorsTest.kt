package br.com.saqz.access.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AccessValidatorsTest {
    @Test
    fun `email accepts valid addresses and rejects malformed`() {
        assertTrue(isValidEmailAddress("ana@exemplo.com"))
        assertTrue(isValidEmailAddress("a.b@c.d"))
        assertFalse(isValidEmailAddress(""))
        assertFalse(isValidEmailAddress("sem-arroba"))
        assertFalse(isValidEmailAddress("@exemplo.com"))
        assertFalse(isValidEmailAddress("ana@"))
        assertFalse(isValidEmailAddress("ana@semdot"))
    }

    @Test
    fun `display name enforces 2 to 80 chars without control characters`() {
        assertFalse(isValidDisplayName(""))
        assertFalse(isValidDisplayName(" "))
        assertFalse(isValidDisplayName("A"))
        assertTrue(isValidDisplayName("An"))
        assertTrue(isValidDisplayName("A".repeat(80)))
        assertFalse(isValidDisplayName("A".repeat(81)))
        assertFalse(isValidDisplayName("Ana\u0000"))
        assertEquals("Ana", normalizedDisplayName("  Ana  "))
        assertNull(normalizedDisplayName("A"))
    }
}
