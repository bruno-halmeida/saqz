package br.com.saqz.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkFailureClassifierTest {
    private class ConnectException(message: String? = null) : Exception(message)
    private class UnknownHostException(message: String? = null) : Exception(message)
    private class SocketException(message: String? = null) : Exception(message)
    private class NoRouteToHostException(message: String? = null) : Exception(message)
    private class SocketTimeoutException(message: String? = null) : Exception(message)
    private class SomethingElse(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

    @Test
    fun `jvm and android network exceptions are connectivity`() {
        listOf(ConnectException(), UnknownHostException(), SocketException("Network is unreachable"), NoRouteToHostException())
            .forEach { assertEquals(NetworkError.Connectivity, it.toTransportFailure()) }
    }

    @Test
    fun `darwin NSURLError codes are classified`() {
        fun nsError(code: Int) = SomethingElse("Exception in http request: Error Domain=NSURLErrorDomain Code=$code")
        listOf(-1009, -1004, -1005, -1020).forEach {
            assertEquals(NetworkError.Connectivity, nsError(it).toTransportFailure(), "code $it")
        }
        assertEquals(NetworkError.Timeout, nsError(-1001).toTransportFailure())
    }

    @Test
    fun `web fetch failures are connectivity`() {
        assertEquals(NetworkError.Connectivity, SomethingElse("TypeError: Failed to fetch").toTransportFailure())
        assertEquals(NetworkError.Connectivity, SomethingElse("Load failed").toTransportFailure())
    }

    @Test
    fun `cause chain is inspected and timeouts win`() {
        assertEquals(NetworkError.Connectivity, SomethingElse("wrap", UnknownHostException()).toTransportFailure())
        assertEquals(NetworkError.Timeout, SomethingElse("wrap", SocketTimeoutException()).toTransportFailure())
    }

    @Test
    fun `unrelated failures stay unknown`() {
        assertNull(SomethingElse("boom").toTransportFailure())
    }

    @Test
    fun `transport maps a connect failure to Connectivity end to end`() = runTest {
        val client = NetworkClient(
            MockEngine { throw ConnectException("Connection refused") },
            NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        )
        val result = client.execute(HttpMethod.Get, "x", serializer<Probe>())
        assertEquals(NetworkError.Connectivity, (result as NetworkResult.Failure).error)
    }

    @Serializable
    private data class Probe(val value: String = "")
}
