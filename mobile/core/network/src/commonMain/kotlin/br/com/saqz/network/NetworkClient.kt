package br.com.saqz.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import io.ktor.utils.io.readRemaining
import kotlin.time.TimeMark
import kotlin.time.TimeSource

fun interface NetworkLogger {
    fun log(message: String)
}

class NetworkClient(
    engine: HttpClientEngine,
    private val config: NetworkConfig,
    private val logger: NetworkLogger = NetworkLogger {},
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
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
        responseSerializer: KSerializer<T>,
        bearerToken: String? = null,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<T> = executeResponse(method, path, bearerToken, request) { body ->
        json.decodeFromString(responseSerializer, body)
    }

    suspend fun executeNoContent(
        method: HttpMethod,
        path: String,
        bearerToken: String? = null,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<Unit> = executeResponse(method, path, bearerToken, request) { body ->
        require(body.isEmpty())
    }

    private suspend fun <T> executeResponse(
        method: HttpMethod,
        path: String,
        bearerToken: String?,
        request: NetworkRequest,
        decode: (String) -> T,
    ): NetworkResult<T> {
        val requestDescription = "${method.value} ${config.baseUrl.trimEnd('/')}/${path.trimStart('/')}"
        val started = TimeSource.Monotonic.markNow()
        log("request $requestDescription authenticated=${bearerToken != null}")
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
            }
            val result = if (response.status.value in 200..299) {
                val body = response.bodyAsText()
                runCatching { decode(body) }
                    .fold(
                        onSuccess = {
                            NetworkResult.Success(
                                it,
                                NetworkResponseMetadata(
                                    response.headers.entries().associate { (name, values) -> name to values },
                                ),
                            )
                        },
                        onFailure = { NetworkResult.Failure(NetworkError.InvalidResponse) },
                    )
            } else {
                val bytes = response.bodyAsChannel()
                    .readRemaining(config.maxErrorBodyBytes.toLong() + 1)
                    .readByteArray()
                val error = if (bytes.size > config.maxErrorBodyBytes) {
                    NetworkError.HttpStatus(response.status.value)
                } else {
                    mapError(response.status.value, bytes.decodeToString())
                }
                NetworkResult.Failure(error)
            }
            logResponse(requestDescription, response.status.value, started, result)
            result
        } catch (_: HttpRequestTimeoutException) {
            failure(requestDescription, started, NetworkError.Timeout)
        } catch (_: SocketTimeoutException) {
            failure(requestDescription, started, NetworkError.Timeout)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            failure(
                requestDescription,
                started,
                NetworkError.Unavailable,
                cause = failure::class.simpleName,
            )
        }
    }

    private fun <T> failure(
        requestDescription: String,
        started: TimeMark,
        error: NetworkError,
        cause: String? = null,
    ): NetworkResult<T> = NetworkResult.Failure(error).also {
        logResponse(requestDescription, null, started, it, cause)
    }

    private fun logResponse(
        requestDescription: String,
        status: Int?,
        started: TimeMark,
        result: NetworkResult<*>,
        cause: String? = null,
    ) {
        val statusDescription = status?.toString() ?: "none"
        val causeDescription = cause?.let { " cause=$it" }.orEmpty()
        log(
            "response $requestDescription status=$statusDescription " +
                "durationMs=${started.elapsedNow().inWholeMilliseconds} " +
                "result=${result.logDescription()}$causeDescription",
        )
    }

    private fun log(message: String) {
        runCatching { logger.log(message) }
    }

    fun close() {
        client.close()
    }

    private fun mapError(status: Int, body: String): NetworkError {
        val problem = runCatching { json.decodeFromString(ApiProblem.serializer(), body) }.getOrNull()
        return if (problem == null) NetworkError.HttpStatus(status) else NetworkError.ApiProblemError(problem)
    }
}

private fun NetworkResult<*>.logDescription(): String = when (this) {
    is NetworkResult.Success -> "success"
    is NetworkResult.Failure -> error.logDescription()
}

private fun NetworkError.logDescription(): String = when (this) {
    is NetworkError.ApiProblemError -> buildString {
        append("api-error")
        append(" code=${problem.code ?: "none"}")
        append(" correlationId=${problem.correlationId}")
    }
    is NetworkError.HttpStatus -> "http-error status=$status"
    NetworkError.InvalidResponse -> "invalid-response"
    NetworkError.Timeout -> "timeout"
    NetworkError.Unavailable -> "unavailable"
}

expect fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient
