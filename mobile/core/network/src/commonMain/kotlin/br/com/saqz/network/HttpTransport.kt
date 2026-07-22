package br.com.saqz.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class HttpTransport(
    engine: HttpClientEngine,
    private val config: NetworkConfig,
    json: Json,
    private val errorMapper: NetworkErrorMapper,
    private val logger: NetworkCallLogger,
) {
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
            connectTimeoutMillis = config.requestTimeoutMillis
            socketTimeoutMillis = config.requestTimeoutMillis
        }
    }

    suspend fun <T> execute(
        method: HttpMethod,
        path: String,
        bearerToken: String?,
        request: NetworkRequest,
        logStyle: TransportLogStyle,
        configure: HttpRequestBuilder.() -> Unit = {},
        decode: suspend (HttpResponse) -> NetworkResult<T>,
    ): NetworkResult<T> {
        val started = TimeSource.Monotonic.markNow()
        logStyle.logRequest(logger, method, bearerToken != null)
        return try {
            val response = client.request(config.baseUrl) {
                this.method = method
                url { appendPathSegments(path.trimStart('/')) }
                if (bearerToken != null) bearerAuth(bearerToken)
                request.headers.forEach { (name, value) -> header(name, value) }
                request.body?.let { body ->
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                configure()
            }
            val result = if (response.status.value in 200..299) decode(response) else response.toBoundedError()
            logStyle.logResponse(logger, method, response.status.value, started, result)
            result
        } catch (_: HttpRequestTimeoutException) {
            failure(method, logStyle, started, NetworkError.Timeout)
        } catch (_: SocketTimeoutException) {
            failure(method, logStyle, started, NetworkError.Timeout)
        } catch (_: UnresolvedAddressException) {
            failure(method, logStyle, started, NetworkError.Connectivity)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: MediaLimitException) {
            failure(method, logStyle, started, NetworkError.PayloadTooLarge)
        } catch (failure: Throwable) {
            failure(method, logStyle, started, NetworkError.Unknown, failure::class.simpleName)
        }
    }

    fun close() = client.close()

    private suspend fun HttpResponse.toBoundedError(): NetworkResult.Failure {
        val channel = bodyAsChannel()
        return try {
            val bytes = channel.readRemaining(config.maxErrorBodyBytes.toLong() + 1).readByteArray()
            NetworkResult.Failure(
                if (bytes.size > config.maxErrorBodyBytes) NetworkError.HttpStatus(status.value)
                else errorMapper.map(status.value, bytes.decodeToString()),
            )
        } finally {
            channel.cancel()
        }
    }

    private fun <T> failure(
        method: HttpMethod,
        logStyle: TransportLogStyle,
        started: TimeMark,
        error: NetworkError,
        cause: String? = null,
    ): NetworkResult<T> = NetworkResult.Failure(error).also {
        logStyle.logResponse(logger, method, null, started, it, cause)
    }
}

internal sealed interface TransportLogStyle {
    fun logRequest(logger: NetworkCallLogger, method: HttpMethod, authenticated: Boolean)

    fun logResponse(
        logger: NetworkCallLogger,
        method: HttpMethod,
        status: Int?,
        started: TimeMark,
        result: NetworkResult<*>,
        cause: String? = null,
    )

    data class Standard(private val requestDescription: String) : TransportLogStyle {
        override fun logRequest(logger: NetworkCallLogger, method: HttpMethod, authenticated: Boolean) {
            logger.safeLog("request $requestDescription authenticated=$authenticated")
        }

        override fun logResponse(
            logger: NetworkCallLogger,
            method: HttpMethod,
            status: Int?,
            started: TimeMark,
            result: NetworkResult<*>,
            cause: String?,
        ) = logResponse(logger, requestDescription, status, started, result, cause)
    }

    data class Media(private val safePath: String) : TransportLogStyle {
        override fun logRequest(logger: NetworkCallLogger, method: HttpMethod, authenticated: Boolean) {
            logger.safeLog("request ${method.value} $safePath")
        }

        override fun logResponse(
            logger: NetworkCallLogger,
            method: HttpMethod,
            status: Int?,
            started: TimeMark,
            result: NetworkResult<*>,
            cause: String?,
        ) = logMediaResponse(logger, method, safePath, status, started)
    }
}

internal fun HttpResponse.metadata() = NetworkResponseMetadata(
    headers.entries().associate { (name, values) -> name to values },
)
