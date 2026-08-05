package br.com.saqz.subscriptions.adapter.input.http

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals

class RemoteIpTest {
    @Test
    fun `uses the first hop of X-Forwarded-For when present`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 10.0.0.2")
            remoteAddr = "10.0.0.2"
        }

        assertEquals("203.0.113.7", resolveRemoteIp(request))
    }

    @Test
    fun `falls back to remoteAddr when X-Forwarded-For is absent`() {
        val request = MockHttpServletRequest().apply {
            remoteAddr = "198.51.100.9"
        }

        assertEquals("198.51.100.9", resolveRemoteIp(request))
    }
}
