package br.com.saqz.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.cancel
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import io.ktor.utils.io.readRemaining

class NetworkClient(
    engine: HttpClientEngine,
    private val config: NetworkConfig,
    private val logger: NetworkCallLogger = NetworkCallLogger {},
    private val json: Json = defaultNetworkJson(),
    errorMapper: NetworkErrorMapper = ApiProblemErrorMapper(json),
) {
    private val transport = HttpTransport(engine, config, json, errorMapper, logger)

    suspend fun <T> execute(
        method: HttpMethod,
        path: String,
        responseSerializer: KSerializer<T>,
        bearerToken: String? = null,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<T> = executeDecoded(method, path, bearerToken, request) { response ->
        val body = response.bodyAsText()
        runCatching { json.decodeFromString(responseSerializer, body) }
            .fold(
                onSuccess = { NetworkResult.Success(it, response.metadata()) },
                onFailure = { NetworkResult.Failure(NetworkError.InvalidResponse) },
            )
    }

    suspend fun executeNoContent(
        method: HttpMethod,
        path: String,
        bearerToken: String? = null,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<Unit> = executeDecoded(method, path, bearerToken, request) { response ->
        if (response.bodyAsText().isEmpty()) NetworkResult.Success(Unit, response.metadata())
        else NetworkResult.Failure(NetworkError.InvalidResponse)
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
        val result = transport.execute(
            method = method,
            path = path,
            bearerToken = bearerToken,
            request = request,
            logStyle = mediaLogStyle(path),
            configure = {
                upload.etag?.let { header(HttpHeaders.IfMatch, it) }
                setBody(content)
            },
        ) { response ->
            response.bodyAsChannel().cancel()
            NetworkResult.Success(Unit, response.metadata())
        }
        if (content.limitExceeded) return NetworkResult.Failure(NetworkError.PayloadTooLarge)
        return result
    }

    suspend fun readBinary(
        path: String,
        bearerToken: String? = null,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<NetworkBinaryBody> = transport.execute(
        method = HttpMethod.Get,
        path = path,
        bearerToken = bearerToken,
        request = request,
        logStyle = mediaLogStyle(path),
    ) { response ->
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > config.maxBinaryBodyBytes) {
            response.bodyAsChannel().cancel()
            return@execute NetworkResult.Failure(NetworkError.PayloadTooLarge)
        }
        val contentType = response.headers[HttpHeaders.ContentType] ?: run {
            response.bodyAsChannel().cancel()
            return@execute NetworkResult.Failure(NetworkError.InvalidResponse)
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

    private suspend fun <T> executeDecoded(
        method: HttpMethod,
        path: String,
        bearerToken: String?,
        request: NetworkRequest,
        decode: suspend (HttpResponse) -> NetworkResult<T>,
    ): NetworkResult<T> {
        val requestDescription = "${method.value} ${config.baseUrl.trimEnd('/')}/${path.trimStart('/')}"
        return transport.execute(
            method = method,
            path = path,
            bearerToken = bearerToken,
            request = request,
            logStyle = TransportLogStyle.Standard(requestDescription),
            decode = decode,
        )
    }

    fun close() {
        transport.close()
    }

    private fun mediaLogStyle(path: String) = TransportLogStyle.Media("/${path.trimStart('/')}")
}

expect fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient
