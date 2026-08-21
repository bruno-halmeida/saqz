package br.com.saqz.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityTagTest {
    @Test
    fun `already strong tags are unchanged`() {
        assertEquals("\"7\"", "\"7\"".toStrongEntityTag())
        assertEquals("\"photo-1\"", "\"photo-1\"".toStrongEntityTag())
    }

    @Test
    fun `weak validator prefix is stripped`() {
        assertEquals("\"1\"", "W/\"1\"".toStrongEntityTag())
        assertEquals("\"photo-1\"", "W/\"photo-1\"".toStrongEntityTag())
    }

    @Test
    fun `unquoted numeric tags are quoted`() {
        assertEquals("\"1\"", "1".toStrongEntityTag())
    }

    @Test
    fun `wildcard is left intact`() {
        assertEquals("*", "*".toStrongEntityTag())
    }

    @Test
    fun `entity tag header names are detected case insensitively`() {
        assertTrue(isEntityTagHeader("ETag"))
        assertTrue(isEntityTagHeader("if-match"))
        assertTrue(isEntityTagHeader("If-None-Match"))
        assertFalse(isEntityTagHeader("Authorization"))
    }
}
