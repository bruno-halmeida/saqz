package br.com.saqz.groups.adapter.input.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuotedResourceVersionTest {
    @Test
    fun `strong quoted version is accepted`() {
        assertEquals(7, QuotedResourceVersion.parse("\"7\""))
    }

    @Test
    fun `weak quoted version from a compressing proxy is accepted`() {
        assertEquals(1, QuotedResourceVersion.parse("W/\"1\""))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(12, QuotedResourceVersion.parse("  W/\"12\"  "))
    }

    @Test
    fun `unquoted version stays malformed`() {
        assertFailsWith<InvalidGroupRequestException> { QuotedResourceVersion.parse("1") }
    }

    @Test
    fun `missing header is a precondition`() {
        assertFailsWith<PreconditionRequiredException> { QuotedResourceVersion.parseRequired(null) }
    }

    @Test
    fun `zero and photo tags are rejected`() {
        assertFailsWith<InvalidGroupRequestException> { QuotedResourceVersion.parse("\"0\"") }
        assertFailsWith<InvalidGroupRequestException> { QuotedResourceVersion.parse("W/\"photo-1\"") }
    }
}
