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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Mesmo nome que o `RequestCorrelationFilter` do backend lê e ecoa. */
internal const val CORRELATION_HEADER = "X-Correlation-ID"
internal const val CORRELATION_ATTEMPT_HEADER = "X-Correlation-Attempt"

@OptIn(ExperimentalUuidApi::class)
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
        // Um id por operação (os retries compartilham, via CorrelationContext) e o número da
        // tentativa. Nasce no app para ele conhecê-lo mesmo quando a resposta não chega; o
        // backend ecoa o id no header e no ApiProblem.
        val operation = currentCoroutineContext()[CorrelationContext]
        val operationId = operation?.id ?: Uuid.random().toString()
        val attempt = operation?.attempt ?: 1
        val correlationId = "$operationId attempt=$attempt"
        val started = TimeSource.Monotonic.markNow()
        logStyle.logRequest(logger, method, bearerToken != null, correlationId)
        return try {
            val response = client.request(config.baseUrl) {
                this.method = method
                url {
                    appendPathSegments(path.trimStart('/'))
                    request.query.forEach { (name, value) -> parameters.append(name, value) }
                }
                if (bearerToken != null) bearerAuth(bearerToken)
                header(CORRELATION_HEADER, operationId)
                header(CORRELATION_ATTEMPT_HEADER, attempt.toString())
                request.headers.forEach { (name, value) ->
                    header(name, if (isEntityTagHeader(name)) value.toStrongEntityTag() else value)
                }
                request.body?.let { body ->
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                configure()
            }
            val result = if (response.status.value in 200..299) decode(response) else response.toBoundedError()
            logStyle.logResponse(logger, method, response.status.value, started, result, correlationId)
            result
        } catch (_: HttpRequestTimeoutException) {
            failure(method, logStyle, started, NetworkError.Timeout, correlationId)
        } catch (_: SocketTimeoutException) {
            failure(method, logStyle, started, NetworkError.Timeout, correlationId)
        } catch (_: UnresolvedAddressException) {
            failure(method, logStyle, started, NetworkError.Connectivity, correlationId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: MediaLimitException) {
            failure(method, logStyle, started, NetworkError.PayloadTooLarge, correlationId)
        } catch (failure: Throwable) {
            failure(method, logStyle, started, NetworkError.Unknown, correlationId, failure::class.simpleName)
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

    @Suppress("LongParameterList")
    private fun <T> failure(
        method: HttpMethod,
        logStyle: TransportLogStyle,
        started: TimeMark,
        error: NetworkError,
        correlationId: String,
        cause: String? = null,
    ): NetworkResult<T> = NetworkResult.Failure(error).also {
        logStyle.logResponse(logger, method, null, started, it, correlationId, cause)
    }
}

internal sealed interface TransportLogStyle {
    fun logRequest(logger: NetworkCallLogger, method: HttpMethod, authenticated: Boolean, correlationId: String)

    @Suppress("LongParameterList")
    fun logResponse(
        logger: NetworkCallLogger,
        method: HttpMethod,
        status: Int?,
        started: TimeMark,
        result: NetworkResult<*>,
        correlationId: String,
        cause: String? = null,
    )

    data class Standard(private val requestDescription: String) : TransportLogStyle {
        override fun logRequest(logger: NetworkCallLogger, method: HttpMethod, authenticated: Boolean, correlationId: String) {
            logger.safeLog("request $requestDescription authenticated=$authenticated correlationId=$correlationId")
        }

        @Suppress("LongParameterList")
        override fun logResponse(
            logger: NetworkCallLogger,
            method: HttpMethod,
            status: Int?,
            started: TimeMark,
            result: NetworkResult<*>,
            correlationId: String,
            cause: String?,
        ) = logResponse(logger, requestDescription, status, started, result, correlationId, cause)
    }

    data class Media(private val safePath: String) : TransportLogStyle {
        override fun logRequest(logger: NetworkCallLogger, method: HttpMethod, authenticated: Boolean, correlationId: String) {
            logger.safeLog("request ${method.value} $safePath correlationId=$correlationId")
        }

        @Suppress("LongParameterList")
        override fun logResponse(
            logger: NetworkCallLogger,
            method: HttpMethod,
            status: Int?,
            started: TimeMark,
            result: NetworkResult<*>,
            correlationId: String,
            cause: String?,
        ) = logMediaResponse(logger, method, safePath, status, started, correlationId)
    }
}

internal fun HttpResponse.metadata() = NetworkResponseMetadata(
    status = status.value,
    headers = headers.entries().associate { (name, values) ->
        name to values.map { value -> if (isEntityTagHeader(name)) value.toStrongEntityTag() else value }
    },
)
