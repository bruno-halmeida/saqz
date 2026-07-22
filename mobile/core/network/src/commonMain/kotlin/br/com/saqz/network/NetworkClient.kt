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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.ContentType
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import io.ktor.utils.io.readRemaining
import io.ktor.util.network.UnresolvedAddressException
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class NetworkClient(
    engine: HttpClientEngine,
    private val config: NetworkConfig,
    private val logger: NetworkCallLogger = NetworkCallLogger {},
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

    suspend fun uploadMedia(
        method: HttpMethod,
        path: String,
        upload: NetworkMediaUpload,
        bearerToken: String? = null,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<Unit> {
        if (upload.contentLength > config.maxBinaryBodyBytes) {
            return NetworkResult.Failure(NetworkError.PayloadTooLarge)
        }
        val content = BoundedMultipartContent(upload, config.maxBinaryBodyBytes.toLong())
        val result = executeMediaRequest(method, path, bearerToken, request) {
            upload.etag?.let { header(HttpHeaders.IfMatch, it) }
            setBody(content)
        }
        if (content.limitExceeded) return NetworkResult.Failure(NetworkError.PayloadTooLarge)
        return when (result) {
            is NetworkResult.Success -> {
                result.value.bodyAsChannel().cancel()
                NetworkResult.Success(Unit, result.metadata)
            }
            is NetworkResult.Failure -> result
        }
    }

    suspend fun readBinary(
        path: String,
        bearerToken: String? = null,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<NetworkBinaryBody> = executeBinaryRead(HttpMethod.Get, path, bearerToken, request) { response ->
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > config.maxBinaryBodyBytes) {
            response.bodyAsChannel().cancel()
            return@executeBinaryRead NetworkResult.Failure(NetworkError.PayloadTooLarge)
        }
        val contentType = response.headers[HttpHeaders.ContentType] ?: run {
            response.bodyAsChannel().cancel()
            return@executeBinaryRead NetworkResult.Failure(NetworkError.InvalidResponse)
        }
        val channel = response.bodyAsChannel()
        try {
            val bytes = channel.readRemaining(config.maxBinaryBodyBytes.toLong() + 1).readByteArray()
            if (bytes.size > config.maxBinaryBodyBytes) {
                NetworkResult.Failure(NetworkError.PayloadTooLarge)
            } else {
                NetworkResult.Success(
                    NetworkBinaryBody(
                        bytes = bytes,
                        contentType = contentType,
                        etag = response.headers[HttpHeaders.ETag],
                        cacheControl = response.headers[HttpHeaders.CacheControl],
                    ),
                    response.metadata(),
                )
            }
        } finally {
            channel.cancel()
        }
    }

    private suspend fun executeMediaRequest(
        method: HttpMethod,
        path: String,
        bearerToken: String?,
        request: NetworkRequest,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): NetworkResult<HttpResponse> {
        val safePath = "/${path.trimStart('/')}"
        val started = TimeSource.Monotonic.markNow()
        log("request ${method.value} $safePath")
        return try {
            val response = client.request(config.baseUrl) {
                this.method = method
                url { appendPathSegments(path.trimStart('/')) }
                if (bearerToken != null) bearerAuth(bearerToken)
                request.headers.forEach { (name, value) -> header(name, value) }
                configure()
            }
            val result = if (response.status.value in 200..299) {
                NetworkResult.Success(response, response.metadata())
            } else {
                response.toBoundedError()
            }
            logMediaResponse(logger, method, safePath, response.status.value, started)
            result
        } catch (_: HttpRequestTimeoutException) {
            logMediaResponse(logger, method, safePath, null, started)
            NetworkResult.Failure(NetworkError.Timeout)
        } catch (_: SocketTimeoutException) {
            logMediaResponse(logger, method, safePath, null, started)
            NetworkResult.Failure(NetworkError.Timeout)
        } catch (_: UnresolvedAddressException) {
            logMediaResponse(logger, method, safePath, null, started)
            NetworkResult.Failure(NetworkError.Connectivity)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: MediaLimitException) {
            logMediaResponse(logger, method, safePath, null, started)
            NetworkResult.Failure(NetworkError.PayloadTooLarge)
        } catch (_: Throwable) {
            logMediaResponse(logger, method, safePath, null, started)
            NetworkResult.Failure(NetworkError.Unknown)
        }
    }

    private suspend fun executeBinaryRead(
        method: HttpMethod,
        path: String,
        bearerToken: String?,
        request: NetworkRequest,
        decode: suspend (HttpResponse) -> NetworkResult<NetworkBinaryBody>,
    ): NetworkResult<NetworkBinaryBody> {
        val response = executeMediaRequest(method, path, bearerToken, request) {}
        return when (response) {
            is NetworkResult.Success -> decode(response.value)
            is NetworkResult.Failure -> response
        }
    }

    private suspend fun HttpResponse.toBoundedError(): NetworkResult.Failure {
        val channel = bodyAsChannel()
        return try {
            val bytes = channel.readRemaining(config.maxErrorBodyBytes.toLong() + 1).readByteArray()
            NetworkResult.Failure(
                if (bytes.size > config.maxErrorBodyBytes) NetworkError.HttpStatus(status.value)
                else mapError(status.value, bytes.decodeToString()),
            )
        } finally {
            channel.cancel()
        }
    }

    private fun HttpResponse.metadata() = NetworkResponseMetadata(
        headers.entries().associate { (name, values) -> name to values },
    )

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
            logResponse(logger, requestDescription, response.status.value, started, result)
            result
        } catch (_: HttpRequestTimeoutException) {
            failure(requestDescription, started, NetworkError.Timeout)
        } catch (_: SocketTimeoutException) {
            failure(requestDescription, started, NetworkError.Timeout)
        } catch (_: UnresolvedAddressException) {
            failure(requestDescription, started, NetworkError.Connectivity)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            failure(
                requestDescription,
                started,
                NetworkError.Unknown,
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
        logResponse(logger, requestDescription, null, started, it, cause)
    }

    private fun log(message: String) {
        logger.safeLog(message)
    }

    fun close() {
        client.close()
    }

    private fun mapError(status: Int, body: String): NetworkError {
        val problem = runCatching { json.decodeFromString(ApiProblem.serializer(), body) }.getOrNull()
        return if (problem == null) NetworkError.HttpStatus(status) else NetworkError.ApiProblemError(problem)
    }
}

expect fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient
