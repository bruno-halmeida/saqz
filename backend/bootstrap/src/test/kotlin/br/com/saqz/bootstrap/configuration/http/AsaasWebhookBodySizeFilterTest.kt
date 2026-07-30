package br.com.saqz.bootstrap.configuration.http

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsaasWebhookBodySizeFilterTest {
    @Test
    fun `rejects oversized Content-Length before chain runs`() {
        val rejected = mutableListOf<Int>()
        val filter = AsaasWebhookBodySizeFilter(maxBytes = 64) { _, response ->
            response.status = 413
            rejected += 413
        }
        // Mock derives length from content; override declared length the filter reads.
        val requestWithDeclaredLength = object : MockHttpServletRequest("POST", AsaasWebhookBodySizeFilter.WEBHOOK_PATH) {
            override fun getContentLength(): Int = 1000
            override fun getContentLengthLong(): Long = 1000L
        }.apply { setContent(ByteArray(8)) }
        val response = MockHttpServletResponse()
        var chainRan = false

        filter.doFilter(requestWithDeclaredLength, response) { _, _ -> chainRan = true }

        assertFalse(chainRan)
        assertEquals(listOf(413), rejected)
        assertEquals(413, response.status)
    }

    @Test
    fun `allows body at or under the limit`() {
        val filter = AsaasWebhookBodySizeFilter(maxBytes = 64) { _, response -> response.status = 413 }
        val payload = ByteArray(64) { 1 }
        val request = MockHttpServletRequest("POST", AsaasWebhookBodySizeFilter.WEBHOOK_PATH).apply {
            setContent(payload)
        }
        val response = MockHttpServletResponse()
        var readBytes = 0

        filter.doFilter(request, response, FilterChain { req, _ ->
            readBytes = (req as HttpServletRequest).inputStream.readAllBytes().size
        })

        assertEquals(64, readBytes)
        assertEquals(200, response.status)
    }

    @Test
    fun `rejects body that exceeds limit while reading when Content-Length is unknown`() {
        val rejected = mutableListOf<Int>()
        val filter = AsaasWebhookBodySizeFilter(maxBytes = 16) { _, response ->
            response.status = 413
            rejected += 413
        }
        val request = object : MockHttpServletRequest("POST", AsaasWebhookBodySizeFilter.WEBHOOK_PATH) {
            override fun getContentLength(): Int = -1
            override fun getContentLengthLong(): Long = -1L
        }.apply {
            setContent(ByteArray(64) { 7 })
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, FilterChain { req, _ ->
            (req as HttpServletRequest).inputStream.readAllBytes()
        })

        assertEquals(listOf(413), rejected)
        assertEquals(413, response.status)
    }

    @Test
    fun `does not filter other paths`() {
        val filter = AsaasWebhookBodySizeFilter(maxBytes = 1) { _, response -> response.status = 413 }
        val request = MockHttpServletRequest("POST", "/api/other").apply {
            setContent(ByteArray(1024))
        }
        val response = MockHttpServletResponse()
        var chainRan = false

        filter.doFilter(request, response) { _, _ -> chainRan = true }

        assertTrue(chainRan)
        assertEquals(200, response.status)
    }
}
