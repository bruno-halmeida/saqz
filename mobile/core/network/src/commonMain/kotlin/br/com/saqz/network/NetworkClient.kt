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
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import io.ktor.utils.io.readRemaining
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.random.Random

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
            logMediaResponse(method, safePath, response.status.value, started)
            result
        } catch (_: HttpRequestTimeoutException) {
            logMediaResponse(method, safePath, null, started)
            NetworkResult.Failure(NetworkError.Timeout)
        } catch (_: SocketTimeoutException) {
            logMediaResponse(method, safePath, null, started)
            NetworkResult.Failure(NetworkError.Timeout)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: MediaLimitException) {
            logMediaResponse(method, safePath, null, started)
            NetworkResult.Failure(NetworkError.PayloadTooLarge)
        } catch (_: Throwable) {
            logMediaResponse(method, safePath, null, started)
            NetworkResult.Failure(NetworkError.Unavailable)
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

    private fun logMediaResponse(method: HttpMethod, path: String, status: Int?, started: TimeMark) {
        log("response ${method.value} $path status=${status ?: "none"} durationMs=${started.elapsedNow().inWholeMilliseconds}")
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
    NetworkError.PayloadTooLarge -> "payload-too-large"
    NetworkError.Timeout -> "timeout"
    NetworkError.Unavailable -> "unavailable"
}

expect fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient

internal class MediaLimitException : IllegalStateException()

internal class BoundedMultipartContent(
    private val upload: NetworkMediaUpload,
    private val maximumBytes: Long,
) : OutgoingContent.WriteChannelContent() {
    var limitExceeded: Boolean = false
        private set
    private val boundary = "saqz-${Random.nextLong().toString(16)}"
    private val prefix = buildString {
        append("--$boundary\r\n")
        append("Content-Disposition: form-data; name=\"")
        append(upload.fieldName)
        append("\"; filename=\"")
        append(upload.fileName)
        append("\"\r\n")
        append("Content-Type: ${upload.contentType}\r\n")
        append("Content-Length: ${upload.contentLength}\r\n\r\n")
    }.encodeToByteArray()
    private val suffix = "\r\n--$boundary--\r\n".encodeToByteArray()

    override val contentType: ContentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)
    override val contentLength: Long = prefix.size + upload.contentLength + suffix.size

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val source = upload.openChannel()
        var transferred = 0L
        val buffer = ByteArray(8 * 1024)
        try {
            channel.writeFully(prefix)
            while (true) {
                val read = source.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                transferred += read
                if (transferred > maximumBytes || transferred > upload.contentLength) failLimit()
                channel.writeFully(buffer, 0, read)
            }
            if (transferred != upload.contentLength) failLimit()
            channel.writeFully(suffix)
        } catch (failure: Throwable) {
            channel.cancel(failure)
            throw failure
        } finally {
            source.cancel()
        }
    }

    private fun failLimit(): Nothing {
        limitExceeded = true
        throw MediaLimitException()
    }
}
