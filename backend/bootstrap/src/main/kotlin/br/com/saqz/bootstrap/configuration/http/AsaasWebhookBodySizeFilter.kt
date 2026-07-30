package br.com.saqz.bootstrap.configuration.http

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Caps anonymous Asaas webhook bodies before Spring materializes `@RequestBody String`.
 * Multipart max-request-size does not apply to this raw JSON POST.
 */
class AsaasWebhookBodySizeFilter(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val onTooLarge: (HttpServletRequest, HttpServletResponse) -> Unit,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI != WEBHOOK_PATH

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val declared = request.contentLengthLong
        if (declared >= 0 && declared > maxBytes) {
            onTooLarge(request, response)
            return
        }

        val wrapped = LimitedBodyRequest(request, maxBytes)
        try {
            filterChain.doFilter(wrapped, response)
        } catch (failure: Exception) {
            if (isBodyTooLarge(failure)) {
                if (!response.isCommitted) onTooLarge(request, response)
                return
            }
            throw failure
        }
    }

    private fun isBodyTooLarge(failure: Throwable): Boolean {
        var cursor: Throwable? = failure
        while (cursor != null) {
            if (cursor is BodyTooLargeException) return true
            cursor = cursor.cause
        }
        return false
    }

    companion object {
        const val WEBHOOK_PATH = "/webhooks/asaas"
        const val DEFAULT_MAX_BYTES: Long = 64 * 1024
    }
}

class BodyTooLargeException(message: String = "request body exceeds limit") : IOException(message)

private class LimitedBodyRequest(
    request: HttpServletRequest,
    private val maxBytes: Long,
) : HttpServletRequestWrapper(request) {
    private val limitedInput = LimitedServletInputStream(request.inputStream, maxBytes)
    private var reader: java.io.BufferedReader? = null

    override fun getInputStream(): ServletInputStream = limitedInput

    override fun getReader(): java.io.BufferedReader {
        if (reader == null) {
            val charset = characterEncoding?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: StandardCharsets.UTF_8
            reader = InputStreamReader(limitedInput, charset).buffered()
        }
        return reader!!
    }
}

private class LimitedServletInputStream(
    private val delegate: ServletInputStream,
    private val maxBytes: Long,
) : ServletInputStream() {
    private var totalRead = 0L

    override fun read(): Int {
        val value = delegate.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) account(n.toLong())
        return n
    }

    override fun isFinished(): Boolean = delegate.isFinished

    override fun isReady(): Boolean = delegate.isReady

    override fun setReadListener(readListener: ReadListener?) = delegate.setReadListener(readListener)

    override fun close() = delegate.close()

    private fun account(count: Long) {
        totalRead += count
        if (totalRead > maxBytes) throw BodyTooLargeException()
    }
}
